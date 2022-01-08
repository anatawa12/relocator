package com.anatawa12.relocator.plugin

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.plugin.RelocateResult.*

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

enum class RelocateResult {
    Continue,
    Finish,
    Remove
}

enum class ClassRelocatorStep {
    PreFiltering,
    LanguageProcessing,
    Finalizing,
}
