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

@file:JvmName("FirUtil")

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor

// Get the synthetic class or object node out of the class descriptor which was produced synthetically (without any real declaration in PSI)
val ClassDescriptor?.syntheticClassOrObject: SyntheticClassOrObject?
    get() = (this as? LazyClassDescriptor)?.correspondingClassOrObject as? SyntheticClassOrObject
