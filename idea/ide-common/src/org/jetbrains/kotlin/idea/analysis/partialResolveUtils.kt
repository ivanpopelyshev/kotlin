/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.analysis

import org.jetbrains.kotlin.cfg.ControlFlowInformationProvider
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.SimpleGlobalContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.context.withProject
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.frontend.di.createContainerForBodyResolve
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.*
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyScriptDescriptor
import org.jetbrains.kotlin.resolve.scopes.LexicalScope

fun findElementOfAdditionalResolve(element: KtElement): KtElement? {
    val elementOfAdditionalResolve = KtPsiUtil.getTopmostParentOfTypes(
            element,
            KtNamedFunction::class.java,
            KtAnonymousInitializer::class.java,
            KtPrimaryConstructor::class.java,
            KtSecondaryConstructor::class.java,
            KtProperty::class.java,
            KtSuperTypeList::class.java,
            KtInitializerList::class.java,
            KtImportList::class.java,
            KtAnnotationEntry::class.java,
            KtTypeParameter::class.java,
            KtTypeConstraint::class.java,
            KtPackageDirective::class.java,
            KtCodeFragment::class.java) as KtElement?

    when (elementOfAdditionalResolve) {
        null -> {
            // Case of JetAnnotationEntry on top level class
            if (element is KtAnnotationEntry) {
                return element
            }

            if (element is KtFileAnnotationList) {
                return element
            }

            return null
        }

        is KtPackageDirective -> return element

        is KtDeclaration -> {
            if (element is KtParameter && !KtPsiUtil.isLocal(element)) {
                return null
            }

            return elementOfAdditionalResolve
        }

        else -> return elementOfAdditionalResolve
    }
}

fun performElementAdditionalResolve(
        resolveElement: KtElement,
        resolveSession: ResolveSession,
        codeFragmentAnalyzer: CodeFragmentAnalyzer,
        contextElements: Collection<KtElement>?,
        bodyResolveMode: BodyResolveMode,
        probablyNothingCallableNames: ProbablyNothingCallableNames,
        targetPlatform: TargetPlatform
): Pair<BindingContext, StatementFilter> {
    if (contextElements == null) {
        assert(bodyResolveMode == BodyResolveMode.FULL)
    }

    val file = resolveElement.getContainingKtFile()

    var statementFilterUsed = StatementFilter.NONE

    fun createStatementFilter(): StatementFilter {
        assert(resolveElement is KtDeclaration)
        if (bodyResolveMode != BodyResolveMode.FULL) {
            statementFilterUsed = PartialBodyResolveFilter(
                    contextElements!!,
                    resolveElement as KtDeclaration,
                    probablyNothingCallableNames,
                    bodyResolveMode == BodyResolveMode.PARTIAL_FOR_COMPLETION)
        }
        return statementFilterUsed
    }

    val trace: BindingTrace = when (resolveElement) {
        is KtNamedFunction -> functionAdditionalResolve(resolveSession, resolveElement, file, createStatementFilter(), targetPlatform)

        is KtAnonymousInitializer -> initializerAdditionalResolve(resolveSession, resolveElement, file, createStatementFilter(), targetPlatform)

        is KtPrimaryConstructor -> constructorAdditionalResolve(resolveSession, resolveElement.parent as KtClass, file, targetPlatform)

        is KtSecondaryConstructor -> secondaryConstructorAdditionalResolve(resolveSession, resolveElement, file, createStatementFilter(), targetPlatform)

        is KtProperty -> propertyAdditionalResolve(resolveSession, resolveElement, file, createStatementFilter(), targetPlatform)

        is KtSuperTypeList ->
            delegationSpecifierAdditionalResolve(resolveSession, resolveElement, resolveElement.getParent() as KtClassOrObject, file, targetPlatform)

        is KtInitializerList ->
            delegationSpecifierAdditionalResolve(resolveSession, resolveElement, resolveElement.getParent() as KtEnumEntry, file, targetPlatform)

        is KtImportList -> {
            val resolver = resolveSession.fileScopeProvider.getImportResolver(resolveElement.getContainingKtFile())
            resolver.forceResolveAllImports()
            resolveSession.trace
        }

        is KtFileAnnotationList -> {
            val annotationEntry = resolveElement.annotationEntries.firstOrNull()
            if (annotationEntry != null) {
                annotationAdditionalResolve(resolveSession, annotationEntry)
            }
            else {
                resolveSession.trace
            }
        }

        is KtAnnotationEntry -> annotationAdditionalResolve(resolveSession, resolveElement)

        is KtTypeParameter -> typeParameterAdditionalResolve(resolveSession, resolveElement)

        is KtTypeConstraint -> typeConstraintAdditionalResolve(resolveSession, resolveElement)

        is KtCodeFragment -> codeFragmentAdditionalResolve(resolveSession, codeFragmentAnalyzer, resolveElement, bodyResolveMode)

        else -> {
            if (resolveElement.getParentOfType<KtPackageDirective>(true) != null) {
                packageRefAdditionalResolve(resolveSession, resolveElement)
            }
            else {
                error("Invalid type of the topmost parent: $resolveElement\n${resolveElement.getElementTextWithContext()}")
            }
        }
    }

    val controlFlowTrace = DelegatingBindingTrace(trace.bindingContext, "Element control flow resolve", resolveElement)
    ControlFlowInformationProvider(resolveElement, controlFlowTrace).checkDeclaration()
    controlFlowTrace.addOwnDataTo(trace, null, false)

    return Pair(trace.bindingContext, statementFilterUsed)
}

private fun functionAdditionalResolve(resolveSession: ResolveSession, namedFunction: KtNamedFunction, file: KtFile,
                                      statementFilter: StatementFilter, targetPlatform: TargetPlatform): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, namedFunction)

    val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(namedFunction)
    val functionDescriptor = resolveSession.resolveToDescriptor(namedFunction) as FunctionDescriptor
    ForceResolveUtil.forceResolveAllContents(functionDescriptor)

    val bodyResolver = createBodyResolver(resolveSession, trace, file, statementFilter, targetPlatform)
    bodyResolver.resolveFunctionBody(DataFlowInfo.EMPTY, trace, namedFunction, functionDescriptor, scope)

    forceResolveAnnotationsInside(resolveSession, namedFunction)

    return trace
}

private fun initializerAdditionalResolve(resolveSession: ResolveSession, anonymousInitializer: KtAnonymousInitializer,
                                         file: KtFile, statementFilter: StatementFilter, targetPlatform: TargetPlatform): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, anonymousInitializer)

    val classOrObjectDescriptor = resolveSession.resolveToDescriptor(anonymousInitializer.containingDeclaration) as LazyClassDescriptor

    val bodyResolver = createBodyResolver(resolveSession, trace, file, statementFilter, targetPlatform)
    bodyResolver.resolveAnonymousInitializer(DataFlowInfo.EMPTY, anonymousInitializer, classOrObjectDescriptor)

    forceResolveAnnotationsInside(resolveSession, anonymousInitializer)

    return trace
}

private fun constructorAdditionalResolve(resolveSession: ResolveSession, klass: KtClass, file: KtFile, targetPlatform: TargetPlatform): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, klass)
    val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(klass)

    val classDescriptor = resolveSession.resolveToDescriptor(klass) as ClassDescriptor
    val constructorDescriptor = classDescriptor.unsubstitutedPrimaryConstructor
                                ?: error("Can't get primary constructor for descriptor '$classDescriptor' " +
                                         "in from class '${klass.getElementTextWithContext()}'")

    val primaryConstructor = klass.getPrimaryConstructor()
    if (primaryConstructor != null) {
        val bodyResolver = createBodyResolver(resolveSession, trace, file, StatementFilter.NONE, targetPlatform)
        bodyResolver.resolveConstructorParameterDefaultValues(DataFlowInfo.EMPTY, trace, primaryConstructor, constructorDescriptor, scope)

        forceResolveAnnotationsInside(resolveSession, primaryConstructor)
    }

    return trace
}

private fun secondaryConstructorAdditionalResolve(resolveSession: ResolveSession, constructor: KtSecondaryConstructor,
                                                  file: KtFile, statementFilter: StatementFilter, targetPlatform: TargetPlatform): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, constructor)

    val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(constructor)
    val constructorDescriptor = resolveSession.resolveToDescriptor(constructor) as ConstructorDescriptor
    ForceResolveUtil.forceResolveAllContents(constructorDescriptor)

    val bodyResolver = createBodyResolver(resolveSession, trace, file, statementFilter, targetPlatform)
    bodyResolver.resolveSecondaryConstructorBody(DataFlowInfo.EMPTY, trace, constructor, constructorDescriptor, scope)

    forceResolveAnnotationsInside(resolveSession, constructor)

    return trace
}

private fun propertyAdditionalResolve(resolveSession: ResolveSession, property: KtProperty,
                                      file: KtFile, statementFilter: StatementFilter, targetPlatform: TargetPlatform): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, property)

    val bodyResolver = createBodyResolver(resolveSession, trace, file, statementFilter, targetPlatform)
    val descriptor = resolveSession.resolveToDescriptor(property) as PropertyDescriptor
    ForceResolveUtil.forceResolveAllContents(descriptor)

    val bodyResolveContext = BodyResolveContextForLazy(TopDownAnalysisMode.LocalDeclarations, { declaration ->
        assert(declaration.parent == property || declaration == property) {
            "Must be called only for property accessors or for property, but called for $declaration"
        }
        resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(declaration)
    })

    bodyResolver.resolveProperty(bodyResolveContext, property, descriptor)

    forceResolveAnnotationsInside(resolveSession, property)

    for (accessor in property.accessors) {
        ControlFlowInformationProvider(accessor, trace).checkDeclaration()
    }

    return trace
}

private fun delegationSpecifierAdditionalResolve(resolveSession: ResolveSession, ktElement: KtElement,
                                                 classOrObject: KtClassOrObject, file: KtFile, targetPlatform: TargetPlatform): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, ktElement)
    val descriptor = resolveSession.resolveToDescriptor(classOrObject) as LazyClassDescriptor

    // Activate resolving of supertypes
    ForceResolveUtil.forceResolveAllContents(descriptor.typeConstructor.supertypes)

    val bodyResolver = createBodyResolver(resolveSession, trace, file, StatementFilter.NONE, targetPlatform)
    bodyResolver.resolveSuperTypeEntryList(DataFlowInfo.EMPTY,
                                           classOrObject,
                                           descriptor,
                                           descriptor.unsubstitutedPrimaryConstructor,
                                           descriptor.scopeForConstructorHeaderResolution,
                                           descriptor.scopeForMemberDeclarationResolution)

    return trace
}

private fun annotationAdditionalResolve(resolveSession: ResolveSession, ktAnnotationEntry: KtAnnotationEntry): BindingTrace {
    val modifierList = ktAnnotationEntry.getParentOfType<KtModifierList>(true)
    val declaration = modifierList?.getParentOfType<KtDeclaration>(true)
    if (declaration != null) {
        doResolveAnnotations(getAnnotationsByDeclaration(resolveSession, modifierList, declaration))
    }
    else {
        val fileAnnotationList = ktAnnotationEntry.getParentOfType<KtFileAnnotationList>(true)
        if (fileAnnotationList != null) {
            doResolveAnnotations(resolveSession.getFileAnnotations(fileAnnotationList.getContainingKtFile()))
        }
        if (modifierList != null && modifierList.parent is KtFile) {
            doResolveAnnotations(resolveSession.getDanglingAnnotations(modifierList.getContainingKtFile()))
        }
    }

    return resolveSession.trace
}

private fun typeParameterAdditionalResolve(resolveSession: ResolveSession, typeParameter: KtTypeParameter): BindingTrace {
    val descriptor = resolveSession.resolveToDescriptor(typeParameter)
    ForceResolveUtil.forceResolveAllContents(descriptor)

    return resolveSession.trace
}

private fun typeConstraintAdditionalResolve(resolveSession: ResolveSession, jetTypeConstraint: KtTypeConstraint): BindingTrace {
    val declaration = jetTypeConstraint.getParentOfType<KtDeclaration>(true)!!
    val descriptor = resolveSession.resolveToDescriptor(declaration) as ClassDescriptor

    for (parameterDescriptor in descriptor.declaredTypeParameters) {
        ForceResolveUtil.forceResolveAllContents<TypeParameterDescriptor>(parameterDescriptor)
    }

    return resolveSession.trace
}

private fun codeFragmentAdditionalResolve(resolveSession: ResolveSession,
                                          codeFragmentAnalyzer: CodeFragmentAnalyzer,
                                          codeFragment: KtCodeFragment,
                                          bodyResolveMode: BodyResolveMode): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, codeFragment)

    val contextResolveMode = if (bodyResolveMode == BodyResolveMode.PARTIAL)
        BodyResolveMode.PARTIAL_FOR_COMPLETION
    else
        bodyResolveMode
    codeFragmentAnalyzer.analyzeCodeFragment(codeFragment, trace, contextResolveMode)

    return trace
}

private fun packageRefAdditionalResolve(resolveSession: ResolveSession, ktElement: KtElement): BindingTrace {
    val trace = createDelegatingTrace(resolveSession, ktElement)

    if (ktElement is KtSimpleNameExpression) {
        val header = ktElement.getParentOfType<KtPackageDirective>(true)!!

        if (Name.isValidIdentifier(ktElement.getReferencedName())) {
            if (trace.bindingContext[BindingContext.REFERENCE_TARGET, ktElement] == null) {
                val fqName = header.getFqName(ktElement)
                val packageDescriptor = resolveSession.moduleDescriptor.getPackage(fqName)
                trace.record(BindingContext.REFERENCE_TARGET, ktElement, packageDescriptor)
            }
        }
    }

    return trace
}

// All additional resolve should be done to separate trace
private fun createDelegatingTrace(resolveSession: ResolveSession, resolveElement: KtElement): BindingTrace {
    return resolveSession.storageManager.createSafeTrace(
            DelegatingBindingTrace(resolveSession.bindingContext, "trace to resolve element", resolveElement))
}

private fun createBodyResolver(
        resolveSession: ResolveSession,
        trace: BindingTrace,
        file: KtFile,
        statementFilter: StatementFilter,
        targetPlatform: TargetPlatform
): BodyResolver {
    val globalContext = SimpleGlobalContext(resolveSession.storageManager, resolveSession.exceptionTracker)
    val module = resolveSession.moduleDescriptor
    return createContainerForBodyResolve(
            globalContext.withProject(file.project).withModule(module),
            trace,
            targetPlatform,
            statementFilter,
            LanguageVersionSettingsImpl.DEFAULT // TODO: see KT-12410
    ).get<BodyResolver>()
}

private fun forceResolveAnnotationsInside(resolveSession: ResolveSession, element: KtElement) {
    element.forEachDescendantOfType<KtAnnotationEntry>(canGoInside = { it !is KtBlockExpression }) { entry ->
        resolveSession.bindingContext[BindingContext.ANNOTATION, entry]?.let {
            ForceResolveUtil.forceResolveAllContents(it)
        }
    }
}

private fun doResolveAnnotations(annotations: Annotations) {
    ForceResolveUtil.forceResolveAllContents(annotations)
}

private fun getAnnotationsByDeclaration(resolveSession: ResolveSession, modifierList: KtModifierList, declaration: KtDeclaration): Annotations {
    var descriptor = resolveSession.resolveToDescriptor(declaration)
    if (declaration is KtClass) {
        if (modifierList == declaration.getPrimaryConstructorModifierList()) {
            descriptor = (descriptor as ClassDescriptor).unsubstitutedPrimaryConstructor
                         ?: error("No constructor found: ${declaration.getText()}")
        }
    }

    if (declaration is KtClassOrObject && modifierList.parent == declaration.getBody() && descriptor is LazyClassDescriptor) {
        return descriptor.danglingAnnotations
    }

    return descriptor.annotations
}

private class BodyResolveContextForLazy(
        private val topDownAnalysisMode: TopDownAnalysisMode,
        private val declaringScopes: Function1<KtDeclaration, LexicalScope?>
) : BodiesResolveContext {
    override fun getFiles(): Collection<KtFile> = setOf()

    override fun getDeclaredClasses(): MutableMap<KtClassOrObject, ClassDescriptorWithResolutionScopes> = hashMapOf()

    override fun getAnonymousInitializers(): MutableMap<KtAnonymousInitializer, ClassDescriptorWithResolutionScopes> = hashMapOf()

    override fun getSecondaryConstructors(): MutableMap<KtSecondaryConstructor, ConstructorDescriptor> = hashMapOf()

    override fun getProperties(): MutableMap<KtProperty, PropertyDescriptor> = hashMapOf()

    override fun getFunctions(): MutableMap<KtNamedFunction, SimpleFunctionDescriptor> = hashMapOf()

    override fun getDeclaringScope(declaration: KtDeclaration): LexicalScope? = declaringScopes(declaration)

    override fun getScripts(): MutableMap<KtScript, LazyScriptDescriptor> = hashMapOf()

    override fun getOuterDataFlowInfo(): DataFlowInfo = DataFlowInfo.EMPTY

    override fun getTopDownAnalysisMode() = topDownAnalysisMode
}