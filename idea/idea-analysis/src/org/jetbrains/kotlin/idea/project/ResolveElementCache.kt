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

package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.SimpleGlobalContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.context.withProject
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.frontend.di.createContainerForBodyResolve
import org.jetbrains.kotlin.idea.analysis.CodeFragmentAnalyzer
import org.jetbrains.kotlin.idea.analysis.ElementResolver
import org.jetbrains.kotlin.idea.analysis.findElementOfAdditionalResolve
import org.jetbrains.kotlin.idea.analysis.performElementAdditionalResolve
import org.jetbrains.kotlin.idea.stubindex.KotlinProbablyNothingFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinProbablyNothingPropertyShortNameIndex
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.*
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

class ResolveElementCache(
        private val resolveSession: ResolveSession,
        private val project: Project,
        private val targetPlatform: TargetPlatform,
        private val codeFragmentAnalyzer: CodeFragmentAnalyzer
) : BodyResolveCache, ElementResolver {
    private class CachedFullResolve(val bindingContext: BindingContext, resolveElement: KtElement) {
        private val modificationStamp: Long? = modificationStamp(resolveElement)

        fun isUpToDate(resolveElement: KtElement) = modificationStamp == modificationStamp(resolveElement)

        private fun modificationStamp(resolveElement: KtElement): Long? {
            val file = resolveElement.containingFile
            return when {
                // for non-physical file we don't get OUT_OF_CODE_BLOCK_MODIFICATION_COUNT increased and must reset
                // data on any modification of the file
                !file.isPhysical -> file.modificationStamp

                resolveElement is KtDeclaration && KotlinCodeBlockModificationListener.isBlockDeclaration(resolveElement) -> resolveElement.getModificationStamp()
                resolveElement is KtSuperTypeList -> resolveElement.modificationStamp
                else -> null
            }
        }
    }

    // drop whole cache after change "out of code block"
    private val fullResolveCache: CachedValue<MutableMap<KtElement, CachedFullResolve>> = CachedValuesManager.getManager(project).createCachedValue(
            CachedValueProvider<MutableMap<KtElement, ResolveElementCache.CachedFullResolve>> {
                CachedValueProvider.Result.create(ContainerUtil.createConcurrentSoftValueMap<KtElement, CachedFullResolve>(),
                                                  PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT,
                                                  resolveSession.exceptionTracker)
            },
            false)

    private class CachedPartialResolve(val bindingContext: BindingContext, file: KtFile) {
        private val modificationStamp: Long? = modificationStamp(file)

        fun isUpToDate(file: KtFile) = modificationStamp == modificationStamp(file)

        private fun modificationStamp(file: KtFile): Long? {
            return if (!file.isPhysical) // for non-physical file we don't get MODIFICATION_COUNT increased and must reset data on any modification of the file
                file.modificationStamp
            else
                null
        }
    }

    private val partialBodyResolveCache: CachedValue<MutableMap<KtExpression, CachedPartialResolve>> =
            CachedValuesManager.getManager(project).createCachedValue(
                    CachedValueProvider<MutableMap<KtExpression, ResolveElementCache.CachedPartialResolve>> {
                        CachedValueProvider.Result.create(ContainerUtil.createConcurrentSoftValueMap<KtExpression, CachedPartialResolve>(),
                                                          PsiModificationTracker.MODIFICATION_COUNT,
                                                          resolveSession.exceptionTracker)
                    },
                    false)


    private fun probablyNothingCallableNames(): ProbablyNothingCallableNames {
        return object : ProbablyNothingCallableNames {
            override fun functionNames() = KotlinProbablyNothingFunctionShortNameIndex.getInstance().getAllKeys(project)
            override fun propertyNames() = KotlinProbablyNothingPropertyShortNameIndex.getInstance().getAllKeys(project)
        }
    }

    override fun resolveFunctionBody(function: KtNamedFunction)
            = getElementsAdditionalResolve(function, null, BodyResolveMode.FULL)

    fun resolvePrimaryConstructorParametersDefaultValues(ktClass: KtClass): BindingContext {
        return constructorAdditionalResolve(resolveSession, ktClass, ktClass.getContainingKtFile()).bindingContext
    }

    @Deprecated("Use getElementsAdditionalResolve")
    fun getElementAdditionalResolve(resolveElement: KtElement, contextElement: KtElement, bodyResolveMode: BodyResolveMode): BindingContext {
        return getElementsAdditionalResolve(resolveElement, listOf(contextElement), bodyResolveMode)
    }

    fun getElementsAdditionalResolve(resolveElement: KtElement, contextElements: Collection<KtElement>?, bodyResolveMode: BodyResolveMode): BindingContext {
        if (contextElements == null) {
            assert(bodyResolveMode == BodyResolveMode.FULL)
        }

        // check if full additional resolve already performed and is up-to-date
        val fullResolveMap = fullResolveCache.value
        val cachedFullResolve = fullResolveMap[resolveElement]
        if (cachedFullResolve != null) {
            if (cachedFullResolve.isUpToDate(resolveElement)) {
                return cachedFullResolve.bindingContext
            }
            else {
                fullResolveMap.remove(resolveElement) // remove outdated cache entry
            }
        }

        when (bodyResolveMode) {
            BodyResolveMode.FULL -> {
                val bindingContext = performElementAdditionalResolve(
                        resolveElement,
                        resolveSession,
                        codeFragmentAnalyzer,
                        null,
                        BodyResolveMode.FULL,
                        probablyNothingCallableNames(),
                        targetPlatform).first
                fullResolveMap[resolveElement] = CachedFullResolve(bindingContext, resolveElement)
                return bindingContext
            }

            BodyResolveMode.PARTIAL -> {
                if (resolveElement !is KtDeclaration) {
                    return getElementsAdditionalResolve(resolveElement, null, BodyResolveMode.FULL)
                }

                val file = resolveElement.getContainingKtFile()
                val statementsToResolve = contextElements!!.map { PartialBodyResolveFilter.findStatementToResolve(it, resolveElement) }.distinct()
                val partialResolveMap = partialBodyResolveCache.value
                val cachedResults = statementsToResolve.map { partialResolveMap[it ?: resolveElement] }
                if (cachedResults.all { it != null && it.isUpToDate(file) }) { // partial resolve is already cached for these statements
                    return CompositeBindingContext.create(cachedResults.map { it!!.bindingContext }.distinct())
                }

                val (bindingContext, statementFilter) = performElementAdditionalResolve(
                        resolveElement,
                        resolveSession,
                        codeFragmentAnalyzer,
                        contextElements,
                        BodyResolveMode.PARTIAL,
                        probablyNothingCallableNames(),
                        targetPlatform)

                if (statementFilter == StatementFilter.NONE) { // partial resolve is not supported for the given declaration - full resolve performed instead
                    fullResolveMap[resolveElement] = CachedFullResolve(bindingContext, resolveElement)
                    return bindingContext
                }

                val resolveToCache = CachedPartialResolve(bindingContext, file)

                for (statement in (statementFilter as PartialBodyResolveFilter).allStatementsToResolve) {
                    if (!partialResolveMap.containsKey(statement) && bindingContext[BindingContext.PROCESSED, statement] == true) {
                        partialResolveMap[statement] = resolveToCache
                    }
                }

                // we use the whole declaration key in the map to obtain resolve not inside any block (e.g. default parameter values)
                partialResolveMap[resolveElement] = resolveToCache

                return bindingContext
            }

            BodyResolveMode.PARTIAL_FOR_COMPLETION -> {
                if (resolveElement !is KtDeclaration) {
                    return getElementsAdditionalResolve(resolveElement, null, BodyResolveMode.FULL)
                }

                // not cached
                return performElementAdditionalResolve(
                        resolveElement,
                        resolveSession,
                        codeFragmentAnalyzer,
                        contextElements,
                        bodyResolveMode,
                        probablyNothingCallableNames(),
                        targetPlatform).first
            }
        }
    }

    override fun resolveToElements(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext {
        val elementsByAdditionalResolveElement: Map<KtElement?, List<KtElement>> = elements.groupBy(::findElementOfAdditionalResolve)

        val bindingContexts = ArrayList<BindingContext>()
        val declarationsToResolve = ArrayList<KtDeclaration>()
        var addResolveSessionBindingContext = false
        for ((elementOfAdditionalResolve, contextElements) in elementsByAdditionalResolveElement) {
            if (elementOfAdditionalResolve != null) {
                if (elementOfAdditionalResolve !is KtParameter) {
                    val bindingContext = getElementsAdditionalResolve(elementOfAdditionalResolve, contextElements, bodyResolveMode)
                    bindingContexts.add(bindingContext)
                }
                else {
                    // Parameters for function literal could be met inside other parameters. We can't make resolveToDescriptors for internal elements.
                    declarationsToResolve.addIfNotNull(elementOfAdditionalResolve.getNonStrictParentOfType<KtDeclaration>())
                    addResolveSessionBindingContext = true
                }
            }
            else {
                contextElements
                        .map { it.getNonStrictParentOfType<KtDeclaration>() }
                        .filterNotNull()
                        .filterTo(declarationsToResolve) {
                            it !is KtAnonymousInitializer && it !is KtDestructuringDeclaration && it !is KtDestructuringDeclarationEntry
                        }
                addResolveSessionBindingContext = true
            }
        }

        declarationsToResolve.forEach { resolveSession.resolveToDescriptor(it) }
        if (addResolveSessionBindingContext) {
            bindingContexts.add(resolveSession.bindingContext)
        }

        //TODO: it can be slow if too many contexts
        return CompositeBindingContext.create(bindingContexts)
    }

    private fun constructorAdditionalResolve(resolveSession: ResolveSession, klass: KtClass, file: KtFile): BindingTrace {
        val trace = createDelegatingTrace(klass)
        val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(klass)

        val classDescriptor = resolveSession.resolveToDescriptor(klass) as ClassDescriptor
        val constructorDescriptor = classDescriptor.unsubstitutedPrimaryConstructor
                                    ?: error("Can't get primary constructor for descriptor '$classDescriptor' " +
                                             "in from class '${klass.getElementTextWithContext()}'")

        val primaryConstructor = klass.getPrimaryConstructor()
        if (primaryConstructor != null) {
            val bodyResolver = createBodyResolver(resolveSession, trace, file, StatementFilter.NONE)
            bodyResolver.resolveConstructorParameterDefaultValues(DataFlowInfo.EMPTY, trace, primaryConstructor, constructorDescriptor, scope)

            forceResolveAnnotationsInside(primaryConstructor)
        }

        return trace
    }

    private fun forceResolveAnnotationsInside(element: KtElement) {
        element.forEachDescendantOfType<KtAnnotationEntry>(canGoInside = { it !is KtBlockExpression }) { entry ->
            resolveSession.bindingContext[BindingContext.ANNOTATION, entry]?.let {
                ForceResolveUtil.forceResolveAllContents(it)
            }
        }
    }

    private fun createBodyResolver(
            resolveSession: ResolveSession,
            trace: BindingTrace,
            file: KtFile,
            statementFilter: StatementFilter
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

    // All additional resolve should be done to separate trace
    private fun createDelegatingTrace(resolveElement: KtElement): BindingTrace {
        return resolveSession.storageManager.createSafeTrace(
                DelegatingBindingTrace(resolveSession.bindingContext, "trace to resolve element", resolveElement))
    }

}

