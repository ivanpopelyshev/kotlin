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

import org.jetbrains.kotlin.backend.common.CodegenUtil.getMemberToGenerate
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.FirClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.KSerializerDescriptorResolver

abstract class SerializerCompanionMethodGenerator(declaration: FirClassOrObject, bindingContext: BindingContext) {
    protected val companionDescriptor: ClassDescriptor = BindingContextUtils.getNotNull(bindingContext, BindingContext.CLASS, declaration)
    protected val classDescriptor: ClassDescriptor = getSerializableClassDescriptor(companionDescriptor)!!

    fun generate() {
        val properties = classDescriptor.serializableProperties
        generateCompanionSaveIfNeeded(properties)
        generateCompanionLoadIfNeeded(properties)
    }

    protected abstract fun generateCompanionSave(function: FunctionDescriptor, properties: List<PropertyDescriptor>)

    protected abstract fun generateCompanionLoad(function: FunctionDescriptor, properties: List<PropertyDescriptor>)

    private fun generateCompanionSaveIfNeeded(properties: List<PropertyDescriptor>) {
        val function = getMemberToGenerate(companionDescriptor, KSerializerDescriptorResolver.SAVE,
                                           companionDescriptor::checkSaveMethodResult, companionDescriptor::checkSaveMethodParameters) ?: return
        generateCompanionSave(function, properties)
    }

    private fun generateCompanionLoadIfNeeded(properties: List<PropertyDescriptor>) {
        val function = getMemberToGenerate(companionDescriptor, KSerializerDescriptorResolver.LOAD,
                                           companionDescriptor::checkLoadMethodResult, companionDescriptor::checkLoadMethodParameters) ?: return
        generateCompanionLoad(function, properties)
    }
}
