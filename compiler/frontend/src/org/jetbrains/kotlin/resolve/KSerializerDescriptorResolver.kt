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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.createProjection

object KSerializerDescriptorResolver {

    @JvmField val SAVE = "save"
    @JvmField val LOAD = "load"

    val SAVE_NAME = Name.identifier(SAVE)
    val LOAD_NAME = Name.identifier(LOAD)

    fun createSaveFunctionDescriptor(companionDescriptor: ClassDescriptor, classDescriptor: ClassDescriptor): SimpleFunctionDescriptor =
            doCreateSerializerFunction(companionDescriptor, classDescriptor, SAVE_NAME)

    fun createLoadFunctionDescriptor(companionDescriptor: ClassDescriptor, classDescriptor: ClassDescriptor): SimpleFunctionDescriptor =
            doCreateSerializerFunction(companionDescriptor, classDescriptor, LOAD_NAME)

    private fun doCreateSerializerFunction(
            companionDescriptor: ClassDescriptor,
            classDescriptor: ClassDescriptor,
            name: Name
    ): SimpleFunctionDescriptor {
        val functionDescriptor = SimpleFunctionDescriptorImpl.create(
                companionDescriptor, Annotations.EMPTY, name, CallableMemberDescriptor.Kind.SYNTHESIZED, companionDescriptor.source
        )

        val typeParam = listOf(createProjection(classDescriptor.defaultType, Variance.INVARIANT, null))
        val functionFromSerializer = companionDescriptor.builtIns.kSerializer.getMemberScope(typeParam)
                .getContributedFunctions(name, NoLookupLocation.FROM_BUILTINS).single()

        functionDescriptor.initialize(
                null,
                companionDescriptor.thisAsReceiverParameter,
                functionFromSerializer.typeParameters,
                functionFromSerializer.valueParameters.map { it.copy(functionDescriptor, it.name, it.index) },
                functionFromSerializer.returnType,
                Modality.OPEN,
                Visibilities.PUBLIC
        )

        return functionDescriptor
    }

    fun createLoadConstructorDescriptor(
            constructorParameters: List<ValueParameterDescriptor>,
            classDescriptor: ClassDescriptor,
            trace: BindingTrace
    ): ConstructorDescriptor {
        val functionDescriptor = ClassConstructorDescriptorImpl.create(
                classDescriptor,
                Annotations.EMPTY,
                true,
                classDescriptor.source
        )

        val parameterDescriptors = arrayListOf<ValueParameterDescriptor>()

        for (parameter in constructorParameters) {
            val propertyDescriptor = trace.bindingContext.get(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter)
            // If a parameter doesn't have the corresponding property, it must not have a default value in the 'copy' function
            val declaresDefaultValue = propertyDescriptor != null
            val parameterDescriptor = ValueParameterDescriptorImpl(
                    functionDescriptor, null, parameter.index, parameter.annotations, parameter.name, parameter.type, declaresDefaultValue,
                    parameter.isCrossinline, parameter.isNoinline, parameter.isCoroutine, parameter.varargElementType, parameter.source
            )
            parameterDescriptors.add(parameterDescriptor)
            if (declaresDefaultValue) {
                trace.record(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameterDescriptor, propertyDescriptor)
            }
        }

        functionDescriptor.initialize(
                constructorParameters,
                Visibilities.INTERNAL
        )

        trace.record(BindingContext.SERIALIZABLE_CLASS_LOAD_CONSTRUCTOR, classDescriptor, functionDescriptor)
        return functionDescriptor
    }
}
