package com.anatawa12.relocator.reference

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.internal.newTypeDescriptorInternal
import com.anatawa12.relocator.internal.owner
import com.anatawa12.relocator.internal.RelocationMappingPrimitiveMarker

// TODO: replace ClassRefence in method/field to another InernalName class

sealed class Reference {
    var location: Location? = null
        internal set
}

internal fun <R: Reference> R.withLocation(location: Location?) = apply { if (location != null)this.location = location }

/**
 * @param name The name of the class. This must be either binary name or internal form of binary class name.
 */
class ClassReference(
    name: String,
): Reference(), RelocationMappingPrimitiveMarker {
    fun isArray() = name[0] == '['

    val arrayDimensions get() = name.indexOfFirst { it != '[' }

    val arrayComponentType: TypeDescriptor
        get() {
            check(isArray()) { "this is not array" }
            return newTypeDescriptorInternal(name.substring(arrayDimensions))
        }

    fun asTypeDescriptor(): TypeDescriptor = if (isArray()) TypeDescriptor(name) else TypeDescriptor("L$name;")

    /**
     * The internal form of binary class name.
     */
    val name: String = name.replace('.', '/')

    override fun toString(): String = name

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
     * The owner of class.
     */
    val owner: ClassReference,
    /**
     * The name of the method.
     */
    val name: String,
    /**
     * The descriptor of the method.
     */
    val descriptor: MethodDescriptor,
): Reference(), RelocationMappingPrimitiveMarker {
    constructor(owner: String, name: String, descriptor: MethodDescriptor) :
            this(ClassReference(owner), name, descriptor)
    constructor(owner: ClassReference, name: String, descriptor: String) :
            this(owner, name, MethodDescriptor(descriptor))
    constructor(owner: String, name: String, descriptor: String) :
            this(ClassReference(owner), name, MethodDescriptor(descriptor))

    constructor(method: ClassMethod): this(method.owner.name, method.name, method.descriptor)

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
class PartialMethodReference(
    /**
     * The owner of class.
     */
    val owner: ClassReference,
    /**
     * The name of the method.
     */
    val name: String,
    /**
     * The descriptor of the method without return type
     */
    val descriptor: PartialMethodDescriptor,
): Reference() {
    constructor(owner: String, name: String, descriptor: PartialMethodDescriptor) :
            this(ClassReference(owner), name, descriptor)
    constructor(owner: String, name: String, descriptor: String) :
            this(ClassReference(owner), name, PartialMethodDescriptor(descriptor))

    override fun toString(): String = "methods $owner.$name:$descriptor"

    override fun equals(other: Any?): Boolean = this === other
            || other is PartialMethodReference
            && owner == other.owner
            && name == other.name
            && descriptor == other.descriptor

    override fun hashCode(): Int = 0
        .times(31).plus(owner.hashCode())
        .times(31).plus(name.hashCode())
        .times(31).plus(descriptor.hashCode())
}

class TypelessMethodReference(
    /**
     * The owner of class.
     */
    val owner: ClassReference,
    /**
     * The name of the method.
     */
    val name: String,
): Reference() {
    constructor(owner: String, name: String) :
            this(ClassReference(owner), name)

    constructor(method: ClassMethod): this(method.owner.name, method.name)

    override fun toString(): String = "methods $owner.$name"

    override fun equals(other: Any?): Boolean = this === other
            || other is TypelessMethodReference
            && owner == other.owner
            && name == other.name

    override fun hashCode(): Int = 0
        .times(31).plus(owner.hashCode())
        .times(31).plus(name.hashCode())
}

/**
 * @param name The name of the class. This must be either binary name or internal form of binary class name.
 */
class FieldReference(
    /**
     * The owner of class.
     */
    val owner: ClassReference,
    /**
     * The name of the field.
     */
    val name: String,
    /**
     * The descriptor of the field.
     */
    val descriptor: TypeDescriptor,
): Reference(), RelocationMappingPrimitiveMarker {
    constructor(owner: String, name: String, descriptor: TypeDescriptor) :
            this(ClassReference(owner), name, descriptor)
    constructor(owner: String, name: String, descriptor: String) :
            this(ClassReference(owner), name, TypeDescriptor(descriptor))

    constructor(method: ClassField): this(method.owner.name, method.name, method.descriptor)

    override fun toString(): String = "field $owner.$name:$descriptor"

    override fun equals(other: Any?): Boolean = this === other
            || other is FieldReference
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
class PartialFieldReference(
    /**
     * The owner of class.
     */
    val owner: ClassReference,
    /**
     * The name of the method.
     */
    val name: String,
): Reference() {
    constructor(owner: String, name: String) :
            this(ClassReference(owner), name)

    override fun toString(): String = "fields $owner.$name"

    override fun equals(other: Any?): Boolean = this === other
            || other is PartialFieldReference
            && owner == other.owner
            && name == other.name

    override fun hashCode(): Int = 0
        .times(31).plus(owner.hashCode())
        .times(31).plus(name.hashCode())
}

/**
 * @param name The name of the class. This must be either binary name or internal form of binary class name.
 */
class RecordFieldReference(
    /**
     * The owner of the component.
     */
    val owner: ClassReference,
    /**
     * The name of the component.
     */
    val name: String,
    /**
     * The descriptor of the record component.
     */
    val descriptor: TypeDescriptor,
): Reference() {
    constructor(owner: String, name: String, descriptor: TypeDescriptor) :
            this(ClassReference(owner), name, descriptor)

    override fun toString(): String = "field $owner.$name:$descriptor"

    override fun equals(other: Any?): Boolean = this === other
            || other is RecordFieldReference
            && owner == other.owner
            && name == other.name
            && descriptor == other.descriptor

    override fun hashCode(): Int = 0
        .times(31).plus(owner.hashCode())
        .times(31).plus(name.hashCode())
        .times(31).plus(descriptor.hashCode())
}
