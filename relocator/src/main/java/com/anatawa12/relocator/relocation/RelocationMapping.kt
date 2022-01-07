package com.anatawa12.relocator.relocation

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.newClassSignatureInternal
import com.anatawa12.relocator.internal.newMethodSignatureInternal
import com.anatawa12.relocator.internal.newTypeDescriptorInternal
import com.anatawa12.relocator.internal.newTypeParameterInternal
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference
import java.util.*

class RelocationMapping(private val relocationMap: Map<String, String?>) {
    fun mapClass(name: String): String? {
        return mapFilePath(name)
    }

    fun mapFilePath(name: String): String? {
        for ((from, mapTo) in relocationMap) {
            if (name == from) return mapTo
            if (name.startsWith(from) && from.getOrElse(from.length) { '\u0000' } == '/')
                return mapTo + name.substring(from.length)
        }
        return null
    }

    private val excludedInstances = Collections.newSetFromMap<RelocationMappingPrimitiveMarker>(ConcurrentIdentityHashMap())

    /**
     * Exclude the instance from mapping the instance (identity based).
     * If this class doesn't support relocating the instance (primitively), 
     * this function will throw [IllegalArgumentException].
     */
    fun excludeMapping(instance: Any?) {
        require(instance is RelocationMappingPrimitiveMarker) { "the instance is not RelocationMapping-primitive value" }
        excludedInstances.add(instance)
    }

    fun mapClassSignature(signature: ClassSignature): ClassSignature? {
        if (signature in excludedInstances) return null
        val mappedTypes = mapList(signature.typeParameters, ::mapTypeParameter)
        val mappedClass = mapTypeSignature(signature.superClass)
        val mappedInterfaces = mapList(signature.superInterfaces, ::mapTypeSignature)
        if (mappedTypes == null && mappedClass == null && mappedInterfaces == null)
            return null
        return newClassSignatureInternal(
            typeParameters = mappedTypes ?: signature.typeParameters,
            superClass = mappedClass ?: signature.superClass,
            superInterfaces = mappedInterfaces ?: signature.superInterfaces,
            signature = null,
        )
    }

    fun mapClassRef(reference: ClassReference): ClassReference? {
        if (reference in excludedInstances) return null
        if (reference.isArray()) {
            return mapTypeDescriptor(reference.arrayComponentType)?.array(reference.arrayDimensions)?.tryAsClassReference()
        } else {
            mapClass(reference.name).takeIf { it != reference.name }?.let { mapped ->
                return ClassReference(mapped)
            }
        }
        return null
    }

    fun mapMethodSignature(signature: MethodSignature): MethodSignature? {
        if (signature in excludedInstances) return null
        val mappedTypes = mapList(signature.typeParameters, ::mapTypeParameter)
        val mappedValues = mapList(signature.valueParameters, ::mapTypeSignature)
        val mappedReturns = mapTypeSignature(signature.returns)
        val mappedThrows = mapList(signature.throwsTypes, ::mapTypeSignature)
        if (mappedTypes == null && mappedValues == null && mappedReturns == null && mappedThrows == null)
            return null
        return newMethodSignatureInternal(
            typeParameters = mappedTypes ?: signature.typeParameters,
            valueParameters = mappedValues ?: signature.valueParameters,
            returns = mappedReturns ?: signature.returns,
            throwsTypes = mappedThrows ?: signature.throwsTypes,
            signature = null,
        )
    }

    fun mapMethodDescriptor(descriptor: MethodDescriptor): MethodDescriptor? {
        if (descriptor in excludedInstances) return null
        val mappedReturns = mapTypeDescriptor(descriptor.returns)
        val mappedArguments = mapList(descriptor.arguments, ::mapTypeDescriptor)
        if (mappedReturns == null && mappedArguments == null) return null
        return MethodDescriptor(mappedReturns ?: descriptor.returns, mappedArguments ?: descriptor.arguments)
    }

    fun mapMethodRef(reference: MethodReference): MethodReference? {
        if (reference in excludedInstances) return null
        val mappedOwner = mapClassRef(reference.owner)
        val mappedDescriptor = mapMethodDescriptor(reference.descriptor)
        if (mappedOwner == null && mappedDescriptor == null) return null
        return MethodReference(mappedOwner ?: reference.owner, reference.name, mappedDescriptor ?: reference.descriptor)
    }

    fun mapTypeParameter(typeParameter: TypeParameter): TypeParameter? {
        if (typeParameter in excludedInstances) return null
        val mappedClass = typeParameter.classBound?.let(::mapTypeSignature)
        val mappedInterfaces = mapList(typeParameter.interfaceBounds, ::mapTypeSignature)
        if (mappedClass == null && mappedInterfaces == null) return null
        return newTypeParameterInternal(
            name = typeParameter.name,
            classBound = (mappedClass ?: typeParameter.classBound),
            interfaceBounds = mappedInterfaces ?: typeParameter.interfaceBounds,
        )
    }

    fun mapTypeSignature(signature: TypeSignature): TypeSignature? {
        if (signature in excludedInstances) return null
        return when (signature.kind) {
            TypeSignature.Kind.Array -> mapTypeSignature(signature.arrayComponent)?.array(signature.arrayDimensions)
            TypeSignature.Kind.Primitive -> null
            TypeSignature.Kind.TypeArgument -> null
            TypeSignature.Kind.Class -> {
                TypeSignature.ClassBuilder(mapClass(signature.rootClassName) ?: signature.rootClassName).run {
                    signature.getTypeArguments(0).forEach { addTypeArgument(mapTypeArgument(it) ?: it) }
                    for (i in 1..signature.innerClassCount) {
                        innerClassName(signature.getInnerClassName(i))
                        signature.getTypeArguments(i).forEach { addTypeArgument(mapTypeArgument(it) ?: it) }
                    }
                    build()
                }.takeUnless { it == signature }
            }
        }
    }

    fun mapTypeArgument(argument: TypeArgument): TypeArgument? {
        if (argument in excludedInstances) return null
        return argument.type?.let(::mapTypeSignature)?.let { TypeArgument.of(it, argument.variant) }
    }

    fun mapTypeDescriptor(descriptor: TypeDescriptor): TypeDescriptor? {
        if (descriptor in excludedInstances) return null
        return when (descriptor.kind) {
            TypeDescriptor.Kind.Array -> mapTypeDescriptor(descriptor.arrayComponent)?.array(descriptor.arrayDimensions)
            TypeDescriptor.Kind.Primitive -> null
            TypeDescriptor.Kind.Class -> mapClass(descriptor.internalName)?.let { newTypeDescriptorInternal("L$it;") }
        }
    }

    fun mapFieldRef(reference: FieldReference): FieldReference? {
        if (reference in excludedInstances) return null
        val mappedOwner = mapClassRef(reference.owner)
        val mappedDescriptor = mapTypeDescriptor(reference.descriptor)
        if (mappedOwner == null && mappedDescriptor == null) return null
        return FieldReference(mappedOwner ?: reference.owner, reference.name, mappedDescriptor ?: reference.descriptor)
    }

    /**
     * The ConstantMapper that maps [ConstantClass], [ConstantFieldHandle], and [ConstantMethodHandle] via
     * the [RelocationMapping] passed to attachment argument.
     * The relocation of Constant is not primitive relocation so excluding instance of [Constant] is not supported.
     */
    object ConstantMapper : com.anatawa12.relocator.classes.ConstantMapper<RelocationMapping>() {
        override fun mapConstantClass(attachment: RelocationMapping, value: ConstantClass): ConstantClass {
            return attachment.mapTypeDescriptor(value.descriptor)?.let(::ConstantClass) ?: value
        }
        override fun mapConstantFieldHandle(attachment: RelocationMapping, value: ConstantFieldHandle) =
            attachment.mapFieldRef(value.field)?.let { ConstantFieldHandle(value.type, it) } ?: value
        override fun mapConstantMethodHandle(attachment: RelocationMapping, value: ConstantMethodHandle) =
            attachment.mapMethodRef(value.method)?.let { ConstantMethodHandle(value.type, it, value.isInterface) }
                ?: value
    }
}
