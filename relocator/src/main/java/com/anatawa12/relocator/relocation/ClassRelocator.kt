package com.anatawa12.relocator.relocation

import com.anatawa12.relocator.classes.ClassField
import com.anatawa12.relocator.classes.ClassFile
import com.anatawa12.relocator.classes.ClassMethod
import com.anatawa12.relocator.classes.ClassRecordField

interface ClassRelocator {
    fun relocate(classFile: ClassFile)
    fun relocate(method: ClassMethod)
    fun relocate(field: ClassField)
    fun relocate(recordField: ClassRecordField)
}

fun interface ClassRelocatorProvider {
    fun provide(mapping: RelocationMapping): ClassRelocator
}
