package com.anatawa12.relocator.relocation

import com.anatawa12.relocator.classes.*

sealed class AnnotationLocation {
    class Class(val classFile: ClassFile) : AnnotationLocation()
    class Method(val method: ClassMethod) : AnnotationLocation()
    class Field(val field: ClassField) : AnnotationLocation()
    class RecordField(val recordField: ClassRecordField) : AnnotationLocation()
    class Parameter(val method: ClassMethod, val index: Int) : AnnotationLocation()

    class TypeAnnotation(val original: ClassTypeAnnotation, val location: TypeAnnotationLocation) : AnnotationLocation()
    class LocalVariable(val original: ClassLocalVariableAnnotation, val code: ClassCode) : AnnotationLocation()
}

sealed class TypeAnnotationLocation {
    class Class(val classFile: ClassFile): TypeAnnotationLocation()
    class Method(val method: ClassMethod): TypeAnnotationLocation()
    class Field(val field: ClassField): TypeAnnotationLocation()
    class RecordField(val recordField: ClassRecordField): TypeAnnotationLocation()

    class Insn(val insn: com.anatawa12.relocator.classes.Insn, val code: ClassCode) : TypeAnnotationLocation()
    class TryCatchBlock(val block: com.anatawa12.relocator.classes.TryCatchBlock, val code: ClassCode) : TypeAnnotationLocation()
}
