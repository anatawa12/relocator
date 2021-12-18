package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.reference.*
import org.objectweb.asm.Type
import com.anatawa12.relocator.reflect.StringRef as PublicStringRef
import com.anatawa12.relocator.reflect.ClassRef as PublicClassRef

class ParameterDescriptors(
    val self: String,
    val parameters: List<String>,
) {
    constructor(self: MethodReference) : this(
        "L${self.owner.name};",
        Type.getType(self.descriptor).argumentTypes.map { it.descriptor },
    )

    constructor(self: FieldReference) : this("L${self.owner.name};", emptyList())

    fun require(index: Int, type: String) {
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
            desc.require(index, "L${"java/lang/String"};")
        }

        override fun resolve(params: ParametersContainer): String? =
            (params.get(index) as? ConstantString)?.value
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
            return if (string[0] == '[') ConstantClass(string) else ConstantClass("L${string};")
        }
    }
    class Descriptor(val name: StringRef) : ClassRef() {
        override fun toString(): String = "ClassRef(descriptor=${name.data})"

        override fun checkUsable(desc: ParameterDescriptors) {
            name.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): ConstantClass? {
            return ConstantClass(name.resolve(params)?.replace('.', '/') ?: return null)
        }
    }

    class Param(val index: Int) : ClassRef() {
        override fun toString(): String = "ClassRef(instance=${getParamName(index)})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(index, "L${"java/lang/Class"};")
        }

        override fun resolve(params: ParametersContainer): ConstantClass? = params.get(index) as? ConstantClass
    }

    override abstract fun resolve(params: ParametersContainer): ConstantClass?

    companion object {
        @JvmField var VOID = Descriptor(StringRef.Constant("V"))
        @JvmField var BYTE = Descriptor(StringRef.Constant("B"))
        @JvmField var CHAR = Descriptor(StringRef.Constant("C"))
        @JvmField var DOUBLE = Descriptor(StringRef.Constant("D"))
        @JvmField var FLOAT = Descriptor(StringRef.Constant("F"))
        @JvmField var INT = Descriptor(StringRef.Constant("I"))
        @JvmField var LONG = Descriptor(StringRef.Constant("J"))
        @JvmField var SHORT = Descriptor(StringRef.Constant("S"))
        @JvmField var BOOLEAN = Descriptor(StringRef.Constant("Z"))

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

        override fun resolve(params: ParametersContainer): String? {
            return descriptor.resolve(params)
                ?.takeIf { it.last() == ')' }
        }
    }

    class FullDescriptor(val descriptor: StringRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(descriptor=${descriptor.data})"

        override fun checkUsable(desc: ParameterDescriptors) {
            descriptor.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): String? {
            return descriptor.resolve(params)
                ?.takeIf { it.last() != ')' }
        }
    }

    class ParameterTypes(val paramsIndex: Int) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(params=${getParamName(paramsIndex)})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(paramsIndex, "[L${"java/lang/Class"};")
        }

        override fun resolve(params: ParametersContainer): String? {
            val args = params.get(paramsIndex) as? List<*> ?: return null
            if (args.any { it !is ConstantClass }) return null
            return buildString {
                append('(')
                @Suppress("UNCHECKED_CAST")
                for (constantClass in args as List<ConstantClass>)
                    append(constantClass.descriptor)
                append(')')
            }
        }
    }

    class ParameterAndReturnTypes(val paramsIndex: Int, val returns: ClassRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(params=${getParamName(paramsIndex)}, return=${returns})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(paramsIndex, "[L${"java/lang/Class"};")
            returns.checkUsable(desc)
        }

        override fun resolve(params: ParametersContainer): String? {
            val args = params.get(paramsIndex) as? List<*> ?: return null
            if (args.any { it !is ConstantClass }) return null
            val returns = returns.resolve(params) ?: return null
            return buildString {
                append('(')
                @Suppress("UNCHECKED_CAST")
                for (constantClass in args as List<ConstantClass>)
                    append(constantClass.descriptor)
                append(')')
                append(returns.descriptor)
            }
        }
    }

    class Param(val index: Int) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(instance=${getParamName(index)})"

        override fun checkUsable(desc: ParameterDescriptors) {
            desc.require(index, "[L${"java/lang/Class"};")
        }

        override fun resolve(params: ParametersContainer): String? =
            (params.get(index) as? ConstantMethodType?)?.descriptor
    }

    abstract fun checkUsable(desc: ParameterDescriptors)
    abstract fun resolve(params: ParametersContainer): String?

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
        if (owner.descriptor[0] != 'L') return null
        val ownerInternalName = owner.descriptor.substring(1, owner.descriptor.length - 1)
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
        if (owner.descriptor[0] != 'L') return null
        val ownerInternalName = owner.descriptor.substring(1, owner.descriptor.length - 1)
        return when {
            type == null -> TypelessMethodReference(ownerInternalName, name)
            type.last() == ')' -> PartialMethodReference(ownerInternalName, name, type)
            else -> MethodReference(ownerInternalName, name, type)
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
        "L${"java/lang/Class"};",
        "L${"java/lang/Object"};",
        "L${"java/io/Serializable"};", 
        "L${"java/lang/constant/Constable"};", 
        "L${"java/lang/invoke/TypeDescriptor"};",
        "L${"java/lang/invoke/TypeDescriptor\$OfField"};",
        "L${"java/lang/reflect/AnnotatedElement"};",
        "L${"java/lang/reflect/GenericDeclaration"};",
        "L${"java/lang/reflect/Type"};",
    )

    val fieldTypes = setOf(
        "L${"java/lang/reflect/Field"};",
        "L${"java/lang/reflect/AccessibleObject"};",
        "L${"java/lang/Object"};",
        "L${"java/lang/reflect/AnnotatedElement"};",
        "L${"java/lang/reflect/Member"};",
    )

    val methodTypes = setOf(
        "L${"java/lang/reflect/Method"};",
        "L${"java/lang/reflect/Constructor"};",
        "L${"java/lang/reflect/Executable"};",
        "L${"java/lang/reflect/AccessibleObject"};",
        "L${"java/lang/Object"};",
        "L${"java/lang/reflect/Executable"};",
        "L${"java/lang/reflect/AnnotatedElement"};",
        "L${"java/lang/reflect/GenericDeclaration"};",
        "L${"java/lang/reflect/Member"};",
    )
}
