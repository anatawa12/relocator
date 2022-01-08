package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.checkBits
import java.lang.AssertionError
import kotlin.experimental.and
import org.objectweb.asm.TypePath as ATP

class ClassTypeAnnotation(
    val type: TypeReference,
    val typePath: TypePath?,
    val annotation: ClassAnnotation,
) {
    override fun toString(): String = 
        if (typePath != null) "$type $typePath $annotation" else "$type $annotation"
}

data class AnnotationLocalVariable(val begin: CodeLabel, val end: CodeLabel, val index: Int)

class ClassLocalVariableAnnotation(
    val type: TypeReference,
    val typePath: TypePath?,
    val ranges: List<AnnotationLocalVariable>,
    val annotation: ClassAnnotation,
)

class TypePath private constructor(private val path: ShortArray) {
    override fun toString(): String = buildString {
        for (sh in path) {
            when (sh.toInt() and 0xFF00) {
                ARRAY_ELEMENT -> append('[')
                INNER_TYPE -> append('.')
                WILDCARD_BOUND -> append('*')
                TYPE_ARGUMENT -> append(sh and 0xFF).append(';')
                else -> throw AssertionError()
            }
        }
    }

    class Builder(len: Int) {
        constructor(): this(64)
        private var path = ShortArray(len)
        private var len = 0

        fun array() = append(ARRAY_ELEMENT)
        fun inner() = append(INNER_TYPE)
        fun wildcard() = append(WILDCARD_BOUND)
        fun argument(typeArg: Int) = append(TYPE_ARGUMENT or typeArg.checkBits(8, "typeArg"))
        fun build() = TypePath(if (this.path.size == len) this.path else this.path.copyOf(len))

        private fun append(value: Int): Builder {
            if (len == path.size) {
                path = if (path.isEmpty()) ShortArray(1) else path.copyOf(path.size * 2)
            }
            path[len++] = value.toShort()
            return this
        }
    }
}

private const val ARRAY_ELEMENT = ATP.ARRAY_ELEMENT shl 8
private const val INNER_TYPE = ATP.INNER_TYPE shl 8
private const val WILDCARD_BOUND = ATP.WILDCARD_BOUND shl 8
private const val TYPE_ARGUMENT = ATP.TYPE_ARGUMENT shl 8
