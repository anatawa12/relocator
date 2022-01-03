package com.anatawa12.relocator.relocation

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.relocation.RelocateResult.*

abstract class ClassRelocator {
    open fun relocate(classFile: ClassFile) = Continue
    open fun relocate(method: ClassMethod) = Continue
    open fun relocate(field: ClassField) = Continue
    open fun relocate(recordField: ClassRecordField) = Continue
    open fun relocate(annotation: ClassAnnotation, visible: Boolean, location: AnnotationLocation) = Continue

    open fun relocate(
        annotation: ClassTypeAnnotation, 
        visible: Boolean, 
        location: TypeAnnotationLocation,
    ): RelocateResult {
        return relocate(annotation.annotation, visible, AnnotationLocation.TypeAnnotation(annotation, location))
    }

    open fun relocate(annotation: ClassLocalVariableAnnotation, visible: Boolean, location: ClassCode): RelocateResult {
        return relocate(annotation.annotation, visible, AnnotationLocation.LocalVariable(annotation, location))
    }
}

fun interface ClassRelocatorProvider {
    fun provide(mapping: RelocationMapping): ClassRelocator
}

enum class RelocateResult {
    Continue,
    Finish,
    Remove
}
