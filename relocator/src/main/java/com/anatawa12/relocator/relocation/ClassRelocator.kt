package com.anatawa12.relocator.relocation

import com.anatawa12.relocator.classes.*

abstract class ClassRelocator {
    open fun relocate(classFile: ClassFile) = Unit
    open fun relocate(method: ClassMethod) = Unit
    open fun relocate(field: ClassField) = Unit
    open fun relocate(recordField: ClassRecordField) = Unit
    open fun relocate(annotation: ClassAnnotation, visible: Boolean, location: AnnotationLocation) = Unit

    open fun relocate(annotation: ClassTypeAnnotation, visible: Boolean, location: TypeAnnotationLocation) {
        relocate(annotation.annotation, visible, AnnotationLocation.TypeAnnotation(annotation, location))
    }

    open fun relocate(annotation: ClassLocalVariableAnnotation, visible: Boolean, location: ClassCode) {
        relocate(annotation.annotation, visible, AnnotationLocation.LocalVariable(annotation, location))
    }
}

fun interface ClassRelocatorProvider {
    fun provide(mapping: RelocationMapping): ClassRelocator
}
