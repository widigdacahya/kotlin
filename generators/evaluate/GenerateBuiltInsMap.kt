/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.evaluate

import org.jetbrains.kotlin.backend.common.interpreter.builtins.compileTimeAnnotation
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmManglerDesc
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.utils.Printer
import java.io.File

val DESTINATION = File("compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/interpreter/builtins/IrBuiltInsMapGenerated.kt")

fun main() {
    DESTINATION.writeText(generateMap())
}

fun generateMap(): String {
    val sb = StringBuilder()
    val p = Printer(sb)
    p.println(File("license/COPYRIGHT.txt").readText())
    p.println("package org.jetbrains.kotlin.backend.common.interpreter.builtins")
    p.println()
    p.println("import org.jetbrains.kotlin.backend.common.interpreter.state.*")
    p.println()
    p.println("/** This file is generated by org.jetbrains.kotlin.backend.common.interpreter.builtins.GenerateBuiltInsMap.generateMap(). DO NOT MODIFY MANUALLY */")
    p.println()

    val unaryOperationsMap = getOperationMap(1)
    val binaryOperationsMap = getOperationMap(2)
    val ternaryOperationsMap = getOperationMap(3)

    val binaryIrOperationsMap = getBinaryIrOperationMap()

    //save to file
    p.println("val unaryFunctions = mapOf<CompileTimeFunction, Function1<Any?, Any?>>(")
    p.println(generateUnaryBody(unaryOperationsMap))
    p.println(")")
    p.println()

    p.println("val binaryFunctions = mapOf<CompileTimeFunction, Function2<Any?, Any?, Any?>>(")
    p.println(generateBinaryBody(binaryOperationsMap, binaryIrOperationsMap))
    p.println(")")
    p.println()

    p.println("val ternaryFunctions = mapOf<CompileTimeFunction, Function3<Any?, Any?, Any?, Any?>>(")
    p.println(generateTernaryBody(ternaryOperationsMap))
    p.println(")")
    p.println()

    p.println(
        """
        private fun Any.defaultToString(): String {
            return when (this) {
                is Lambda -> this.toString()
                is State -> "${'$'}{this.irClass.name}@" + System.identityHashCode(this).toString(16).padStart(8, '0')
                else -> this.toString().replaceAfter("@", System.identityHashCode(this).toString(16).padStart(8, '0'))
            }
        }
    """.trimIndent()
    )
    p.println()

    return sb.toString()
}

private fun getOperationMap(argumentsCount: Int): MutableMap<CallableDescriptor, Pair<String, String>> {
    val builtIns = DefaultBuiltIns.Instance
    val operationMap = mutableMapOf<CallableDescriptor, Pair<String, String>>()
    val allPrimitiveTypes = PrimitiveType.values().map { builtIns.getBuiltInClassByFqName(it.typeFqName) }
    val arrays = PrimitiveType.values().map { builtIns.getPrimitiveArrayClassDescriptor(it) } + builtIns.array

    fun CallableDescriptor.isCompileTime(classDescriptor: ClassDescriptor): Boolean {
        val thisIsCompileTime = this.annotations.hasAnnotation(compileTimeAnnotation)
        val classIsCompileTime = classDescriptor.annotations.hasAnnotation(compileTimeAnnotation)
        val isPrimitive = KotlinBuiltIns.isPrimitiveClass(classDescriptor) || KotlinBuiltIns.isString(classDescriptor.defaultType)
        val isFakeOverridden = (this as? FunctionDescriptor)?.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE
        return when {
            isPrimitive -> thisIsCompileTime || classIsCompileTime
            else -> !isFakeOverridden && (thisIsCompileTime || classIsCompileTime)
        }
    }

    for (classDescriptor in allPrimitiveTypes + builtIns.string + arrays + builtIns.any) {
        val classTypeParameters = classDescriptor.typeConstructor.parameters.map { it.name.asString() }
        val typeParametersReplacedToAny =
            if (classTypeParameters.isNotEmpty()) classTypeParameters.joinToString(prefix = "<", postfix = ">") { "Any?" } else ""
        val classType = classDescriptor.defaultType

        val compileTimeFunctions = classDescriptor.getMemberScope(listOf()).getContributedDescriptors()
            .filterIsInstance<CallableDescriptor>()
            .filter { it.isCompileTime(classDescriptor) }

        for (function in compileTimeFunctions) {
            val operationArguments = (listOf(classType) + function.valueParameters.map { it.type }).joinToString { "\"" + it + "\"" }

            val typeParametersOfFun = listOf(classType.constructor.toString() + typeParametersReplacedToAny) +
                    function.valueParameters.map { if (classTypeParameters.contains(it.type.toString())) "Any?" else it.type.toString() }

            if (function.valueParameters.size + 1 == argumentsCount) { // +1 for receiver
                operationMap[function] = typeParametersOfFun.joinToString(prefix = "<", postfix = ">") to operationArguments
            }
        }
    }

    return operationMap
}

private fun getBinaryIrOperationMap(): MutableMap<CallableDescriptor, Pair<String, String>> {
    val irBuiltIns = getIrBuiltIns()
    val operationMap = mutableMapOf<CallableDescriptor, Pair<String, String>>()
    val irFunSymbols =
        (irBuiltIns.lessFunByOperandType.values + irBuiltIns.lessOrEqualFunByOperandType.values +
                irBuiltIns.greaterFunByOperandType.values + irBuiltIns.greaterOrEqualFunByOperandType.values +
                irBuiltIns.eqeqSymbol + irBuiltIns.eqeqeqSymbol + irBuiltIns.ieee754equalsFunByOperandType.values +
                irBuiltIns.andandSymbol + irBuiltIns.ororSymbol)
            .map { it.descriptor }
            .filter { it.annotations.hasAnnotation(compileTimeAnnotation) }

    for (function in irFunSymbols) {
        val parametersTypes = function.valueParameters.map { it.type }
        val operationArguments = parametersTypes.joinToString { "\"" + it + "\"" }
        val functionTypeParameters = parametersTypes.joinToString(prefix = "<", postfix = ">")

        check(parametersTypes.size == 2) { "Couldn't add following method from ir builtins to operations map: ${function.name}" }
        operationMap[function] = functionTypeParameters to operationArguments
    }

    return operationMap
}

private fun generateUnaryBody(unaryOperationsMap: Map<CallableDescriptor, Pair<String, String>>): String {
    val irNullCheck = getIrBuiltIns().checkNotNullSymbol.descriptor
    return unaryOperationsMap.entries.joinToString(separator = ",\n", postfix = ",\n") { (function, parameters) ->
        val methodName = "${function.name}"
        val parentheses = if (function is FunctionDescriptor) "()" else ""
        val body =
            if (methodName == "toString" && parameters.first == "<Any>") "a.defaultToString()"
            else "a.$methodName$parentheses"
        "    unaryOperation${parameters.first}(\"$methodName\", ${parameters.second}) { a -> $body }"
    } +
            "    unaryOperation<Any?>(\"${irNullCheck.name}\", \"${irNullCheck.valueParameters.first().type}\") { a -> a!! },\n" +
            "    unaryOperation<ExceptionState>(\"message\", \"Throwable\") { a -> a.getMessage() },\n" +
            "    unaryOperation<ExceptionState>(\"cause\", \"Throwable\") { a -> a.getCause() }"
}

private fun generateBinaryBody(
    binaryOperationsMap: Map<CallableDescriptor, Pair<String, String>>, binaryIrOperationsMap: Map<CallableDescriptor, Pair<String, String>>
): String {
    return binaryOperationsMap.entries.joinToString(separator = ",\n", postfix = ",\n") { (function, parameters) ->
        val methodName = "${function.name}"
        "    binaryOperation${parameters.first}(\"$methodName\", ${parameters.second}) { a, b -> a.$methodName(b) }"
    } + binaryIrOperationsMap.entries.joinToString(separator = ",\n") { (function, parameters) ->
        val methodName = "${function.name}"
        val methodSymbol = getIrMethodSymbolByName(methodName)
        "    binaryOperation${parameters.first}(\"$methodName\", ${parameters.second}) { a, b -> a $methodSymbol b }"
    }
}

private fun generateTernaryBody(ternaryOperationsMap: Map<CallableDescriptor, Pair<String, String>>): String {
    return ternaryOperationsMap.entries.joinToString(separator = ",\n") { (function, parameters) ->
        val methodName = "${function.name}"
        "    ternaryOperation${parameters.first}(\"$methodName\", ${parameters.second}) { a, b, c -> a.$methodName(b, c) }"
    }
}

private fun getIrMethodSymbolByName(methodName: String): String {
    return when (methodName) {
        IrBuiltIns.OperatorNames.LESS -> "<"
        IrBuiltIns.OperatorNames.LESS_OR_EQUAL -> "<="
        IrBuiltIns.OperatorNames.GREATER -> ">"
        IrBuiltIns.OperatorNames.GREATER_OR_EQUAL -> ">="
        IrBuiltIns.OperatorNames.EQEQ -> "=="
        IrBuiltIns.OperatorNames.EQEQEQ -> "==="
        IrBuiltIns.OperatorNames.IEEE754_EQUALS -> "=="
        IrBuiltIns.OperatorNames.ANDAND -> "&&"
        IrBuiltIns.OperatorNames.OROR -> "||"
        else -> throw UnsupportedOperationException("Unknown ir operation \"$methodName\"")
    }
}

private fun getIrBuiltIns(): IrBuiltIns {
    val builtIns = DefaultBuiltIns.Instance
    val languageSettings = LanguageVersionSettingsImpl(LanguageVersion.KOTLIN_1_3, ApiVersion.KOTLIN_1_3)

    val moduleDescriptor = ModuleDescriptorImpl(Name.special("<test-module>"), LockBasedStorageManager(""), builtIns)
    val mangler = JvmManglerDesc()
    val signaturer = JvmIdSignatureDescriptor(mangler)
    val symbolTable = SymbolTable(signaturer)
    val constantValueGenerator = ConstantValueGenerator(moduleDescriptor, symbolTable)
    val typeTranslator = TypeTranslator(symbolTable, languageSettings, builtIns)
    constantValueGenerator.typeTranslator = typeTranslator
    typeTranslator.constantValueGenerator = constantValueGenerator

    return IrBuiltIns(builtIns, typeTranslator, signaturer)
}