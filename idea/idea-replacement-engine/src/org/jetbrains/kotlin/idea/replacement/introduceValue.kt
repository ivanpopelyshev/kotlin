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

package org.jetbrains.kotlin.idea.replacement

import org.jetbrains.kotlin.idea.analysis.computeTypeInContext
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.utils.findLocalVariable
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType

internal fun MutableReplacementCode.introduceValue(
        value: KtExpression,
        valueType: KotlinType?,
        usages: Collection<KtExpression>,
        expressionToBeReplaced: KtExpression,
        nameSuggestion: String? = null,
        safeCall: Boolean = false
) {
    assert(usages.all { this.containsStrictlyInside(it) })

    val psiFactory = KtPsiFactory(value)

    val bindingContext = expressionToBeReplaced.analyze(BodyResolveMode.FULL)

    fun replaceUsages(name: Name) {
        val nameInCode = psiFactory.createExpression(name.render())
        for (usage in usages) {
            // there can be parenthesis around the expression which will become unnecessary
            val usageToReplace = (usage.parent as? KtParenthesizedExpression) ?: usage
            usageToReplace.replace(nameInCode)
        }
    }

    fun suggestName(validator: (String) -> Boolean): Name {
        val name = if (nameSuggestion != null)
            KotlinNameSuggester.suggestNameByName(nameSuggestion, validator)
        else
            KotlinNameSuggester.suggestNamesByExpressionOnly(value, bindingContext, validator, "t").first()
        return Name.identifier(name)
    }

    // checks that name is used (without receiver) inside code being constructed but not inside usages that will be replaced
    fun isNameUsed(name: String) = collectNameUsages(this, name).any { nameUsage -> usages.none { it.isAncestor(nameUsage) } }

    if (!safeCall) {
        val resolutionScope = expressionToBeReplaced.getResolutionScope(bindingContext, expressionToBeReplaced.getResolutionFacade())

        if (usages.isNotEmpty()) {
            var explicitType: KotlinType? = null
            if (valueType != null && !ErrorUtils.containsErrorType(valueType)) {
                val valueTypeWithoutExpectedType = value.computeTypeInContext(
                        resolutionScope,
                        expressionToBeReplaced,
                        dataFlowInfo = bindingContext.getDataFlowInfo(expressionToBeReplaced)
                )
                if (valueTypeWithoutExpectedType == null || ErrorUtils.containsErrorType(valueTypeWithoutExpectedType)) {
                    explicitType = valueType
                }
            }

            val name = suggestName { name ->
                resolutionScope.findLocalVariable(Name.identifier(name)) == null && !isNameUsed(name)
            }

            val declaration = psiFactory.createDeclarationByPattern<KtVariableDeclaration>("val $0 = $1", name, value)
            statementsBefore.add(0, declaration)
            if (explicitType != null) {
                addPostInsertionAction(declaration) { it.setType(explicitType!!); it }
            }

            replaceUsages(name)
        }
        else {
            statementsBefore.add(0, value)
        }
    }
    else {
        //TODO: handle mainExpression == null and statementsBefore!

        mainExpression = if (!isNameUsed("it")) {
            replaceUsages(Name.identifier("it"))
            psiFactory.createExpressionByPattern("$0?.let { $1 }", value, mainExpression!!)
        }
        else {
            val name = suggestName { !isNameUsed(it) }
            replaceUsages(name)
            psiFactory.createExpressionByPattern("$0?.let { $1 -> $2 }", value, name, mainExpression!!)
        }
    }
}

private fun collectNameUsages(scope: MutableReplacementCode, name: String): List<KtSimpleNameExpression> {
    return scope.expressions.flatMap { expression ->
        expression.collectDescendantsOfType<KtSimpleNameExpression> { it.getReceiverExpression() == null && it.getReferencedName() == name }
    }
}

