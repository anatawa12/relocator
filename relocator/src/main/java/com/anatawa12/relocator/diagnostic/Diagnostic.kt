package com.anatawa12.relocator.diagnostic

import com.anatawa12.relocator.classes.ClassFile
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.RecordComponentNode

class Diagnostic internal constructor(
    val type: BasicDiagnosticType,
    val location: Location,
    val parameters: Array<Any?>,
) {
    override fun toString(): String {
        val inClass = location
        return "${message()} $inClass"
    }

    fun message(): String = type.render(parameters)
}

enum class DiagnosticKind {
    Warning,
    Error,
}

sealed class DiagnosticValueType<T> {
    object String : DiagnosticValueType<kotlin.String>()
    object Int : DiagnosticValueType<kotlin.Int>()

    fun optional(): Optional<T> = Optional(this)

    class Optional<T> internal constructor(val inner: DiagnosticValueType<T>) : DiagnosticValueType<T?>() {
        override fun equals(other: Any?): Boolean {
            if (other === null) return false
            if (this === other) return true
            if (other is Optional<*> && mostInner() == other.mostInner())
                return true
            return false
        }

        override fun hashCode(): kotlin.Int = mostInner().hashCode() + 1

        private fun mostInner(): DiagnosticValueType<*> {
            var cur = this as DiagnosticValueType<*>
            while (cur is Optional<*>) cur = cur.inner
            return cur
        }
    }
}

/*
// ABI for Kotlin. should be separated module
class DiagnosticBuilder<T> internal constructor(
    internal val kind: DiagnosticKind,
    internal val render: Any?,
    internal val factory: (String, DiagnosticBuilder<T>) -> T,
) {
    internal fun create(name: String) = factory(name, this)
}
 */

object BasicDiagnostics : DiagnosticContainer() {
    val UNRESOLVABLE_INNER_CLASS = warning("UNRESOLVABLE_INNER_CLASS", String, String) { outer, inner ->
        "the internal name of '$outer.$inner' not found."
    }
    val UNRESOLVABLE_REFLECTION_CLASS = warning("UNRESOLVABLE_REFLECTION_CLASS", "Unresolvable reflection call for class found.")
    val UNRESOLVABLE_REFLECTION_FIELD = warning("UNRESOLVABLE_REFLECTION_FIELD", "Unresolvable reflection call for field found.")
    val UNRESOLVABLE_REFLECTION_METHOD = warning("UNRESOLVABLE_REFLECTION_METHOD", "Unresolvable reflection call for method found.")

    val UNRESOLVABLE_CLASS = error("UNRESOLVABLE_CLASS", String) { name -> "the class '$name' not found" }
    val UNRESOLVABLE_FIELD = error("UNRESOLVABLE_FIELD", String, String, String.optional()) { owner, name, desc ->
        "the field '$owner.$name${if (desc == null) "" else ":$desc"}' not found"
    }
    val UNRESOLVABLE_METHOD = error("UNRESOLVABLE_METHOD", String, String, String.optional()) { owner, name, desc ->
        "the method '$owner.$name${if (desc == null) "" else ":$desc"}' not found"
    }
}

sealed class Location {
    abstract override fun toString(): String

    object None : Location() {
        override fun toString(): String = ""
    }

    class Class(val name: String) : Location() {
        internal constructor(file: ClassFile) : this(file.name)
        override fun toString(): String = "at class $name"
    }

    class RecordField(val owner: String, val name: String, val desc: String) : Location() {
        constructor(owner: String, record: RecordComponentNode) : this(owner, record.name, record.descriptor)
        override fun toString(): String = "at record field $owner.$name:$desc"
    }

    class Method(val owner: String, val name: String, val desc: String) : Location() {
        constructor(owner: String, method: MethodNode) : this(owner, method.name, method.desc)
        override fun toString(): String = "at method $owner.$name:$desc"
    }

    class MethodLocal(val owner: String, val mName: String, val desc: String, val num: Int, val name: String) : Location() {
        constructor(owner: String, method: MethodNode, variable: LocalVariableNode) : this(owner, method.name, method.desc, variable.index, variable.name)
        override fun toString(): String = "at local variable $num($name) in method $owner.$mName:$desc"
    }

    class Field(val owner: String, val name: String, val desc: String) : Location() {
        constructor(owner: String, field: FieldNode) : this(owner, field.name, field.desc)
        override fun toString(): String = "at field $owner.$name:$desc"
    }
}
