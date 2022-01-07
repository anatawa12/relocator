package com.anatawa12.relocator.classes

import com.anatawa12.relocator.reference.ClassReference

sealed class AnnotationValue() {
    abstract override fun toString(): String
}

class KeyValuePair(var key: String, var value: AnnotationValue)

class ClassAnnotation : AnnotationValue {
    var annotationClass: ClassReference
    var values: MutableList<KeyValuePair>

    constructor(annotationClass: ClassReference, values: List<KeyValuePair>) {
        this.annotationClass = annotationClass
        this.values = values.toMutableList()
    }

    constructor(annotationClass: ClassReference, vararg values: KeyValuePair)
            : this(annotationClass, values.asList())

    override fun toString(): String = "@${annotationClass}(${values.joinToString { "${it.key}=${it.value}" }})"
}

class AnnotationByte(var value: Byte) : AnnotationValue() {
    override fun toString(): String = "byte $value"
}
class AnnotationBoolean(var value: Boolean) : AnnotationValue() {
    override fun toString(): String = "boolean $value"
}
class AnnotationChar(var value: Char) : AnnotationValue() {
    override fun toString(): String = "char $value"
}
class AnnotationShort(var value: Short) : AnnotationValue() {
    override fun toString(): String = "short $value"
}
class AnnotationInt(var value: Int) : AnnotationValue() {
    override fun toString(): String = "int $value"
}
class AnnotationLong(var value: Long) : AnnotationValue() {
    override fun toString(): String = "long $value"
}
class AnnotationFloat(var value: Float) : AnnotationValue() {
    override fun toString(): String = "float $value"
}
class AnnotationDouble(var value: Double) : AnnotationValue() {
    override fun toString(): String = "double $value"
}
class AnnotationString(var value: String) : AnnotationValue() {
    override fun toString(): String = "string $value"
}
class AnnotationEnum(var owner: ClassReference, val value: String) : AnnotationValue() {
    override fun toString(): String = "enum $owner.$value"
}
class AnnotationClass(var descriptor: TypeDescriptor) : AnnotationValue() {
    constructor(descriptor: String) : this(TypeDescriptor(descriptor))
    override fun toString(): String = "class $descriptor"
}
 class AnnotationArray private constructor(val values: List<AnnotationValue>, @Suppress("UNUSED_PARAMETER") dummy: Int) :
    AnnotationValue(), List<AnnotationValue> by values {
    constructor(values: List<AnnotationValue>): this(values.toMutableList(), 0)
    constructor(vararg values: AnnotationValue): this(values.asList())
    constructor(values: ByteArray): this(values.map(::AnnotationByte))
    constructor(values: BooleanArray): this(values.map(::AnnotationBoolean))
    constructor(values: CharArray): this(values.map(::AnnotationChar))
    constructor(values: ShortArray): this(values.map(::AnnotationShort))
    constructor(values: IntArray): this(values.map(::AnnotationInt))
    constructor(values: LongArray): this(values.map(::AnnotationLong))
    constructor(values: FloatArray): this(values.map(::AnnotationFloat))
    constructor(values: DoubleArray): this(values.map(::AnnotationDouble))
    constructor(values: Array<String>): this(values.map(::AnnotationString))

    override fun toString(): String = values.joinToString()
    override fun equals(other: Any?): Boolean = values == other
    override fun hashCode(): Int = values.hashCode()
}
