package com.anatawa12.relocator.internal

import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference
import com.anatawa12.relocator.reflect.StringRef as PublicStringRef
import com.anatawa12.relocator.reflect.ClassRef as PublicClassRef

sealed class MemberRef

internal sealed class StringRef {
    class Param(val index: Int) : StringRef() {
        override val data: String get() = getParamName(index)
    }

    class Constant(val value: String) : StringRef() {
        override val data: String get() = "'$value'"
    }

    class Joined(val values: List<StringRef>) : StringRef() {
        override val data: String get() = values.joinToString(" + ") { it.data }
    }

    abstract val data: String

    override fun toString(): String {
        return "StringRef(${data})"
    }

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
    }
    class Descriptor(val name: StringRef) : ClassRef() {
        override fun toString(): String = "ClassRef(descriptor=${name.data})"
    }
    class Param(val index: Int) : ClassRef() {
        override fun toString(): String = "ClassRef(instance=${getParamName(index)})"
    }

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
    }
    class FullDescriptor(val descriptor: StringRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(descriptor=${descriptor.data})"
    }
    class ParameterTypes(val paramsIndex: Int) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(params=${getParamName(paramsIndex)})"
    }
    class ParameterAndReturnTypes(val paramsIndex: Int, val returns: ClassRef) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(params=${getParamName(paramsIndex)}, return=${returns})"
    }
    class Param(val index: Int) : MethodTypeRef() {
        override fun toString(): String = "MethodTypeRef(instance=${getParamName(index)})"
    }

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

internal class FieldRef(val owner: ClassRef, val name: StringRef, val type: ClassRef?) : MemberRef()
internal class MethodRef(val owner: ClassRef, val name: StringRef, val type: MethodTypeRef?) : MemberRef()

fun checkIndex(index: Int): Int {
    require(index in 0..UShort.MAX_VALUE.toInt()) { "$index is not valid for parameter index." }
    return index
}

fun getParamName(index: Int): String {
    if (index < 0) return "this"
    return "param#$index"
}

object Reflects {
    val defaultMethodMap: MutableMap<MethodReference, MemberRef> = mutableMapOf(
        MethodReference("java/lang/ClassLoader", "loadClass", "(L${"java/lang/String"};)L${"java/lang/Class"};") to
                ClassRef.Named(StringRef.Param(0)),
        MethodReference("java/lang/ClassLoader", "loadClass", "(L${"java/lang/String"};B)L${"java/lang/Class"};") to
                ClassRef.Named(StringRef.Param(0)),
        MethodReference("java/lang/Class",
            "forName",
            "(L${"java/lang/Module"};L${"java/lang/String"};)L${"java/lang/Class"};") to
                ClassRef.Named(StringRef.Param(0)),
        MethodReference("java/lang/Class", "forName", "(L${"java/lang/String"};)L${"java/lang/Class"};") to
                ClassRef.Named(StringRef.Param(0)),
        MethodReference("java/lang/Class",
            "forName",
            "(L${"java/lang/String"};BL${"java/lang/ClassLoader"};)L${"java/lang/Class"};") to
                ClassRef.Named(StringRef.Param(0)),
        MethodReference("java/lang/Class", "getField", "(L${"java/lang/String"};)L${"java/lang/reflect/Field"};") to
                FieldRef(ClassRef.param(-1), StringRef.Param(0), null),
        MethodReference("java/lang/Class",
            "getMethod",
            "(L${"java/lang/String"};[L${"java/lang/Class"};)L${"java/lang/reflect/Method"};") to
                MethodRef(ClassRef.param(-1), StringRef.Param(0), MethodTypeRef.ParameterTypes(1)),
    )

    // TODO
    val defaultFieldMap: MutableMap<FieldReference, MemberRef> = mutableMapOf(
        FieldReference("java/lang/Void", "TYPE", "L${"java/lang/Class"};") to ClassRef.VOID,
        FieldReference("java/lang/Integer", "TYPE", "L${"java/lang/Class"};") to ClassRef.INT,
        FieldReference("java/lang/Long", "TYPE", "L${"java/lang/Class"};") to ClassRef.LONG,
        FieldReference("java/lang/Float", "TYPE", "L${"java/lang/Class"};") to ClassRef.FLOAT,
        FieldReference("java/lang/Double", "TYPE", "L${"java/lang/Class"};") to ClassRef.DOUBLE,
        FieldReference("java/lang/Byte", "TYPE", "L${"java/lang/Class"};") to ClassRef.BYTE,
        FieldReference("java/lang/Character", "TYPE", "L${"java/lang/Class"};") to ClassRef.CHAR,
        FieldReference("java/lang/Short", "TYPE", "L${"java/lang/Class"};") to ClassRef.SHORT,
        FieldReference("java/lang/Boolean", "TYPE", "L${"java/lang/Class"};") to ClassRef.BOOLEAN,
    )
}
