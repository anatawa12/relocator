package com.anatawa12.relocator.reflect

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.FieldRef
import com.anatawa12.relocator.internal.MethodRef
import com.anatawa12.relocator.internal.internal
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference
import com.google.common.collect.HashMultimap

class ReflectionMappingContainer private constructor(
    private val methods: MutableMap<MethodReference, MemberRef>,
    private val fields: MutableMap<FieldReference, MemberRef>,
) {
    private val refMethods = HashMultimap.create<MethodReference, MemberRef>()
    private val refFields = HashMultimap.create<FieldReference, MemberRef>()
    constructor() : this(Default.default.methods.toMutableMap(), Default.default.fields.toMutableMap())

    fun addClass(reference: MethodReference, classRef: ClassRef) {
        require (reference.descriptor.returns in Reflects.classTypes) { 
            "$reference will never return class instance"
        }
        methods[reference] = classRef.internal.apply { checkUsable(ParameterDescriptors(reference)) }
    }

    fun addField(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        require (reference.descriptor.returns in Reflects.fieldTypes) { 
            "$reference will never return field instance"
        }
        methods[reference] = FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) }
    }

    fun addMethod(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        require (reference.descriptor.returns in Reflects.methodTypes) { 
            "$reference will never return method instance"
        }
        methods[reference] = MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) }
    }

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

    fun addRefClass(reference: MethodReference, classRef: ClassRef) {
        refMethods.put(reference, classRef.internal.apply { checkUsable(ParameterDescriptors(reference)) })
    }

    fun addRefField(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        refMethods.put(reference, FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) })
    }

    fun addRefMethod(reference: MethodReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        refMethods.put(reference, MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) })
    }

    fun addRefClass(reference: FieldReference, classRef: ClassRef) {
        refFields.put(reference, classRef.internal.apply { checkUsable(ParameterDescriptors(reference)) })
    }

    fun addRefField(reference: FieldReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: ClassRef?) {
        refFields.put(reference, FieldRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) })
    }

    fun addRefMethod(reference: FieldReference, ownerRef: ClassRef, nameRef: StringRef, typeRef: MethodTypeRef?) {
        refFields.put(reference, MethodRef(ownerRef.internal, nameRef.internal, typeRef?.internal)
            .apply { checkUsable(ParameterDescriptors(reference)) })
    }

    private object Default {
        @JvmField
        val default = ReflectionMappingContainer(mutableMapOf(), mutableMapOf())
        
        init {
            default.run {
                val classLoader = ClassReference("java/lang/ClassLoader")
                val string = ClassReference("java/lang/String")
                val clazz = ClassReference("java/lang/Class")
                val module = ClassReference("java/lang/Module")
                val field = ClassReference("java/lang/reflect/Field")
                val method = ClassReference("java/lang/reflect/Method")
                val constructor = ClassReference("java/lang/reflect/Constructor")

                addClass(MethodReference(classLoader, "loadClass", "(L$string;)L$clazz;"),
                    ClassRef.named(StringRef.param(0)))
                addClass(MethodReference(classLoader, "loadClass", "(L$string;B)L$clazz;"),
                    ClassRef.named(StringRef.param(0)))
                addClass(MethodReference(clazz, "forName", "(L$module;L$string;)L$clazz;"),
                    ClassRef.named(StringRef.param(1)))
                addClass(MethodReference(clazz, "forName", "(L$string;)L$clazz;"),
                    ClassRef.named(StringRef.param(0)))
                addClass(MethodReference(clazz, "forName", "(L$string;ZL$classLoader;)L$clazz;"),
                    ClassRef.named(StringRef.param(0)))

                addField(MethodReference(clazz, "getField", "(L$string;)L$field;"),
                    ClassRef.thisParam, StringRef.param(0), null)
                addMethod(MethodReference(clazz, "getMethod", "(L$string;[L$clazz;)L$method;"),
                    ClassRef.thisParam, StringRef.param(0), MethodTypeRef.parameterTypes(1))
                addMethod(MethodReference(clazz, "getConstructor", "([L$clazz;)L$constructor;"),
                    ClassRef.thisParam, StringRef.constant("<init>"), MethodTypeRef.parameterTypes(0))

                addField(MethodReference(clazz, "getDeclaredField", "(L$string;)L$field;"),
                    ClassRef.thisParam, StringRef.param(0), null)
                addMethod(MethodReference(clazz, "getDeclaredMethod", "(L$string;[L$clazz;)L$method;"),
                    ClassRef.thisParam, StringRef.param(0), MethodTypeRef.parameterTypes(1))
                addMethod(MethodReference(clazz, "getDeclaredConstructor", "([L$clazz;)L$constructor;"),
                    ClassRef.thisParam, StringRef.constant("<init>"), MethodTypeRef.parameterTypes(0))

                addClass(FieldReference("java/lang/Void", "TYPE", "L${"java/lang/Class"};"), ClassRef.VOID)
                addClass(FieldReference("java/lang/Integer", "TYPE", "L${"java/lang/Class"};"), ClassRef.INT)
                addClass(FieldReference("java/lang/Long", "TYPE", "L${"java/lang/Class"};"), ClassRef.LONG)
                addClass(FieldReference("java/lang/Float", "TYPE", "L${"java/lang/Class"};"), ClassRef.FLOAT)
                addClass(FieldReference("java/lang/Double", "TYPE", "L${"java/lang/Class"};"), ClassRef.DOUBLE)
                addClass(FieldReference("java/lang/Byte", "TYPE", "L${"java/lang/Class"};"), ClassRef.BYTE)
                addClass(FieldReference("java/lang/Character", "TYPE", "L${"java/lang/Class"};"), ClassRef.CHAR)
                addClass(FieldReference("java/lang/Short", "TYPE", "L${"java/lang/Class"};"), ClassRef.SHORT)
                addClass(FieldReference("java/lang/Boolean", "TYPE", "L${"java/lang/Class"};"), ClassRef.BOOLEAN)
            }

            reflectionMappingMethods = ReflectionMappingContainer::methods
            reflectionMappingRefMethods = ReflectionMappingContainer::refMethods
            reflectionMappingFields = ReflectionMappingContainer::fields
            reflectionMappingRefFields = ReflectionMappingContainer::refFields
        }
    }
}
