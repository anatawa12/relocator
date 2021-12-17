package com.anatawa12.relocator.reflect

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.FieldRef
import com.anatawa12.relocator.internal.MethodRef
import com.anatawa12.relocator.internal.internal
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference

class ReflectionMappingContainer {
    private val methods: MutableMap<MethodReference, MemberRef> =
        Reflects.defaultMethodMap.toMutableMap()

    fun addClass(reference: MethodReference, classRef: ClassRef) {
        methods[reference] = classRef.internal
    }

    fun addField(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        methods[reference] = FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
    }

    fun addMethod(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        methods[reference] = MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
    }

    private val fields: MutableMap<FieldReference, MemberRef> =
        Reflects.defaultFieldMap.toMutableMap()

    fun addClass(reference: FieldReference, classRef: ClassRef) {
        fields[reference] = classRef.internal
    }

    fun addField(reference: FieldReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        fields[reference] = FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
    }

    fun addMethod(reference: FieldReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        fields[reference] = MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
    }

    init {
        reflectionMappingMethods = ReflectionMappingContainer::methods
        reflectionMappingFields = ReflectionMappingContainer::fields
    }
}
