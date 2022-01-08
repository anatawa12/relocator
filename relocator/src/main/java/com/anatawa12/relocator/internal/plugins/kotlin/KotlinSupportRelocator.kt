package com.anatawa12.relocator.internal.plugins.kotlin

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.internal.component1
import com.anatawa12.relocator.internal.component2
import com.anatawa12.relocator.plugin.AnnotationLocation
import com.anatawa12.relocator.plugin.ClassRelocator
import com.anatawa12.relocator.plugin.RelocateResult
import kotlinx.metadata.*
import kotlinx.metadata.jvm.*

class KotlinSupportRelocator(
    val parameters: Parameters
) : ClassRelocator() {
    // TODO: KProperty
    private val keepMapped: Boolean
    private val doMapping: Boolean

    init {
        keepMapped = parameters.provideForReflection ||
                parameters.isKotlinMetadataMapped && parameters.libraryUseMode == LibraryUseMode.Metadata
        doMapping = parameters.provideForReflection || parameters.libraryUseMode != LibraryUseMode.DoNotProvide
    }

    override fun relocate(annotation: ClassAnnotation, visible: Boolean, location: AnnotationLocation): RelocateResult {
        if (annotation.annotationClass in parameters.kotlinMetadatas)
            return RelocateResult.Finish
        return RelocateResult.Continue
    }

    override fun relocate(classFile: ClassFile): RelocateResult {
        val visibleMetadata = classFile.visibleAnnotations
            .firstOrNull { it.annotationClass in parameters.kotlinMetadatas }
        if (visibleMetadata != null) {
            process(visibleMetadata, true, classFile)
        } else {
            val invisibleMetadata =
                classFile.invisibleAnnotations.firstOrNull { it.annotationClass in parameters.kotlinMetadatas }
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
                metadata.accept(parameters.visitors.KmClassVisitorImpl(writer))
                writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.FileFacade -> {
                val writer = KotlinClassMetadata.FileFacade.Writer()
                metadata.accept(parameters.visitors.KmPackageVisitorImpl(writer))
                writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.SyntheticClass -> {
                val writer = KotlinClassMetadata.SyntheticClass.Writer()
                if (!metadata.isLambda) return
                metadata.accept(parameters.visitors.KmLambdaVisitorImpl(writer))
                writer.write(header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.MultiFileClassFacade -> {
                KotlinClassMetadata.MultiFileClassFacade.Writer().write(
                    metadata.partClassNames.map { parameters.visitors.mapInternalName(it) },
                    header.metadataVersion,
                    header.extraInt,
                )
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                val writer = KotlinClassMetadata.MultiFileClassPart.Writer()
                metadata.accept(parameters.visitors.KmPackageVisitorImpl(writer))
                writer.write(metadata.facadeClassName, header.metadataVersion, header.extraInt)
            }
            is KotlinClassMetadata.Unknown -> error("unsupported metadata: ${header.kind}")
            null -> error("un-parsable metadata version: ${header.metadataVersion.contentToString()}")
        }
        val values = makeAnnotationValues(mappedMetadata.header)
        if (parameters.provideForReflection) {
            annotation.values.clear()
            annotation.values.addAll(values)
            annotation.annotationClass = parameters.mappedKotlinMetadata
            parameters.excludePlugin.exclude(annotation)
        }
        when (parameters.libraryUseMode) {
            LibraryUseMode.DoNotProvide -> {}
            LibraryUseMode.Metadata -> {
                classFile.invisibleAnnotations.add(ClassAnnotation(Parameters.kotlinMetadata, values)
                    .apply(parameters.excludePlugin::exclude))
            }
        }
    }

    companion object {
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
