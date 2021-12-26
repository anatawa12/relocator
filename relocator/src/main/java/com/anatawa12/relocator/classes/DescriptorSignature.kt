package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.TypeKind
import com.anatawa12.relocator.internal.TypeSignatureIndices
//import com.anatawa12.relocator.internal.TypeSignatureList

// TODO: skip verification to create Type from Method/Class
// TODO: add ability to access elements of signature

class MethodDescriptor(val descriptor: String) {
    constructor(returns: TypeDescriptor, vararg args: TypeDescriptor) :
            this(returns, args.asList())
    constructor(returns: TypeDescriptor, args: List<TypeDescriptor>) :
            this(args.joinToString(prefix = "(", postfix = ")${returns.descriptor}", separator = "") { it.descriptor })

    private val _arguments = ArgsList(DescriptorParser.parseMethodDesc(descriptor))
    val arguments: List<TypeDescriptor> get() = _arguments
    val returns: TypeDescriptor get() {
        val indices = _arguments.argIndices
        return TypeDescriptor(descriptor.substring(if (indices.isEmpty()) 2 else indices.last() + 1))
    }

    private inner class ArgsList(val argIndices: IntArray) : AbstractList<TypeDescriptor>() {
        override val size: Int get() = argIndices.size

        override fun get(index: Int): TypeDescriptor {
            val indices = argIndices
            if (index !in indices.indices)
                throw IndexOutOfBoundsException("index: $index, count: ${indices.size}")
            return TypeDescriptor(descriptor.substring(if (index == 0) 1 else indices[index - 1], indices[index]))
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && descriptor == (other as MethodDescriptor).descriptor
    override fun hashCode(): Int = descriptor.hashCode()
    override fun toString(): String = descriptor
}

class TypeDescriptor(val descriptor: String) {
    init {
        DescriptorParser.parseTypeDesc(descriptor, TypeKind.Voidable)
    }

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && descriptor == (other as TypeDescriptor).descriptor
    override fun hashCode(): Int = descriptor.hashCode()
    override fun toString(): String = descriptor
}

/*
class TypeParameter private constructor(
    private val raw: String,
    private val range: IntRange,
    private val parsed: TypeParameterIndices,
) {
    val signature get() = raw.substring(range)

    constructor(signature: String) : this(signature,
        signature.indices,
        DescriptorParser.parseTypeParameter(signature)) {
        Init.init()
    }

    private object Init {
        init {
            newTypeParameter = ::TypeParameter
        }

        @JvmStatic
        fun init() {
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && signature == (other as TypeParameter).signature

    override fun hashCode(): Int = signature.hashCode()
    override fun toString(): String = signature
}
*/

class ClassSignature(val signature: String) {
    private val parsed = DescriptorParser.parseClassSignature(signature)

    /*
    val parameters: List<TypeParameter> = TypeParameterList(signature, parsed.params)
    val superClass: TypeSignature get() = TypeSignature(signature.substring(parsed.superClass.getTypeRange(signature)!!))
    val interfaces: List<TypeSignature> = TypeSignatureList(signature, parsed.interfaces)
    */

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && signature == (other as ClassSignature).signature
    override fun hashCode(): Int = signature.hashCode()
    override fun toString(): String = signature
}

class MethodSignature(val signature: String) {
    private val parsed = DescriptorParser.parseMethodSignature(signature)

    /*
    val parameters: List<TypeParameter> = TypeParameterList(signature, parsed.params)
    val arguments: List<TypeSignature> = TypeSignatureList(signature, parsed.args)
    val returns: TypeSignature get() = TypeSignature(signature.substring(parsed.returns.getTypeRange(signature)!!))
    val throws: List<TypeSignature> = TypeSignatureList(signature, parsed.throws)
    */

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && signature == (other as MethodSignature).signature
    override fun hashCode(): Int = signature.hashCode()
    override fun toString(): String = signature
}

class TypeSignature private constructor(
    val signature: String,
    private val parsed: TypeSignatureIndices,
) {
    constructor(signature: String) : this(signature, DescriptorParser.parseTypeSignature(signature, TypeKind.Voidable))

    override fun equals(other: Any?): Boolean =
        this === other || javaClass == other?.javaClass && signature == (other as TypeSignature).signature
    override fun hashCode(): Int = signature.hashCode()
    override fun toString(): String = signature
}
