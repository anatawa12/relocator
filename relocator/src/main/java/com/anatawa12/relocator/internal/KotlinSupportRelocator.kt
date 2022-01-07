package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.plugin.AnnotationLocation
import com.anatawa12.relocator.plugin.ClassRelocator
import com.anatawa12.relocator.plugin.RelocateResult
import com.anatawa12.relocator.plugin.RelocationMapping
import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import org.objectweb.asm.Type

class KotlinSupportRelocator(
    val mapping: RelocationMapping,
    val libraryUseMode: LibraryUseMode = LibraryUseMode.DoNotProvide,
    val provideForReflection: Boolean = true,
    val annotationSet: AnnotationSet = AnnotationSet.JetbrainsAndKotlinJvm,
) : ClassRelocator() {
    // TODO: KProperty
    private val mappedKotlinMetadata: ClassReference
    private val kotlinMetadatas: Set<ClassReference>
    private val keepMapped: Boolean
    private val doMapping: Boolean

    init {
        mapping.excludeMapping(kotlinMetadata)
        val mappedKotlinMetadata = mapping.mapClassRef(kotlinMetadata)
        this.mappedKotlinMetadata = mappedKotlinMetadata ?: kotlinMetadata
        kotlinMetadatas = setOf(this.mappedKotlinMetadata, kotlinMetadata)
        keepMapped = provideForReflection || mappedKotlinMetadata == null && libraryUseMode == LibraryUseMode.Metadata
        doMapping = provideForReflection || libraryUseMode != LibraryUseMode.DoNotProvide
    }

    override fun relocate(annotation: ClassAnnotation, visible: Boolean, location: AnnotationLocation): RelocateResult {
        if (annotation.annotationClass in kotlinMetadatas)
            return RelocateResult.Finish
        return RelocateResult.Continue
    }

    override fun relocate(classFile: ClassFile): RelocateResult {
        val visibleMetadata = classFile.visibleAnnotations.firstOrNull { it.annotationClass in kotlinMetadatas }
        if (visibleMetadata != null) {
            process(visibleMetadata, true, classFile)
        } else {
            val invisibleMetadata =
                classFile.invisibleAnnotations.firstOrNull { it.annotationClass in kotlinMetadatas }
            if (invisibleMetadata != null) {
                process(invisibleMetadata, false, classFile)
            }
        }
        return RelocateResult.Continue
    }

    private fun process(annotation: ClassAnnotation, visible: Boolean, classFile: ClassFile) {
        if (!keepMapped)
            (if (visible) classFile.visibleAnnotations else classFile.invisibleAnnotations).remove(annotation)
        if (!doMapping) return
        val header = makeHeader(annotation)
        val mappedMetadata = when (val metadata = KotlinClassMetadata.read(header)) {
            is KotlinClassMetadata.Class -> {
                val writer = KotlinClassMetadata.Class.Writer()
                metadata.accept(KmClassVisitorImpl(writer))
                writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.FileFacade -> {
                val writer = KotlinClassMetadata.FileFacade.Writer()
                metadata.accept(KmPackageVisitorImpl(writer))
                writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.SyntheticClass -> {
                val writer = KotlinClassMetadata.SyntheticClass.Writer()
                if (!metadata.isLambda) return println("non-lambda SyntheticClass: ${classFile.name}")
                metadata.accept(KmLambdaVisitorImpl(writer))
                writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.MultiFileClassFacade -> {
                println("MultiFileClassFacade")
                return
                //val writer = KotlinClassMetadata.MultiFileClassFacade.Writer()
                //metadata.accept(KmClassVisitorImpl(writer))
                //writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                val writer = KotlinClassMetadata.MultiFileClassPart.Writer()
                metadata.accept(KmPackageVisitorImpl(writer))
                writer.write(metadata.facadeClassName, header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.Unknown -> error("unsupported metadata: ${header.kind}")
            null -> error("un-parsable metadata version: ${header.metadataVersion.contentToString()}")
        }
        val values = makeAnnotationValues(mappedMetadata.header)
        if (provideForReflection) {
            annotation.values.clear()
            annotation.values.addAll(values)
        }
        when (libraryUseMode) {
            LibraryUseMode.DoNotProvide -> {}
            LibraryUseMode.Metadata -> {
                classFile.invisibleAnnotations.add(ClassAnnotation(kotlinMetadata, values))
            }
        }
    }

    // utils

    private fun mapClassName(className: ClassName): ClassName {
        if (className.isLocal) {
            return className
        }
        if (className in kotlinPrimitive) {
            return className
        }
        val splitted = className.split('.', limit = 2)
        val mapped = mapping.mapClass(splitted[0]) ?: return className

        return if (splitted.size == 1) mapped else "$mapped.${splitted[1]}"
    }

    private fun mapInternalName(internalName: String): String =
        mapping.mapClassRef(ClassReference(internalName))?.name ?: internalName

    private fun mapKmAnnotation(annotation: KmAnnotation): KmAnnotation {
        println("skipping mapKmAnnotation: $annotation")
        return annotation
    }

    // TODO: support <anonymous>(V)V
    // V is kotlin/Unit
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
        private val kotlinMetadata = ClassReference("$rsKotlin/Metadata")
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

        fun makeHeader(annotation: ClassAnnotation): KotlinClassHeader {
            var kind: Int? = null
            var metadataVersion: IntArray? = null
            var data1: Array<String>? = null
            var data2: Array<String>? = null
            var extraString: String? = null
            var packageName: String? = null
            var extraInt = 0
            for ((key, value) in annotation.values) when (key) {
                "k" -> (value as? AnnotationInt)?.value?.let { kind = it }
                "mv" -> (value as? AnnotationArray)?.tryToIntArray()?.let { metadataVersion = it }
                "d1" -> (value as? AnnotationArray)?.tryToStringArray()?.let { data1 = it }
                "d2" -> (value as? AnnotationArray)?.tryToStringArray()?.let { data2 = it }
                "xs" -> (value as? AnnotationString)?.value?.let { extraString = it }
                "pn" -> (value as? AnnotationString)?.value?.let { packageName = it }
                "xi" -> (value as? AnnotationInt)?.value?.let { extraInt = it }
            }
            return KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
        }

        fun makeAnnotationValues(header: KotlinClassHeader): List<KeyValuePair> = buildList {
            add(KeyValuePair("k", AnnotationInt(header.kind)))
            add(KeyValuePair("mv", AnnotationArray(header.metadataVersion)))
            header.data1.takeUnless(Array<String>::isEmpty)?.let(::AnnotationArray)?.let { add(KeyValuePair("d1", it)) }
            header.data2.takeUnless(Array<String>::isEmpty)?.let(::AnnotationArray)?.let { add(KeyValuePair("d2", it)) }
            header.extraString.takeUnless(String::isEmpty)?.let(::AnnotationString)?.let { add(KeyValuePair("xs", it)) }
            header.packageName.takeUnless(String::isEmpty)?.let(::AnnotationString)?.let { add(KeyValuePair("pn", it)) }
            add(KeyValuePair("xi", AnnotationInt(header.extraInt)))
        }
    }

    enum class LibraryUseMode {
        DoNotProvide,
        Metadata,
        //TypeAnnotations,
    }

    enum class AnnotationSet {
        // use jetbrains nullability and kotlin-jvm mutability
        JetbrainsAndKotlinJvm,
    }
}

private fun AnnotationArray.tryToIntArray(): IntArray? {
    val array = IntArray(size)
    for ((i, v) in withIndex())
        array[i] = (v as? AnnotationInt)?.value ?: return null
    return array
}

private fun AnnotationArray.tryToStringArray(): Array<String>? {
    val array = arrayOfNulls<String>(size)
    for ((i, v) in withIndex())
        array[i] = (v as? AnnotationString)?.value ?: return null
    @Suppress("UNCHECKED_CAST")
    return array as Array<String>
}
