package com.anatawa12.relocator.internal.plugins.exclude

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.internal.ConcurrentIdentityHashMap
import com.anatawa12.relocator.plugin.*
import java.util.*

class ExcludeClassRelocatorPlugin : ClassRelocatorPlugin, ExcludePlugin {
    override fun getName(): String = "exclude"

    override fun apply(context: ClassRelocatorPluginContext) {
        context.addClassRelocator(ClassRelocatorStep.PreFiltering, ClassRelocatorImpl())
    }

    val excludeAnnotations = Collections.newSetFromMap(ConcurrentIdentityHashMap())

    override fun exclude(value: ClassMethod) {
        excludeAnnotations += value
    }

    override fun exclude(value: ClassField) {
        excludeAnnotations += value
    }

    override fun exclude(value: ClassRecordField) {
        excludeAnnotations += value
    }

    override fun exclude(value: ClassAnnotation) {
        excludeAnnotations += value
    }

    override fun exclude(value: ClassTypeAnnotation) {
        excludeAnnotations += value
    }

    override fun exclude(value: ClassLocalVariableAnnotation) {
        excludeAnnotations += value
    }

    inner class ClassRelocatorImpl : ClassRelocator() {
        override fun relocate(classFile: ClassFile) = filter(classFile)
        override fun relocate(method: ClassMethod) = filter(method)
        override fun relocate(field: ClassField) = filter(field)
        override fun relocate(recordField: ClassRecordField) = filter(recordField)
        override fun relocate(annotation: ClassAnnotation, visible: Boolean, location: AnnotationLocation) =
            filter(annotation)

        override fun relocate(annotation: ClassTypeAnnotation, visible: Boolean, location: TypeAnnotationLocation) =
            filter(annotation)

        override fun relocate(annotation: ClassLocalVariableAnnotation, visible: Boolean, location: ClassCode) =
            filter(annotation)

        private fun filter(value: Any) =
            if (value in excludeAnnotations) RelocateResult.Finish else RelocateResult.Continue
    }
}
