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

package org.jetbrains.kotlin.allopen

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.extensions.AnnotationBasedExtension
import org.jetbrains.kotlin.extensions.DeclarationAttributeAltererExtension
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.resolve.BindingContext

class CliAllOpenDeclarationAttributeAltererExtension(
        private val allOpenAnnotationFqNames: List<String>
) : AbstractAllOpenDeclarationAttributeAltererExtension() {
    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner) = allOpenAnnotationFqNames
}

abstract class AbstractAllOpenDeclarationAttributeAltererExtension : DeclarationAttributeAltererExtension, AnnotationBasedExtension {
    override fun refineDeclarationModality(
            modifierListOwner: KtModifierListOwner,
            declaration: DeclarationDescriptor?,
            containingDeclaration: DeclarationDescriptor?,
            currentModality: Modality,
            bindingContext: BindingContext
    ): Modality? {
        if (currentModality != Modality.FINAL) {
            return null
        }

        // Explicit final
        if (modifierListOwner.hasModifier(KtTokens.FINAL_KEYWORD)) {
            return null
        }

        if (declaration?.hasSpecialAnnotation(modifierListOwner) ?: false) return Modality.OPEN
        if (containingDeclaration?.hasSpecialAnnotation(modifierListOwner) ?: false) return Modality.OPEN

        return null
    }
}