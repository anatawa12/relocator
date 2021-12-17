package com.anatawa12.relocator.reflect

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.FieldRef
import com.anatawa12.relocator.internal.MethodRef
import com.anatawa12.relocator.internal.internal
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference
import org.objectweb.asm.Type

class ReflectionMappingContainer {
    private val methods: MutableMap<MethodReference, MemberRef> =
        Reflects.defaultMethodMap.toMutableMap()

    fun addClass(reference: MethodReference, classRef: ClassRef) {
        require (Type.getReturnType(reference.descriptor).descriptor in Reflects.classTypes) { 
            "$reference will never return class instance"
        }
        methods[reference] = classRef.internal.apply { checkUsable(ParameterDescriptors(reference)) }
    }

    fun addField(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        require (Type.getReturnType(reference.descriptor).descriptor in Reflects.fieldTypes) { 
            "$reference will never return field instance"
        }
        methods[reference] = FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) }
    }

    fun addMethod(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        require (Type.getReturnType(reference.descriptor).descriptor in Reflects.methodTypes) { 
            "$reference will never return method instance"
        }
        methods[reference] = MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) }
    }

    private val fields: MutableMap<FieldReference, MemberRef> =
        Reflects.defaultFieldMap.toMutableMap()

    fun addClass(reference: FieldReference, classRef: ClassRef) {
        require (reference.descriptor in Reflects.classTypes) { "$reference will never class instance" }
        fields[reference] = classRef.internal.apply { checkUsable(ParameterDescriptors(reference)) }
    }

    fun addField(reference: FieldReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        require (reference.descriptor in Reflects.fieldTypes) { "$reference will never field instance" }
        fields[reference] = FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) }
    }

    fun addMethod(reference: FieldReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        require (reference.descriptor in Reflects.methodTypes) { "$reference will never method instance" }
        fields[reference] = MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) }
    }

    init {
        reflectionMappingMethods = ReflectionMappingContainer::methods
        reflectionMappingFields = ReflectionMappingContainer::fields
    }
}
