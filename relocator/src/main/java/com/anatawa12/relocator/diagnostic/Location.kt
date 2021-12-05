package com.anatawa12.relocator.diagnostic

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.internal.owner

sealed class Location {
    abstract override fun toString(): String

    object None : Location() {
        override fun toString(): String = ""
    }

    class Class(val name: String) : Location() {
        internal constructor(file: ClassFile) : this(file.name)
        override fun toString(): String = "at class $name"
    }

    class RecordField(val owner: String, val name: String, val descriptor: String) : Location() {
        constructor(record: ClassRecordField) : this(record.owner.name, record.name, record.descriptor)
        override fun toString(): String = "at record field $owner.$name:$descriptor"
    }

    class Method(val owner: String, val name: String, val descriptor: String) : Location() {
        constructor(method: ClassMethod) : this(method.owner.name, method.name, method.descriptor)
        override fun toString(): String = "at method $owner.$name:$descriptor"
    }

    class MethodLocal(val owner: String, val mName: String, val descriptor: String, val num: Int, val name: String) : Location() {
        constructor(variable: LocalVariable) : this(variable.owner.owner, variable.index, variable.name)
        constructor(method: ClassMethod, num: Int, name: String)
                : this(method.name, method.name, method.descriptor, num, name)
        override fun toString(): String = "at local variable $num($name) in method $owner.$mName:$descriptor"
    }

    class Field(val owner: String, val name: String, val descriptor: String) : Location() {
        constructor(field: ClassField) : this(field.owner.name, field.name, field.descriptor)
        override fun toString(): String = "at field $owner.$name:$descriptor"
    }
}
