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

package org.jetbrains.kotlin.fir;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ReadOnly;
import org.jetbrains.kotlin.psi.*;

import java.util.List;

/**
 * Class or Object in Frontend Intermediate Representation (FIR), can be synthetic.
 */
public interface FirClassOrObject extends FirElement, KtDeclarationContainer {
    @Nullable String getName();
    boolean isLocal();

    @NotNull @ReadOnly List<KtSuperTypeListEntry> getSuperTypeListEntries();
    @NotNull @ReadOnly List<KtObjectDeclaration> getCompanionObjects();

    boolean hasExplicitPrimaryConstructor();
    boolean hasPrimaryConstructor();
    @Nullable KtPrimaryConstructor getPrimaryConstructor();
    @Nullable KtModifierList getPrimaryConstructorModifierList();
    @NotNull @ReadOnly List<KtParameter> getPrimaryConstructorParameters();
    @NotNull @ReadOnly List<KtSecondaryConstructor> getSecondaryConstructors();
}