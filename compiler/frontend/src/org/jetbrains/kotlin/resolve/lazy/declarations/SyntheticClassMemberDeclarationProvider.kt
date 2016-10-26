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

package org.jetbrains.kotlin.resolve.lazy.declarations

import org.jetbrains.kotlin.fir.FirClassOrObject
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.resolve.lazy.data.KtClassLikeInfo
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

class SyntheticClassMemberDeclarationProvider(
    override val correspondingClassOrObject: FirClassOrObject
) : ClassMemberDeclarationProvider {
    override val ownerInfo: KtClassLikeInfo? = null
    override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): List<KtDeclaration> = emptyList()
    override fun getFunctionDeclarations(name: Name): Collection<KtNamedFunction> = emptyList()
    override fun getPropertyDeclarations(name: Name): Collection<KtProperty> = emptyList()
    override fun getClassOrObjectDeclarations(name: Name): Collection<KtClassLikeInfo> = emptyList()
    override fun getTypeAliasDeclarations(name: Name): Collection<KtTypeAlias> = emptyList()
}
