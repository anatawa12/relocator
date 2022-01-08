package com.anatawa12.relocator.internal.plugins.kotlin

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.internal.mapList
import com.anatawa12.relocator.plugin.RelocationMapping
import com.anatawa12.relocator.reference.ClassReference
import kotlinx.metadata.*
import kotlinx.metadata.KmAnnotationArgument.*
import kotlinx.metadata.KmAnnotationArgument.AnnotationValue
import kotlinx.metadata.jvm.*
import org.objectweb.asm.Type

class RelocatorVisitors(val mapping: RelocationMapping) {

    // utils

    fun mapClassName(className: ClassName): ClassName {
        if (className.isLocal) return className
        if (className in kotlinPrimitive) return className
        val splitted = className.split('.', limit = 2)
        val mapped = mapping.mapSlashedClass(splitted[0]) ?: return className

        return if (splitted.size == 1) mapped else "$mapped.${splitted[1]}"
    }

    fun mapInternalName(internalName: String): String =
        mapping.mapClassRef(ClassReference(internalName))?.name ?: internalName

    fun mapKmAnnotation(annotation: KmAnnotation): KmAnnotation = mapKmAnnotation0(annotation) ?: annotation

    private fun mapKmAnnotation0(annotation: KmAnnotation): KmAnnotation? {
        val args = annotation.arguments.toList()
        val className = mapClassName(annotation.className).takeUnless { it == annotation.className }
        val list = mapList(args) { (k, v) -> mapKmAnnotationValue(v)?.let { k to it } }
        if (className == null && list == null) return null
        return KmAnnotation(
            className ?: annotation.className,
            list?.toMap() ?: annotation.arguments,
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun mapKmAnnotationValue(value: KmAnnotationArgument): KmAnnotationArgument? = when (value) {
        is ByteValue -> value
        is CharValue -> value
        is ShortValue -> value
        is IntValue -> value
        is LongValue -> value
        is FloatValue -> value
        is DoubleValue -> value
        is BooleanValue -> value
        is UByteValue -> value
        is UShortValue -> value
        is UIntValue -> value
        is ULongValue -> value
        is StringValue -> value
        is KClassValue -> KClassValue(mapClassName(value.className), value.arrayDimensionCount)
        is EnumValue -> EnumValue(mapClassName(value.enumClassName), value.enumEntryName)
        is AnnotationValue -> mapKmAnnotation0(value.annotation)?.let(::AnnotationValue)
        is ArrayValue -> mapList(value.elements, ::mapKmAnnotationValue)?.let(::ArrayValue)
    }

    private fun mapJvmMethodSignature(signature: JvmMethodSignature): JvmMethodSignature {
        val returns = TypeDescriptor(Type.getReturnType(signature.desc).descriptor)
        // use this to support 'V' on arguments
        val arguments = Type.getArgumentTypes(signature.desc).map { TypeDescriptor(it.descriptor) }

        val mappedReturns = mapping.mapTypeDescriptor(returns)
        val mappedArguments = mapList(arguments, mapping::mapTypeDescriptor)
        if (mappedReturns == null && mappedArguments == null) return signature
        return JvmMethodSignature(signature.name, buildString {
            append('(')
            (mappedArguments ?: arguments).forEach { append(it) }
            append(')')
            append(mappedReturns ?: returns)
        })
    }

    private fun mapJvmFieldSignature(annotation: JvmFieldSignature): JvmFieldSignature =
        mapping.mapTypeDescriptor(TypeDescriptor(annotation.desc))
            ?.let { JvmFieldSignature(annotation.name, it.descriptor) } ?: annotation

    // region visitors

    inner class KmClassVisitorImpl(delegate: KmClassVisitor) : KmClassVisitor(delegate) {
        override fun visit(flags: Flags, name: ClassName) = super.visit(flags, mapClassName(name))

        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor? =
            super.visitTypeParameter(flags, name, id, variance)?.let(::KmTypeParameterVisitorImpl)

        override fun visitSupertype(flags: Flags): KmTypeVisitor? =
            super.visitSupertype(flags)?.let(::KmTypeVisitorImpl)

        override fun visitConstructor(flags: Flags): KmConstructorVisitor? =
            super.visitConstructor(flags)?.let(::KmConstructorVisitorImpl)

        //override fun visitCompanionObject(name: String) = super.visitCompanionObject(name)
        //override fun visitNestedClass(name: String) = super.visitNestedClass(name)
        //override fun visitEnumEntry(name: String) = super.visitEnumEntry(name)

        override fun visitSealedSubclass(name: ClassName) = super.visitSealedSubclass(mapClassName(name))

        //override fun visitInlineClassUnderlyingPropertyName(name: String) =
        //    super.visitInlineClassUnderlyingPropertyName(name)

        override fun visitInlineClassUnderlyingType(flags: Flags): KmTypeVisitor? =
            super.visitInlineClassUnderlyingType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            super.visitVersionRequirement()?.let(::KmVersionRequirementVisitorImpl)

        override fun visitExtensions(type: KmExtensionType): JvmClassExtensionVisitor? =
            super.visitExtensions(type)?.let(::JvmClassExtensionVisitorImpl)

        // KmDeclarationContainerVisitor
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
            super.visitFunction(flags, name)?.let(::KmFunctionVisitorImpl)

        override fun visitProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags,
        ): KmPropertyVisitor? {
            return super.visitProperty(flags, name, getterFlags, setterFlags)?.let(::KmPropertyVisitorImpl)
        }

        override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? =
            super.visitTypeAlias(flags, name)?.let(::KmTypeAliasVisitorImpl)
        // KmDeclarationContainerVisitor

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmPackageVisitorImpl(delegate: KmPackageVisitor) : KmPackageVisitor(delegate) {
        override fun visitExtensions(type: KmExtensionType): JvmPackageExtensionVisitor? =
            delegate?.visitExtensions(type)?.let(::JvmPackageExtensionVisitorImpl)

        // KmDeclarationContainerVisitor
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
            super.visitFunction(flags, name)?.let(::KmFunctionVisitorImpl)

        override fun visitProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags,
        ): KmPropertyVisitor? =
            super.visitProperty(flags, name, getterFlags, setterFlags)?.let(::KmPropertyVisitorImpl)

        override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? =
            super.visitTypeAlias(flags, name)?.let(::KmTypeAliasVisitorImpl)
        // KmDeclarationContainerVisitor

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmModuleFragmentVisitorImpl(delegate: KmModuleFragmentVisitor) : KmModuleFragmentVisitor(delegate) {
        override fun visitPackage(): KmPackageVisitor? =
            super.visitPackage()?.let(::KmPackageVisitorImpl)

        override fun visitClass(): KmClassVisitor? =
            super.visitClass()?.let(::KmClassVisitorImpl)

        override fun visitExtensions(type: KmExtensionType) = error("not supported")
        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmModuleVisitorImpl(delegate: KmModuleVisitor) : KmModuleVisitor(delegate) {
        override fun visitPackageParts(
            fqName: String,
            fileFacades: List<String>,
            multiFileClassParts: Map<String, String>
        ) = super.visitPackageParts(
            mapping.mapDottedClass(fqName) ?: fqName,
            mapList(fileFacades, mapping::mapSlashedClass) ?: fileFacades,
            multiFileClassParts.map { (k, v) ->
                (mapping.mapSlashedClass(k) ?: k) to (mapping.mapSlashedClass(v) ?: v)
            }.toMap(),
        )

        override fun visitAnnotation(annotation: KmAnnotation) = super.visitAnnotation(mapKmAnnotation(annotation))

        override fun visitOptionalAnnotationClass(): KmClassVisitor? =
            super.visitOptionalAnnotationClass()?.let(::KmClassVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmLambdaVisitorImpl(delegate: KmLambdaVisitor) : KmLambdaVisitor(delegate) {
        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
            super.visitFunction(flags, name)?.let(::KmFunctionVisitorImpl)
        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmConstructorVisitorImpl(delegate: KmConstructorVisitor) : KmConstructorVisitor(delegate) {
        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            super.visitValueParameter(flags, name)?.let(::KmValueParameterVisitorImpl)

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            super.visitVersionRequirement()?.let(::KmVersionRequirementVisitorImpl)

        override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor? =
            super.visitExtensions(type)?.let(::JvmConstructorExtensionVisitorImpl)
        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmFunctionVisitorImpl(delegate: KmFunctionVisitor) : KmFunctionVisitor(delegate) {
        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor? =
            super.visitTypeParameter(flags, name, id, variance)?.let(::KmTypeParameterVisitorImpl)

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? =
            super.visitReceiverParameterType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            super.visitValueParameter(flags, name)?.let(::KmValueParameterVisitorImpl)

        override fun visitReturnType(flags: Flags): KmTypeVisitor? =
            super.visitReturnType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            super.visitVersionRequirement()?.let(::KmVersionRequirementVisitorImpl)

        override fun visitContract(): KmContractVisitor? =
            super.visitContract()?.let(::KmContractVisitorImpl)

        override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? =
            super.visitExtensions(type)?.let(::JvmFunctionExtensionVisitorImpl)
        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmPropertyVisitorImpl(delegate: KmPropertyVisitor) : KmPropertyVisitor(delegate) {
        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor? =
            super.visitTypeParameter(flags, name, id, variance)?.let(::KmTypeParameterVisitorImpl)

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? =
            super.visitReceiverParameterType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitSetterParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            super.visitSetterParameter(flags, name)?.let(::KmValueParameterVisitorImpl)

        override fun visitReturnType(flags: Flags): KmTypeVisitor? =
            super.visitReturnType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            super.visitVersionRequirement()?.let(::KmVersionRequirementVisitorImpl)

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? =
            super.visitExtensions(type)?.let(::JvmPropertyExtensionVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmTypeAliasVisitorImpl(delegate: KmTypeAliasVisitor) : KmTypeAliasVisitor(delegate) {
        override fun visitTypeParameter(
            flags: Flags,
            name: String,
            id: Int,
            variance: KmVariance
        ): KmTypeParameterVisitor? =
            super.visitTypeParameter(flags, name, id, variance)?.let(::KmTypeParameterVisitorImpl)

        override fun visitUnderlyingType(flags: Flags): KmTypeVisitor? =
            super.visitUnderlyingType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitExpandedType(flags: Flags): KmTypeVisitor? =
            super.visitExpandedType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitAnnotation(annotation: KmAnnotation) =
            super.visitAnnotation(mapKmAnnotation(annotation))

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? =
            super.visitVersionRequirement()?.let(::KmVersionRequirementVisitorImpl)

        override fun visitExtensions(type: KmExtensionType) = error("not supported")

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmValueParameterVisitorImpl(delegate: KmValueParameterVisitor) : KmValueParameterVisitor(delegate) {
        override fun visitType(flags: Flags): KmTypeVisitor? =
            super.visitType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitVarargElementType(flags: Flags): KmTypeVisitor? =
            super.visitVarargElementType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitExtensions(type: KmExtensionType) = error("not supported")

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmTypeParameterVisitorImpl(delegate: KmTypeParameterVisitor) : KmTypeParameterVisitor(delegate) {
        override fun visitUpperBound(flags: Flags): KmTypeVisitor? =
            super.visitUpperBound(flags)?.let(::KmTypeVisitorImpl)

        override fun visitExtensions(type: KmExtensionType): KmTypeParameterExtensionVisitor? =
            super.visitExtensions(type)?.let(::JvmTypeParameterExtensionVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmTypeVisitorImpl(delegate: KmTypeVisitor) : KmTypeVisitor(delegate) {
        override fun visitClass(name: ClassName) = super.visitClass(mapClassName(name))

        override fun visitTypeAlias(name: ClassName) = super.visitTypeAlias(mapClassName(name))

        //override fun visitTypeParameter(id: Int) = super.visitTypeParameter(id)

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? =
            super.visitArgument(flags, variance)?.let(::KmTypeVisitorImpl)

        //override fun visitStarProjection() = super.visitStarProjection()

        override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor? =
            super.visitAbbreviatedType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitOuterType(flags: Flags): KmTypeVisitor? =
            super.visitOuterType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor? =
            super.visitFlexibleTypeUpperBound(flags, typeFlexibilityId)?.let(::KmTypeVisitorImpl)

        override fun visitExtensions(type: KmExtensionType): KmTypeExtensionVisitor? =
            super.visitExtensions(type)?.let(::JvmTypeExtensionVisitorImpl)

        //super.visitExtensions(type)?.let(::KmTypeExtensionVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmVersionRequirementVisitorImpl(delegate: KmVersionRequirementVisitor) :
        KmVersionRequirementVisitor(delegate) {
        //override fun visit(
        //    kind: KmVersionRequirementVersionKind,
        //    level: KmVersionRequirementLevel,
        //    errorCode: Int?,
        //    message: String?
        //) = super.visit(kind, level, errorCode, message)

        //override fun visitVersion(major: Int, minor: Int, patch: Int) = super.visitVersion(major, minor, patch)

        // override fun visitEnd() = super.visitEnd()
    }

    inner class KmContractVisitorImpl(delegate: KmContractVisitor) : KmContractVisitor(delegate) {
        override fun visitEffect(type: KmEffectType, invocationKind: KmEffectInvocationKind?): KmEffectVisitor? =
            super.visitEffect(type, invocationKind)?.let(::KmEffectVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmEffectVisitorImpl(delegate: KmEffectVisitor) : KmEffectVisitor(delegate) {
        override fun visitConstructorArgument(): KmEffectExpressionVisitor? =
            super.visitConstructorArgument()?.let(::KmEffectExpressionVisitorImpl)

        override fun visitConclusionOfConditionalEffect(): KmEffectExpressionVisitor? =
            super.visitConclusionOfConditionalEffect()?.let(::KmEffectExpressionVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }

    inner class KmEffectExpressionVisitorImpl(delegate: KmEffectExpressionVisitor) :
        KmEffectExpressionVisitor(delegate) {
        //override fun visit(flags: Flags, parameterIndex: Int?) = super.visit(flags, parameterIndex)

        //override fun visitConstantValue(value: Any?) = super.visitConstantValue(value)

        override fun visitIsInstanceType(flags: Flags): KmTypeVisitor? =
            super.visitIsInstanceType(flags)?.let(::KmTypeVisitorImpl)

        override fun visitAndArgument(): KmEffectExpressionVisitor? =
            super.visitAndArgument()?.let(::KmEffectExpressionVisitorImpl)

        override fun visitOrArgument(): KmEffectExpressionVisitor? =
            super.visitOrArgument()?.let(::KmEffectExpressionVisitorImpl)

        //override fun visitEnd() = super.visitEnd()
    }
    // endregion visitors

    //region extension visitors

    inner class JvmClassExtensionVisitorImpl(delegate: KmClassExtensionVisitor) :
        JvmClassExtensionVisitor(delegate as JvmClassExtensionVisitor) {
        override fun visitAnonymousObjectOriginName(internalName: String) =
            super.visitAnonymousObjectOriginName(mapInternalName(internalName))

        //override fun visitJvmFlags(flags: Flags) = super.visitJvmFlags(flags)

        // JvmDeclarationContainerExtensionVisitor
        override fun visitLocalDelegatedProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor? =
            super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags)?.let(::KmPropertyVisitorImpl)

        //override fun visitModuleName(name: String) = super.visitModuleName(name)
        // end JvmDeclarationContainerExtensionVisitor

        //override fun visitEnd() = super.visitEnd()
    }

    inner class JvmPackageExtensionVisitorImpl(delegate: KmPackageExtensionVisitor) :
        JvmPackageExtensionVisitor(delegate as JvmPackageExtensionVisitor) {
        // JvmDeclarationContainerExtensionVisitor
        override fun visitLocalDelegatedProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor? =
            super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags)?.let(::KmPropertyVisitorImpl)

        //override fun visitModuleName(name: String) = super.visitModuleName(name)
        // end JvmDeclarationContainerExtensionVisitor

        //override fun visitEnd() = super.visitEnd()
    }

    inner class JvmFunctionExtensionVisitorImpl(delegate: KmFunctionExtensionVisitor) :
        JvmFunctionExtensionVisitor(delegate as JvmFunctionExtensionVisitor) {
        override fun visit(signature: JvmMethodSignature?) = super.visit(signature?.let(::mapJvmMethodSignature))

        override fun visitLambdaClassOriginName(internalName: String) =
            super.visitLambdaClassOriginName(mapInternalName(internalName))

        //override fun visitEnd() = super.visitEnd()
    }

    inner class JvmPropertyExtensionVisitorImpl(delegate: KmPropertyExtensionVisitor) :
        JvmPropertyExtensionVisitor(delegate as JvmPropertyExtensionVisitor) {
        override fun visit(
            jvmFlags: Flags,
            fieldSignature: JvmFieldSignature?,
            getterSignature: JvmMethodSignature?,
            setterSignature: JvmMethodSignature?
        ) = super.visit(
            jvmFlags,
            fieldSignature?.let(::mapJvmFieldSignature),
            getterSignature?.let(::mapJvmMethodSignature),
            setterSignature?.let(::mapJvmMethodSignature),
        )

        override fun visitSyntheticMethodForAnnotations(signature: JvmMethodSignature?) =
            super.visitSyntheticMethodForAnnotations(signature?.let(::mapJvmMethodSignature))

        override fun visitSyntheticMethodForDelegate(signature: JvmMethodSignature?) =
            super.visitSyntheticMethodForDelegate(signature?.let(::mapJvmMethodSignature))

        //override fun visitEnd() = super.visitEnd()
    }

    inner class JvmConstructorExtensionVisitorImpl(delegate: KmConstructorExtensionVisitor) :
        JvmConstructorExtensionVisitor(delegate as JvmConstructorExtensionVisitor) {
        override fun visit(signature: JvmMethodSignature?) = super.visit(signature?.let(::mapJvmMethodSignature))
    }

    inner class JvmTypeParameterExtensionVisitorImpl(delegate: KmTypeParameterExtensionVisitor) :
        JvmTypeParameterExtensionVisitor(delegate as JvmTypeParameterExtensionVisitor) {
        override fun visitAnnotation(annotation: KmAnnotation) =
            super.visitAnnotation(mapKmAnnotation(annotation))

        //override fun visitEnd() = super.visitEnd()
    }

    inner class JvmTypeExtensionVisitorImpl(delegate: KmTypeExtensionVisitor) :
        JvmTypeExtensionVisitor(delegate as JvmTypeExtensionVisitor) {
        //override fun visit(isRaw: Boolean) = super.visit(isRaw)

        override fun visitAnnotation(annotation: KmAnnotation) =
            super.visitAnnotation(mapKmAnnotation(annotation))

        //override fun visitEnd() = super.visitEnd()
    }

    //endregion extension visitors

    companion object {
        private val rsKotlin = StringBuilder()
            .append('k').append('o').append('t').append('l').append('i').append('n').toString()
        private val kotlinPrimitive: Set<ClassName> = buildSet {
            // primitives
            for (primitive in listOf("Byte", "Short", "Int", "Long", "Float", "Double", "Char", "Boolean")) {
                add("$rsKotlin/$primitive")
                add("$rsKotlin/${primitive}Array")
            }
            add("$rsKotlin/Unit")
            add("$rsKotlin/Any")

            // primitive-ish
            add("$rsKotlin/Annotation")
            add("$rsKotlin/String")
            add("$rsKotlin/CharSequence")
            add("$rsKotlin/Throwable")
            add("$rsKotlin/Cloneable")
            add("$rsKotlin/Number")
            add("$rsKotlin/Comparable")
            add("$rsKotlin/Enum")

            // collections
            for (collection in listOf("Iterable", "Iterator", "Collection", "List", "Set", "Map", "ListIterator")) {
                add("$rsKotlin/collections/$collection")
                add("$rsKotlin/collections/Mutable$collection")
            }
            add("$rsKotlin/collections/Map.Entry")
            add("$rsKotlin/collections/MutableMap.MutableEntry")
        }
    }
}
