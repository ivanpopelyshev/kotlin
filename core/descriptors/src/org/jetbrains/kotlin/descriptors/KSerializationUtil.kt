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

@file:JvmName("KSerializationUtil")

package org.jetbrains.kotlin.descriptors

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.KotlinType

private val K_SERIALIZABLE_ANNOTATION_FQ_NAME = FqName("kotlin.serialization.KSerializable")

@get:JvmName("isSerializable")
val ClassDescriptor.isSerializable: Boolean
    get() = annotations.hasAnnotation(K_SERIALIZABLE_ANNOTATION_FQ_NAME)

val ClassDescriptor.serializableProperties: List<PropertyDescriptor>
    get() = unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES)
            .filterIsInstance<PropertyDescriptor>()

fun getSerializableClassDescriptor(companionDescriptor: ClassDescriptor) : ClassDescriptor? {
    if (!companionDescriptor.isCompanionObject) return null
    val classDescriptor = (companionDescriptor.containingDeclaration as? ClassDescriptor) ?: return null
    if (!classDescriptor.isSerializable) return null
    return classDescriptor
}

fun isSerializerCompanion(companionDescriptor: ClassDescriptor) : Boolean = getSerializableClassDescriptor(companionDescriptor) != null

// todo: serialization: do an actual check better that just number of parameters
fun ClassDescriptor.checkSaveMethodParameters(parameters: List<ValueParameterDescriptor>) : Boolean =
        parameters.size == 2

fun ClassDescriptor.checkSaveMethodResult(type: KotlinType) : Boolean =
        KotlinBuiltIns.isUnit(type)

// todo: serialization: do an actual check better that just number of parameters
fun ClassDescriptor.checkLoadMethodParameters(parameters: List<ValueParameterDescriptor>) : Boolean =
        parameters.size == 1

// todo: serialization: do an actual check
fun ClassDescriptor.checkLoadMethodResult(type: KotlinType) : Boolean =
        true
