package com.anatawa12.relocator.reference

import com.anatawa12.relocator.diagnostic.Location
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type

sealed class Reference {
    var location: Location? = null
        internal set

    companion object Utils {
        @JvmStatic
        internal fun fromType(type: Type): ClassReference? {
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
        internal fun fromInternalName(internalName: String): ClassReference? =
            fromType(Type.getObjectType(internalName))

        @JvmStatic
        internal fun fromDescriptor(descriptor: String): ClassReference? =
            fromType(Type.getType(descriptor))

        internal fun fromHandle(handle: Handle) = when (handle.tag) {
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

internal fun <R: Reference> R.withLocation(location: Location) = apply { this.location = location }

/**
 * @param name The name of the class. This must be either binary name or internal form of binary class name.
 */
class ClassReference(
    name: String,
): Reference() {
    /**
     * The internal form of binary class name.
     */
    val name: String = name.replace('.', '/')

    override fun toString(): String = "class $name"

    override fun equals(other: Any?): Boolean = this === other
                || other is ClassReference
                && name == other.name

    override fun hashCode(): Int = 0
        .times(31).plus(name.hashCode())
}

/**
 * @param name The name of the class. This must be either binary name or internal form of binary class name.
 */
class MethodReference(
    /**
     * The internal name of owner of class.
     */
    owner: String,
    /**
     * The name of the method.
     */
    val name: String,
    /**
     * The descriptor of the method.
     * This may not have return type information.
     * It's for reflection.
     */
    val descriptor: String,
): Reference() {
    /**
     * The internal name of owner of class.
     */
    val owner: String = owner.replace('.', '/')

    override fun toString(): String = "method $owner.$name:$descriptor"

    override fun equals(other: Any?): Boolean = this === other
            || other is MethodReference
            && owner == other.owner
            && name == other.name
            && descriptor == other.descriptor

    override fun hashCode(): Int = 0
        .times(31).plus(owner.hashCode())
        .times(31).plus(name.hashCode())
        .times(31).plus(descriptor.hashCode())
}

/**
 * @param name The name of the class. This must be either binary name or internal form of binary class name.
 */
class FieldReference(
    owner: String,
    /**
     * The name of the method.
     */
    val name: String,
    /**
     * The descriptor of the method.
     * This is null if unknown.
     * It's for reflection.
     */
    val descriptor: String?,
): Reference() {
    /**
     * The internal name of owner of class.
     */
    val owner: String = owner.replace('.', '/')

    override fun toString(): String = "field $owner.$name:$descriptor"

    override fun equals(other: Any?): Boolean = this === other
            || other is MethodReference
            && owner == other.owner
            && name == other.name
            && descriptor == other.descriptor

    override fun hashCode(): Int = 0
        .times(31).plus(owner.hashCode())
        .times(31).plus(name.hashCode())
        .times(31).plus(descriptor.hashCode())
}
