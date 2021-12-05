package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.OwnerAccessor
import com.anatawa12.relocator.internal.ownerAccessorLocalVariable
import com.anatawa12.relocator.reference.ClassReference
import kotlinx.atomicfu.atomic

class ClassParameter(
    var name: String,
    var access: Int,
)

class TryCatchBlock(
    val start: CodeLabel,
    val end: CodeLabel,
    val handler: CodeLabel,
    val type: ClassReference?,
) {
    val visibleAnnotations: MutableList<ClassTypeAnnotation> = ArrayList(0)
    val invisibleAnnotations: MutableList<ClassTypeAnnotation> = ArrayList(0)
}

class LocalVariable(
    val name: String,
    val descriptor: String,
    val signature: String?,
    val start: CodeLabel,
    val end: CodeLabel,
    val index: Int,
) {
    private val owner = atomic<ClassCode?>(null)

    init {
        ownerAccessorLocalVariable = Accessor
    }

    private object Accessor : OwnerAccessor<LocalVariable, ClassCode>() {
        override fun trySet(element: LocalVariable, target: ClassCode): Boolean =
            element.owner.compareAndSet(null, target)

        override fun check(element: LocalVariable, target: ClassCode): Boolean =
            element.owner.value === target

        override fun clear(element: LocalVariable) {
            element.owner.value = null
        }

        override fun get(element: LocalVariable): ClassCode =
            element.owner.value ?: error("owner of this variable not found")
    }
}
