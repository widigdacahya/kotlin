/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.di;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetImportsFactory;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.jet.lang.resolve.calls.ArgumentTypeResolver;
import org.jetbrains.jet.lang.resolve.calls.CallExpressionResolver;
import org.jetbrains.jet.lang.resolve.calls.CallResolver;
import org.jetbrains.jet.lang.resolve.calls.CandidateResolver;
import org.jetbrains.jet.lang.resolve.lazy.LazyCodeAnalyzer;
import org.jetbrains.jet.lang.resolve.lazy.ScopeProvider;
import org.jetbrains.jet.lang.types.expressions.ExpressionTypingServices;

import javax.annotation.PreDestroy;

/* This file is generated by org.jetbrains.jet.generators.injectors.GenerateInjectors. DO NOT EDIT! */
public class InjectorForLazyResolve {
    
    private final Project project;
    private final LazyCodeAnalyzer analyzer;
    private final BindingTrace bindingTrace;
    private DescriptorResolver descriptorResolver;
    private ExpressionTypingServices expressionTypingServices;
    private TypeResolver typeResolver;
    private ScopeProvider scopeProvider;
    private AnnotationResolver annotationResolver;
    private QualifiedExpressionResolver qualifiedExpressionResolver;
    private JetImportsFactory jetImportsFactory;
    private CallExpressionResolver callExpressionResolver;
    private CallResolver callResolver;
    private ArgumentTypeResolver argumentTypeResolver;
    private CandidateResolver candidateResolver;
    
    public InjectorForLazyResolve(
        @NotNull Project project,
        @NotNull LazyCodeAnalyzer analyzer,
        @NotNull BindingTrace bindingTrace
    ) {
        this.project = project;
        this.analyzer = analyzer;
        this.bindingTrace = bindingTrace;
        this.descriptorResolver = new DescriptorResolver();
        this.expressionTypingServices = new ExpressionTypingServices();
        this.typeResolver = new TypeResolver();
        this.scopeProvider = new ScopeProvider(analyzer);
        this.annotationResolver = new AnnotationResolver();
        this.qualifiedExpressionResolver = new QualifiedExpressionResolver();
        this.jetImportsFactory = new JetImportsFactory();
        this.callExpressionResolver = new CallExpressionResolver();
        this.callResolver = new CallResolver();
        this.argumentTypeResolver = new ArgumentTypeResolver();
        this.candidateResolver = new CandidateResolver();

        this.descriptorResolver.setAnnotationResolver(annotationResolver);
        this.descriptorResolver.setExpressionTypingServices(expressionTypingServices);
        this.descriptorResolver.setTypeResolver(typeResolver);

        this.expressionTypingServices.setCallExpressionResolver(callExpressionResolver);
        this.expressionTypingServices.setCallResolver(callResolver);
        this.expressionTypingServices.setDescriptorResolver(descriptorResolver);
        this.expressionTypingServices.setProject(project);
        this.expressionTypingServices.setTypeResolver(typeResolver);

        this.typeResolver.setAnnotationResolver(annotationResolver);
        this.typeResolver.setDescriptorResolver(descriptorResolver);
        this.typeResolver.setQualifiedExpressionResolver(qualifiedExpressionResolver);

        this.annotationResolver.setCallResolver(callResolver);
        this.annotationResolver.setExpressionTypingServices(expressionTypingServices);

        this.jetImportsFactory.setProject(project);

        callExpressionResolver.setExpressionTypingServices(expressionTypingServices);

        callResolver.setArgumentTypeResolver(argumentTypeResolver);
        callResolver.setCandidateResolver(candidateResolver);
        callResolver.setExpressionTypingServices(expressionTypingServices);
        callResolver.setTypeResolver(typeResolver);

        argumentTypeResolver.setExpressionTypingServices(expressionTypingServices);
        argumentTypeResolver.setTypeResolver(typeResolver);

        candidateResolver.setArgumentTypeResolver(argumentTypeResolver);

    }
    
    @PreDestroy
    public void destroy() {
    }
    
    public DescriptorResolver getDescriptorResolver() {
        return this.descriptorResolver;
    }
    
    public ExpressionTypingServices getExpressionTypingServices() {
        return this.expressionTypingServices;
    }
    
    public TypeResolver getTypeResolver() {
        return this.typeResolver;
    }
    
    public ScopeProvider getScopeProvider() {
        return this.scopeProvider;
    }
    
    public AnnotationResolver getAnnotationResolver() {
        return this.annotationResolver;
    }
    
    public QualifiedExpressionResolver getQualifiedExpressionResolver() {
        return this.qualifiedExpressionResolver;
    }
    
    public JetImportsFactory getJetImportsFactory() {
        return this.jetImportsFactory;
    }
    
}
