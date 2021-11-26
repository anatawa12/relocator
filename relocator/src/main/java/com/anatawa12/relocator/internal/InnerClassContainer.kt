package com.anatawa12.relocator.internal

import org.objectweb.asm.tree.InnerClassNode

internal class InnerClassContainer(
    innerClasses: List<InnerClassNode>,
) {
    private val byName: MutableMap<Pair<String, String>, InnerClassNode> = hashMapOf()

    init {
        innerClasses.forEach(::add)
    }

    private fun add(node: InnerClassNode) {
        if (node.outerName != null && node.innerName != null)
            byName[node.outerName to node.innerName] = node
    }

    fun findInner(classType: String, name: String): String? {
        return byName[classType to name]?.name
    }
}
