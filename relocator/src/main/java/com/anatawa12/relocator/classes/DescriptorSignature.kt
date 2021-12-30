package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.TypeKind

abstract class AnyMethodDescriptor(val descriptor: String) {
    init {
        check(javaClass == MethodDescriptor::class.java || javaClass == PartialMethodDescriptor::class.java) {
            "it's not allowed to extend AnyMethodDescriptor"
        }
    }
}

class MethodDescriptor(descriptor: String) : AnyMethodDescriptor(descriptor) {
    constructor(returns: TypeDescriptor, vararg args: TypeDescriptor) :
            this(returns, args.asList())
    constructor(returns: TypeDescriptor, args: List<TypeDescriptor>) :
            this(args.joinToString(prefix = "(", postfix = ")${returns.descriptor}", separator = "") { it.descriptor })

    private val argIndices = DescriptorSignatures.parseMethodDesc(descriptor)
    private val _arguments = ArgsList()
    val arguments: List<TypeDescriptor> get() = _arguments
    val returns: TypeDescriptor get() {
        val indices = argIndices
        return TypeDescriptor(descriptor.substring(if (indices.isEmpty()) 2 else indices.last() + 1))
    }

    private inner class ArgsList() : AbstractList<TypeDescriptor>() {
        override val size: Int get() = this@MethodDescriptor.argIndices.size

        override fun get(index: Int): TypeDescriptor {
            val indices = this@MethodDescriptor.argIndices
            if (index !in indices.indices)
                throw IndexOutOfBoundsException("index: $index, count: ${indices.size}")
            return newTypeDescriptorInternal(descriptor.substring(if (index == 0) 1 else indices[index - 1], indices[index]))
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && descriptor == (other as MethodDescriptor).descriptor
    override fun hashCode(): Int = descriptor.hashCode()
    override fun toString(): String = descriptor
}

class PartialMethodDescriptor(descriptor: String) : AnyMethodDescriptor(descriptor) {
    constructor(vararg args: TypeDescriptor) : this(args.asList())
    constructor(args: List<TypeDescriptor>) : this(args.joinToString(prefix = "(", postfix = ")", separator = "") { it.descriptor })

    private val argIndices = DescriptorSignatures.parseMethodDesc(descriptor, false)
    private val _arguments = ArgsList()
    val arguments: List<TypeDescriptor> get() = _arguments

    private inner class ArgsList() : AbstractList<TypeDescriptor>() {
        override val size: Int get() = this@PartialMethodDescriptor.argIndices.size

        override fun get(index: Int): TypeDescriptor {
            val indices = this@PartialMethodDescriptor.argIndices
            if (index !in indices.indices)
                throw IndexOutOfBoundsException("index: $index, count: ${indices.size}")
            return newTypeDescriptorInternal(descriptor.substring(if (index == 0) 1 else indices[index - 1], indices[index]))
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && descriptor == (other as PartialMethodDescriptor).descriptor
    override fun hashCode(): Int = descriptor.hashCode()
    override fun toString(): String = descriptor
}

class TypeDescriptor {
    val descriptor: String
    // TODO: add ofClass and primitive constants with converting to java

    constructor(descriptor: String) {
        DescriptorSignatures.parseTypeDesc(descriptor, TypeKind.Voidable)
        this.descriptor = descriptor
        Init.init()
    }

    private constructor(descriptor: String, @Suppress("UNUSED_PARAMETER") internalMarker: Int) {
        this.descriptor = descriptor
    }

    val elementType: TypeDescriptor get() {
        check(descriptor[0] == '[') { "this type is not array type: $descriptor" }
        return TypeDescriptor(descriptor.substring(descriptor.indexOfFirst { it != '[' }), 0)
    }

    val internalName: String get() = when (descriptor[0]) {
        'L' -> descriptor.substring(1, descriptor.length - 1)
        '[' -> descriptor
        else -> error("primitive type doesn't have internal name: $descriptor")
    }

    private object Init {
        init {
            newTypeDescriptor = { TypeDescriptor(it, 0) }
        }

        @JvmStatic fun init() {}
    }

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && descriptor == (other as TypeDescriptor).descriptor
    override fun hashCode(): Int = descriptor.hashCode()
    override fun toString(): String = descriptor
}

// signatures are implemented in java because 
// missing support for static of kotlin

enum class TypeVariant {
    // ? extends SomeClass
    Covariant,
    // ? super SomeClass
    Contravariant,
    // non wildcard
    Invariant,
}
