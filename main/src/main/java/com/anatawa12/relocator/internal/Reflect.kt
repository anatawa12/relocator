package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.reference.*
import com.anatawa12.relocator.reflect.StringRef as PublicStringRef
import com.anatawa12.relocator.reflect.ClassRef as PublicClassRef

class ParameterDescriptors(
    val self: TypeDescriptor,
    val parameters: List<TypeDescriptor>,
) {
    constructor(self: MethodReference) : this(self.owner.asTypeDescriptor(), self.descriptor.arguments)

    constructor(self: FieldReference) : this(self.owner.asTypeDescriptor(), emptyList())

    fun require(index: Int, type: TypeDescriptor) {
        val actual = if (index == -1) self else parameters.getOrNull(index)
        require(actual == type) {
            val name = if (index == -1) "receiver" else "$index-th parameter"
            "the $name type mismatch with Ref: expected '$type' but was '$actual'"
        }
    }
}

class ParametersContainer(
    val self: Any?,
    val parameters: List<Any?>,
) {
    fun get(index: Int): Any? = if (index == -1) self as? Constant else parameters[index]
}

sealed class MemberRef {
    abstract fun checkUsable(desc: ParameterDescriptors)
    abstract fun resolve(params: ParametersContainer): Any?
}

internal sealed class StringRef {
    class Param(val index: Int) : StringRef() {
        override val data: String get() = getParamName(index)

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(index, TypeDescriptor("L${"java/lang/String"};"))
        }

        override fun resolve(params: ParametersContainer): String? = params.get(index) as? String
    }

    class Constant(val value: String) : StringRef() {
        override val data: String get() = "'$value'"

        override fun checkUsable(desc: ParameterDescriptors) {
        }

        override fun resolve(params: ParametersContainer): String = value
    }

    class Joined(val values: List<StringRef>) : StringRef() {
        override val data: String get() = values.joinToString(" + ") { it.data }

        override fun checkUsable(desc: ParameterDescriptors) {
            for (value in values)
                value.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): String? = buildString {
            for (value in values) append(value.resolve(params) ?: return null)
        }
    }

    abstract val data: String

    override fun toString(): String {
        return "StringRef(${data})"
    }

    abstract fun checkUsable(desc: ParameterDescriptors)
    abstract fun resolve(params: ParametersContainer): String?

    companion object {
        @JvmStatic
        fun param(index: Int) = Param(checkIndex(index))
        @JvmStatic
        fun constant(value: String) = Constant(value)
        @JvmStatic
        fun joined(values: Array<PublicStringRef>) = Joined(values.map { it.internal })
        @JvmStatic
        fun joined(values: List<PublicStringRef>) = Joined(values.map { it.internal })

        @JvmField
        val thisParam = Param(-1)
    }
}

internal sealed class ClassRef : MemberRef() {
    class Named(val name: StringRef) : ClassRef() {
        override fun toString(): String = "ClassRef(name=${name.data})"

        override fun checkUsable(desc: ParameterDescriptors) {
            name.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): ConstantClass? {
            val string = ClassReference(name.resolve(params) ?: return null).name
            return if (string[0] == '[') ConstantClass(TypeDescriptor(string)) else ConstantClass(TypeDescriptor("L${string};"))
        }
    }
    class Descriptor(val name: StringRef) : ClassRef() {
        override fun toString(): String = "ClassRef(descriptor=${name.data})"

        override fun checkUsable(desc: ParameterDescriptors) {
            name.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): ConstantClass? {
            return ConstantClass(TypeDescriptor(name.resolve(params)?.replace('.', '/') ?: return null))
        }
    }

    class Param(val index: Int) : ClassRef() {
        override fun toString(): String = "ClassRef(instance=${getParamName(index)})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(index, TypeDescriptor("L${"java/lang/Class"};"))
        }

        override fun resolve(params: ParametersContainer): ConstantClass? = params.get(index) as? ConstantClass
    }

    override abstract fun resolve(params: ParametersContainer): ConstantClass?

    companion object {
        @JvmField val VOID = Descriptor(StringRef.Constant("V"))
        @JvmField val BYTE = Descriptor(StringRef.Constant("B"))
        @JvmField val CHAR = Descriptor(StringRef.Constant("C"))
        @JvmField val DOUBLE = Descriptor(StringRef.Constant("D"))
        @JvmField val FLOAT = Descriptor(StringRef.Constant("F"))
        @JvmField val INT = Descriptor(StringRef.Constant("I"))
        @JvmField val LONG = Descriptor(StringRef.Constant("J"))
        @JvmField val SHORT = Descriptor(StringRef.Constant("S"))
        @JvmField val BOOLEAN = Descriptor(StringRef.Constant("Z"))

        @JvmStatic
        fun named(nameRef: PublicStringRef) = Named(nameRef.internal)

        @JvmStatic
        fun descriptor(nameRef: PublicStringRef) : Descriptor {
            val internal = nameRef.internal
            if (internal is StringRef.Constant && internal.data.length == 1) {
                when (internal.data[0]) {
                    'V' -> return VOID
                    'B' -> return BYTE
                    'C' -> return CHAR
                    'D' -> return DOUBLE
                    'F' -> return FLOAT
                    'I' -> return INT
                    'J' -> return LONG
                    'S' -> return SHORT
                    'Z' -> return BOOLEAN
                }
            }

            return Descriptor(internal)
        }

        @JvmStatic
        fun param(index: Int) = Param(checkIndex(index))

        @JvmField
        val thisParam = Param(-1)
    }
}

internal sealed class MethodTypeRef {
    class PartialDescriptor(val descriptor: StringRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(descriptor=${descriptor.data})"

        override fun checkUsable(desc: ParameterDescriptors) {
            descriptor.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): PartialMethodDescriptor? {
            return kotlin.runCatching { descriptor.resolve(params)?.let(::PartialMethodDescriptor) }.getOrNull()
        }
    }

    class FullDescriptor(val descriptor: StringRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(descriptor=${descriptor.data})"

        override fun checkUsable(desc: ParameterDescriptors) {
            descriptor.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): MethodDescriptor? {
            return kotlin.runCatching { descriptor.resolve(params)?.let(::MethodDescriptor) }.getOrNull()
        }
    }

    class ParameterTypes(val paramsIndex: Int) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(params=${getParamName(paramsIndex)})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(paramsIndex, TypeDescriptor("[L${"java/lang/Class"};"))
        }

        override fun resolve(params: ParametersContainer): PartialMethodDescriptor? {
            val args = params.get(paramsIndex) as? List<*> ?: return null
            if (args.any { it !is ConstantClass }) return null
            return PartialMethodDescriptor(args.map { (it as ConstantClass).descriptor })
        }
    }

    class ParameterAndReturnTypes(val paramsIndex: Int, val returns: ClassRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(params=${getParamName(paramsIndex)}, return=${returns})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(paramsIndex, TypeDescriptor("[L${"java/lang/Class"};"))
            returns.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): MethodDescriptor? {
            val args = params.get(paramsIndex) as? List<*> ?: return null
            if (args.any { it !is ConstantClass }) return null
            val returns = returns.resolve(params) ?: return null
            return MethodDescriptor(returns.descriptor, args.map { (it as ConstantClass).descriptor })
        }
    }

    class Param(val index: Int) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(instance=${getParamName(index)})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(index, TypeDescriptor("[L${"java/lang/Class"};"))
        }

        override fun resolve(params: ParametersContainer): MethodDescriptor? =
            (params.get(index) as? ConstantMethodType?)?.descriptor
    }

    abstract fun checkUsable(desc: ParameterDescriptors)
    abstract fun resolve(params: ParametersContainer): AnyMethodDescriptor?

    companion object {
        @JvmStatic
        fun partialDescriptor(descriptor: PublicStringRef) = PartialDescriptor(descriptor.internal)
        @JvmStatic
        fun fullDescriptor(descriptor: PublicStringRef) = FullDescriptor(descriptor.internal)
        @JvmStatic
        fun fullDescriptor(descriptor: String) = FullDescriptor(StringRef.constant(descriptor))
        @JvmStatic
        fun parameterTypes(paramsIndex: Int) = ParameterTypes(checkIndex(paramsIndex))
        @JvmStatic
        fun thisParametersAndReturnTypes(returns: PublicClassRef) =
            ParameterAndReturnTypes(-1, returns.internal)
        @JvmStatic
        fun parameterAndReturnTypes(paramsIndex: Int, returns: PublicClassRef) =
            ParameterAndReturnTypes(checkIndex(paramsIndex), returns.internal)
        @JvmStatic
        fun param(index: Int) = Param(checkIndex(index))

        @JvmField
        val thisParameterTypes = ParameterTypes(-1)
        @JvmField
        val thisParam = Param(-1)
    }
}

internal class FieldRef(val owner: ClassRef, val name: StringRef, val type: ClassRef?) : MemberRef() {
    override fun checkUsable(desc: ParameterDescriptors) {
        owner.checkUsable(desc)
        name.checkUsable(desc)
        type?.checkUsable(desc)
    }

    override fun resolve(params: ParametersContainer): Reference? {
        val owner = owner.resolve(params) ?: return null
        val name = name.resolve(params) ?: return null
        val type = type?.resolve(params)
        if (owner.descriptor.kind != TypeDescriptor.Kind.Class) return null
        val ownerInternalName = owner.descriptor.internalName
        return when {
            type == null -> PartialFieldReference(ownerInternalName, name)
            else -> FieldReference(ownerInternalName, name, type.descriptor)
        }
    }
}

internal class MethodRef(val owner: ClassRef, val name: StringRef, val type: MethodTypeRef?) : MemberRef() {
    override fun checkUsable(desc: ParameterDescriptors) {
        owner.checkUsable(desc)
        name.checkUsable(desc)
        type?.checkUsable(desc)
    }

    override fun resolve(params: ParametersContainer): Reference? {
        val owner = owner.resolve(params) ?: return null
        val name = name.resolve(params) ?: return null
        val type = type?.resolve(params)
        if (owner.descriptor.kind != TypeDescriptor.Kind.Class) return null
        val ownerInternalName = ClassReference(owner.descriptor.internalName)
        return when (type) {
            null -> TypelessMethodReference(ownerInternalName, name)
            is PartialMethodDescriptor -> PartialMethodReference(ownerInternalName, name, type)
            else -> MethodReference(ownerInternalName, name, type as MethodDescriptor)
        }
    }
}

fun checkIndex(index: Int): Int {
    require(index in 0..UShort.MAX_VALUE.toInt()) { "$index is not valid for parameter index." }
    return index
}

fun getParamName(index: Int): String {
    if (index < 0) return "this"
    return "param#$index"
}

object Reflects {
    val classTypes = setOf(
        TypeDescriptor("L${"java/lang/Class"};"),
        TypeDescriptor("L${"java/lang/Object"};"),
        TypeDescriptor("L${"java/io/Serializable"};"), 
        TypeDescriptor("L${"java/lang/constant/Constable"};"), 
        TypeDescriptor("L${"java/lang/invoke/TypeDescriptor"};"),
        TypeDescriptor("L${"java/lang/invoke/TypeDescriptor\$OfField"};"),
        TypeDescriptor("L${"java/lang/reflect/AnnotatedElement"};"),
        TypeDescriptor("L${"java/lang/reflect/GenericDeclaration"};"),
        TypeDescriptor("L${"java/lang/reflect/Type"};"),
    )

    val fieldTypes = setOf(
        TypeDescriptor("L${"java/lang/reflect/Field"};"),
        TypeDescriptor("L${"java/lang/reflect/AccessibleObject"};"),
        TypeDescriptor("L${"java/lang/Object"};"),
        TypeDescriptor("L${"java/lang/reflect/AnnotatedElement"};"),
        TypeDescriptor("L${"java/lang/reflect/Member"};"),
    )

    val methodTypes = setOf(
        TypeDescriptor("L${"java/lang/reflect/Method"};"),
        TypeDescriptor("L${"java/lang/reflect/Constructor"};"),
        TypeDescriptor("L${"java/lang/reflect/Executable"};"),
        TypeDescriptor("L${"java/lang/reflect/AccessibleObject"};"),
        TypeDescriptor("L${"java/lang/Object"};"),
        TypeDescriptor("L${"java/lang/reflect/Executable"};"),
        TypeDescriptor("L${"java/lang/reflect/AnnotatedElement"};"),
        TypeDescriptor("L${"java/lang/reflect/GenericDeclaration"};"),
        TypeDescriptor("L${"java/lang/reflect/Member"};"),
    )
}
