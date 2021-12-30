package com.anatawa12.relocator.classes

import com.anatawa12.relocator.classes.MethodHandleType.*
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference

sealed class Constant() {
}

data class ConstantInt(val value: Int) : Constant()
data class ConstantLong(val value: Long) : Constant()
data class ConstantFloat(val value: Float) : Constant()
data class ConstantDouble(val value: Double) : Constant()
data class ConstantString(val value: String) : Constant()
data class ConstantClass(val descriptor: TypeDescriptor) : Constant() {
    constructor(descriptor: String): this(TypeDescriptor(descriptor))
}
data class ConstantMethodType(val descriptor: MethodDescriptor) : Constant()
sealed class ConstantHandle() : Constant() {
}
data class ConstantFieldHandle(
    val type: FieldHandleType,
    val field: FieldReference,
) : ConstantHandle()
data class ConstantMethodHandle(
    val type: MethodHandleType,
    val method: MethodReference,
    val isInterface: Boolean,
) : ConstantHandle() {
    private fun requireInterface() {
        require(isInterface) { "we can't call class method via $type" }
    }
    private fun requireClass() {
        require(!isInterface) { "we can't call interface method via $type" }
    }

    init {
        when (type) {
            INVOKEVIRTUAL -> requireClass()
            INVOKESTATIC -> {} // static interface method since 8
            INVOKESPECIAL -> {} // private interface method since 9
            NEWINVOKESPECIAL -> {
                requireClass()
                require(method.owner.name[0] != '[') { "we can't call array method via $type" }
                require(method.name == "<init>") { "we need to call '<init>' method via $type" }
            }
            INVOKEINTERFACE -> requireInterface()
        }
    }
}

enum class FieldHandleType {
    GETFIELD,
    GETSTATIC,
    PUTFIELD,
    PUTSTATIC,
}

enum class MethodHandleType {
    INVOKEVIRTUAL,
    INVOKESTATIC,
    INVOKESPECIAL,
    NEWINVOKESPECIAL,
    INVOKEINTERFACE,
}

data class ConstantDynamic(
    val name: String,
    val descriptor: MethodDescriptor,
    val bootstrapMethod: ConstantHandle,
    val args: List<Constant>,
) : Constant()
