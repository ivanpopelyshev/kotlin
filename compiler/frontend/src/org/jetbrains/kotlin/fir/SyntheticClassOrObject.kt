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

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.psi.*

/**
 * Synthetic Class or Object in Frontend Intermediate Representation (FIR).
 * It is used to create synthetic companion objects.
 */
class SyntheticClassOrObject(
        private val _parent: FirElement,
        private val _name: String
) : FirClassOrObject {
    override fun getName(): String? = _name
    override fun isLocal(): Boolean = false

    override fun getDeclarations(): List<KtDeclaration> = emptyList()
    override fun getSuperTypeListEntries(): List<KtSuperTypeListEntry> = emptyList()
    override fun getCompanionObjects(): List<KtObjectDeclaration> = emptyList()

    override fun hasExplicitPrimaryConstructor(): Boolean = false
    override fun hasPrimaryConstructor(): Boolean = false
    override fun getPrimaryConstructor(): KtPrimaryConstructor? = null
    override fun getPrimaryConstructorModifierList(): KtModifierList? = null
    override fun getPrimaryConstructorParameters(): List<KtParameter> = emptyList()
    override fun getSecondaryConstructors(): List<KtSecondaryConstructor> = emptyList()

    override fun getKt() = null
    override fun getPsiOrParent() = _parent.psiOrParent
    override fun getParent() = _parent.psiOrParent
    override fun getContainingKtFile() = _parent.containingKtFile
}
