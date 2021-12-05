package com.anatawa12.relocator.classes

import com.anatawa12.relocator.reference.ClassReference

sealed class AnnotationValue()

class KeyValuePair(val key: String, val value: AnnotationValue)

class ClassAnnotation : AnnotationValue {
    val annotationClass: ClassReference
    val values: List<KeyValuePair>

    constructor(annotationClass: ClassReference, values: List<KeyValuePair>) {
        this.annotationClass = annotationClass
        this.values = values.toList()
    }

    constructor(annotationClass: ClassReference, vararg values: KeyValuePair)
            : this(annotationClass, values.asList())
}

class AnnotationByte(val value: Byte) : AnnotationValue()
class AnnotationBoolean(val value: Boolean) : AnnotationValue()
class AnnotationChar(val value: Char) : AnnotationValue()
class AnnotationShort(val value: Short) : AnnotationValue()
class AnnotationInt(val value: Int) : AnnotationValue()
class AnnotationLong(val value: Long) : AnnotationValue()
class AnnotationFloat(val value: Float) : AnnotationValue()
class AnnotationDouble(val value: Double) : AnnotationValue()
class AnnotationString(val value: String) : AnnotationValue()
class AnnotationEnum(val owner: ClassReference, val value: String) : AnnotationValue()
class AnnotationClass(val descriptor: String) : AnnotationValue()
class AnnotationArray(values: List<AnnotationValue>) : AnnotationValue(), List<AnnotationValue> by values {
    constructor(vararg values: AnnotationValue): this(values.toList())
    constructor(values: ByteArray): this(values.map(::AnnotationByte))
    constructor(values: BooleanArray): this(values.map(::AnnotationBoolean))
    constructor(values: CharArray): this(values.map(::AnnotationChar))
    constructor(values: ShortArray): this(values.map(::AnnotationShort))
    constructor(values: IntArray): this(values.map(::AnnotationInt))
    constructor(values: LongArray): this(values.map(::AnnotationLong))
    constructor(values: FloatArray): this(values.map(::AnnotationFloat))
    constructor(values: DoubleArray): this(values.map(::AnnotationDouble))
}
