package com.anatawa12.relocator.internal

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InnerClassNode

internal sealed class Reference {
    companion object Utils {
        @JvmStatic
        fun fromType(type: Type): ClassReference? {
            return when (type.sort) {
                Type.VOID -> null
                Type.BOOLEAN -> null
                Type.CHAR -> null
                Type.BYTE -> null
                Type.SHORT -> null
                Type.INT -> null
                Type.FLOAT -> null
                Type.LONG -> null
                Type.DOUBLE -> null
                Type.ARRAY -> fromType(type.elementType)
                Type.OBJECT -> ClassReference(type.internalName)
                Type.METHOD -> throw IllegalArgumentException("The type is not type, a METHOD.")
                else -> throw IllegalArgumentException("Unknown sort of type: ${type.sort}")
            }
        }

        @JvmStatic
        fun fromInternalName(internalName: String): ClassReference? =
            fromType(Type.getObjectType(internalName))

        @JvmStatic
        fun fromDescriptor(descriptor: String): ClassReference? =
            fromType(Type.getType(descriptor))

        fun fromHandle(handle: Handle) = when (handle.tag) {
            H_GETFIELD,
            H_PUTFIELD,
            H_GETSTATIC,
            H_PUTSTATIC,
            -> FieldReference(handle.owner, handle.name, handle.desc)
            H_INVOKEVIRTUAL,
            H_INVOKESTATIC,
            H_INVOKESPECIAL,
            H_NEWINVOKESPECIAL,
            H_INVOKEINTERFACE,
            -> MethodReference(handle.owner, handle.name, handle.desc)
            else -> error("unknown handle tag: ${handle.tag}")
        }
    }
}

internal class InnerClassContainer(
    val owner: String,
) {
    constructor(
        owner: String,
        innerClasses: List<InnerClassNode>,
    ) : this(owner) {
        innerClasses.forEach(::add)
    }

    private val innerClasses: MutableList<InnerClassNode> = arrayListOf()
    private val byName: MutableMap<Pair<String, String>, InnerClassNode> = hashMapOf()

    private fun add(node: InnerClassNode) {
        innerClasses.add(node)
        if (node.outerName != null && node.innerName != null)
            byName[node.outerName to node.innerName] = node
    }

    fun findInner(classType: String, name: String): String? {
        return byName[classType to name]?.name
    }
}

internal data class ClassReference(
    /**
     * The internal name of the class.
     */
    val name: String,
): Reference()

internal data class MethodReference(
    /**
     * The internal name of owner of class.
     */
    val owner: String,
    /**
     * The name of the method.
     */
    val name: String,
    /**
     * The descriptor of the method.
     */
    val descriptor: String,
): Reference() {
}

internal data class FieldReference(
    /**
     * The internal name of owner of class.
     */
    val owner: String,
    /**
     * The name of the method.
     */
    val name: String,
    /**
     * The descriptor of the method.
     */
    val descriptor: String,
): Reference()
