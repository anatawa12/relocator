package com.anatawa12.relocator.internal

import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.RecordComponentNode

sealed class Diagnostic(val location: Location) {
    override fun toString(): String {
        val inClass = location
        return "${message()}: at $inClass"
    }
    abstract fun message(): String
}

abstract class Warning(location: Location) : Diagnostic(location)
abstract class Error(location: Location) : Diagnostic(location)

class UnresolvableInnerClass(val outer: String, val inner: String, location: Location) : Warning(location) {
    override fun message() = "the internal name of '$outer.$inner' not found."
}

class UnresolvableClassError(val name: String, location: Location) : Error(location) {
    override fun message() = "the class '$name' not found"
}

sealed class Location {
    abstract override fun toString(): String

    class Class(val name: String) : Location() {
        internal constructor(file: ClassFile) : this(file.name)
        override fun toString(): String = "class $name"
    }

    class RecordField(val owner: String, val name: String, val desc: String) : Location() {
        constructor(owner: String, record: RecordComponentNode) : this(owner, record.name, record.descriptor)
        override fun toString(): String = "record field $owner.$name:$desc"
    }

    class Method(val owner: String, val name: String, val desc: String) : Location() {
        constructor(owner: String, method: MethodNode) : this(owner, method.name, method.desc)
        override fun toString(): String = "method $owner.$name:$desc"
    }

    class MethodLocal(val owner: String, val mName: String, val desc: String, val num: Int, val name: String) : Location() {
        constructor(owner: String, method: MethodNode, variable: LocalVariableNode) : this(owner, method.name, method.desc, variable.index, variable.name)
        override fun toString(): String = "local variable $num($name) in method $owner.$mName:$desc"
    }

    class Field(val owner: String, val name: String, val desc: String) : Location() {
        constructor(owner: String, field: FieldNode) : this(owner, field.name, field.desc)
        override fun toString(): String = "field $owner.$name:$desc"
    }
}
