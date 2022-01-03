package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.assertError
import java.util.*

abstract class ConstantMapper<A> {
    // TODO: rewrite when with compiler plugin
    fun mapConstant(attachment: A, value: Constant): Constant = when (value::class) {
        ConstantDouble::class -> mapConstantDouble(attachment, value as ConstantDouble)
        ConstantFloat::class -> mapConstantFloat(attachment, value as ConstantFloat)
        ConstantInt::class -> mapConstantInt(attachment, value as ConstantInt)
        ConstantLong::class -> mapConstantLong(attachment, value as ConstantLong)
        ConstantString::class -> mapConstantString(attachment, value as ConstantString)
        ConstantClass::class -> mapConstantClass(attachment, value as ConstantClass)
        ConstantDynamic::class -> mapConstantDynamic(attachment, value as ConstantDynamic)
        ConstantMethodType::class -> mapConstantMethodType(attachment, value as ConstantMethodType)
        ConstantFieldHandle::class -> mapConstantHandle(attachment, value as ConstantFieldHandle)
        ConstantMethodHandle::class -> mapConstantHandle(attachment, value as ConstantMethodHandle)
        else -> assertError("unknown constant type: ${value::class}")
    }

    open fun mapConstantDouble(attachment: A, value: ConstantDouble): ConstantDouble = value
    open fun mapConstantFloat(attachment: A, value: ConstantFloat): ConstantFloat = value
    open fun mapConstantInt(attachment: A, value: ConstantInt): ConstantInt = value
    open fun mapConstantLong(attachment: A, value: ConstantLong): ConstantLong = value
    open fun mapConstantString(attachment: A, value: ConstantString): ConstantString = value
    open fun mapConstantClass(attachment: A, value: ConstantClass): ConstantClass = value

    open fun mapConstantDynamic(attachment: A, value: ConstantDynamic): ConstantDynamic {
        val mappedDesc = mapMethodDescriptor(attachment, value.descriptor)
        val mappedHandle = mapConstantHandle(attachment, value.bootstrapMethod)
        val mappedArgs = mapList(value.args) { mapConstant(attachment, it) }
        if (mappedDesc === value.descriptor && mappedHandle === value.bootstrapMethod && mappedArgs === value.args)
            return value
        return ConstantDynamic(value.name, mappedDesc, mappedHandle, mappedArgs)
    }

    open fun mapConstantMethodType(attachment: A, value: ConstantMethodType): ConstantMethodType {
        val mapped = mapMethodDescriptor(attachment, value.descriptor)
        return if (mapped === value.descriptor) value else ConstantMethodType(mapped)
    }

    // TODO: rewrite when with compiler plugin
    open fun mapConstantHandle(attachment: A, value: ConstantHandle): ConstantHandle = when (value::class) {
        ConstantFieldHandle::class -> mapConstantFieldHandle(attachment, value as ConstantFieldHandle)
        ConstantMethodHandle::class -> mapConstantMethodHandle(attachment, value as ConstantMethodHandle)
        else -> assertError("unknown constant type: ${value::class}")
    } 

    open fun mapConstantFieldHandle(attachment: A, value: ConstantFieldHandle): ConstantFieldHandle = value
    open fun mapConstantMethodHandle(attachment: A, value: ConstantMethodHandle): ConstantMethodHandle = value
    open fun mapMethodDescriptor(attachment: A, descriptor: MethodDescriptor): MethodDescriptor = descriptor
}

abstract class AnnotationWalker<A> {
    fun walkAnnotationValue(attachment: A, value: AnnotationValue): Unit = when (value) {
        is AnnotationArray -> walkAnnotationArray(attachment, value)
        is AnnotationBoolean -> walkAnnotationBoolean(attachment, value)
        is AnnotationByte -> walkAnnotationByte(attachment, value)
        is AnnotationChar -> walkAnnotationChar(attachment, value)
        is AnnotationDouble -> walkAnnotationDouble(attachment, value)
        is AnnotationFloat -> walkAnnotationFloat(attachment, value)
        is AnnotationInt -> walkAnnotationInt(attachment, value)
        is AnnotationLong -> walkAnnotationLong(attachment, value)
        is AnnotationShort -> walkAnnotationShort(attachment, value)
        is AnnotationString -> walkAnnotationString(attachment, value)
        is AnnotationClass -> walkAnnotationClass(attachment, value)
        is AnnotationEnum -> walkAnnotationEnum(attachment, value)
        is ClassAnnotation -> walkClassAnnotation(attachment, value)
    }

    open fun walkAnnotationArray(attachment: A, value: AnnotationArray) =
        value.values.forEach { walkAnnotationValue(attachment, it) }

    open fun walkAnnotationBoolean(attachment: A, value: AnnotationBoolean) = Unit
    open fun walkAnnotationByte(attachment: A, value: AnnotationByte) = Unit
    open fun walkAnnotationChar(attachment: A, value: AnnotationChar) = Unit
    open fun walkAnnotationDouble(attachment: A, value: AnnotationDouble) = Unit
    open fun walkAnnotationFloat(attachment: A, value: AnnotationFloat) = Unit
    open fun walkAnnotationInt(attachment: A, value: AnnotationInt) = Unit
    open fun walkAnnotationLong(attachment: A, value: AnnotationLong) = Unit
    open fun walkAnnotationShort(attachment: A, value: AnnotationShort) = Unit
    open fun walkAnnotationString(attachment: A, value: AnnotationString) = Unit
    open fun walkAnnotationClass(attachment: A, value: AnnotationClass) = Unit
    open fun walkAnnotationEnum(attachment: A, value: AnnotationEnum) = Unit
    open fun walkClassAnnotation(attachment: A, annotation: ClassAnnotation) =
        annotation.values.forEach { walkAnnotationValue(attachment, it.value) }
}

private inline fun <S: Any> mapList(types: List<S>, map: (S) -> S): List<S> {
    val iterator = types.listIterator()
    while (iterator.hasNext()) {
        val type = iterator.next()
        val mapped = map(type)
        if (mapped === type) continue

        val list = ArrayList<S>(types.size)
        list.addAll(types.subList(0, iterator.previousIndex()))
        list.add(mapped)
        while (iterator.hasNext()) {
            val type1 = iterator.next()
            list.add(map(type1))
        }
        list.trimToSize()
        return Collections.unmodifiableList(list)
    }
    return types
}
