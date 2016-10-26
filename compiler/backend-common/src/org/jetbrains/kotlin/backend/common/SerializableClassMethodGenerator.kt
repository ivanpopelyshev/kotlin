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

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.serializableProperties
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils

// todo: generate constructor for deserialization
abstract class SerializableClassMethodGenerator(declaration: KtClassOrObject, bindingContext: BindingContext) {
    protected val classDescriptor: ClassDescriptor = BindingContextUtils.getNotNull(bindingContext, BindingContext.CLASS, declaration)

    fun generate() {
        val properties = classDescriptor.serializableProperties
        generateLoadConstructor(properties)
    }

    protected abstract fun generateLoadConstructor(properties: List<PropertyDescriptor>)
}
