package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.ClassInnerClass
import com.anatawa12.relocator.reference.ClassReference

internal class InnerClassContainer(
    innerClasses: List<ClassInnerClass>,
) {
    private val byName: MutableMap<Pair<ClassReference, String>, ClassInnerClass> = hashMapOf()

    init {
        innerClasses.forEach(::add)
    }

    private fun add(node: ClassInnerClass) {
        val outerName = node.outerName
        val innerName = node.innerName
        if (outerName != null && innerName != null)
            byName[outerName to innerName] = node
    }

    fun findInner(classType: ClassReference, name: String): ClassReference? {
        return byName[classType to name]?.name
    }
}
