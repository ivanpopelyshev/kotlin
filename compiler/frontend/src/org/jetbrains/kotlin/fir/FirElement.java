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

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;

/**
 * Element in Frontend Intermediate Representation (FIR), can be synthetic.
 */
public interface FirElement {
    /**
     * Returns the actual source element if present, null if synthetic.
     */
    @Nullable KtElement getKt();

    /**
     * Returns this or parent source element for synthetic front-end elements.
     * Use it only for the purposes of source attribution.
     */
    @NotNull PsiElement getPsiOrParent();

    /**
     * Returns parent source element.
     */
    @NotNull PsiElement getParent();

    @NotNull KtFile getContainingKtFile();
}
