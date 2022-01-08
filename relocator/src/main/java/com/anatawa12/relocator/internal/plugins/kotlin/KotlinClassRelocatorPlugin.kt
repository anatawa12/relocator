package com.anatawa12.relocator.internal.plugins.kotlin

import com.anatawa12.relocator.classes.MethodDescriptor
import com.anatawa12.relocator.classes.TypeDescriptor
import com.anatawa12.relocator.diagnostic.SuppressingLocation.*
import com.anatawa12.relocator.diagnostic.SuppressingValue.StringPattern
import com.anatawa12.relocator.diagnostic.SuppressingValue.StringValue
import com.anatawa12.relocator.plugin.*
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.MethodReference
import com.anatawa12.relocator.reflect.ClassRef
import com.anatawa12.relocator.reflect.StringRef

class KotlinClassRelocatorPlugin : ClassRelocatorPlugin {
    override fun getName(): String = "kotlin"

    override fun getDependencies(): Array<String> = arrayOf("smap")

    override fun preApply(context: PreClassRelocatorPluginContext) {
        context.reflectionMap.apply {

            val void = TypeDescriptor("V")
            val stringDesc = TypeDescriptor("L${"java/lang/String"};")

            addRefClass(MethodReference("kotlin/jvm/internal/Intrinsics", "checkHasClass", MethodDescriptor(void, stringDesc)),
                ClassRef.named(StringRef.param(0)))
            addRefClass(MethodReference("kotlin/jvm/internal/Intrinsics", "checkHasClass", MethodDescriptor(void, stringDesc, stringDesc)),
                ClassRef.named(StringRef.param(0)))

            context.suppression.run {
                add(
                    InMethodWithType("kotlinx/coroutines/debug/internal/DebugProbesImpl",
                        "getDynamicAttach",
                        MethodDescriptor("()Lkotlin/jvm/functions/Function1;")),
                    "UNRESOLVABLE_CLASS",
                    StringValue("kotlinx/coroutines/debug/internal/ByteBuddyDynamicAttach"),
                )

                val platformImplementationsKtStaticCtor =
                    InMethodWithType("kotlin/internal/PlatformImplementationsKt",
                        "<clinit>",
                        MethodDescriptor("()V"))
                add(platformImplementationsKtStaticCtor, "UNRESOLVABLE_CLASS",
                    StringPattern("kotlin/internal/jdk[78]/JDK[78]PlatformImplementations"))
                add(platformImplementationsKtStaticCtor, "UNRESOLVABLE_CLASS",
                    StringPattern("kotlin/internal/JRE[78]PlatformImplementations"))

                val buildCache =
                    InMethodWithType("kotlin/coroutines/jvm/internal/ModuleNameRetriever",
                        "buildCache",
                        MethodDescriptor("(Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;)" +
                                "Lkotlin/coroutines/jvm/internal/ModuleNameRetriever\$Cache;"))
                add(buildCache, "UNRESOLVABLE_CLASS", StringValue("java/lang/Module"))
                add(buildCache, "UNRESOLVABLE_CLASS", StringValue("java/lang/module/ModuleDescriptor"))
                add(buildCache,
                    "UNRESOLVABLE_METHOD",
                    StringValue("java/lang/Class"),
                    StringValue("getModule"),
                    StringValue("()"))
                add(buildCache,
                    "UNRESOLVABLE_METHOD",
                    StringValue("java/lang/Module"),
                    StringValue("getDescriptor"),
                    StringValue("()"))
                add(buildCache,
                    "UNRESOLVABLE_METHOD",
                    StringValue("java/lang/module/ModuleDescriptor"),
                    StringValue("name"),
                    StringValue("()"))

                add(InMethodWithType("kotlin/coroutines/jvm/internal/DebugMetadataKt",
                    "getLabel",
                    MethodDescriptor("(Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;)I")), "UNRESOLVABLE_REFLECTION_FIELD")

                add(InPackage("kotlin/reflect/jvm"), "UNRESOLVABLE_REFLECTION_FIELD")
                add(InPackage("kotlin/reflect/jvm"), "UNRESOLVABLE_REFLECTION_METHOD")
                add(InPackage("kotlin/reflect/jvm"), "UNRESOLVABLE_REFLECTION_CLASS")

                add(InClass("kotlinx/coroutines/CommonPool"), "UNRESOLVABLE_REFLECTION_METHOD")
                add(InClass("kotlinx/coroutines/internal/FastServiceLoader"), "UNRESOLVABLE_CLASS")
                add(InClass("kotlinx/coroutines/internal/FastServiceLoader"), "UNRESOLVABLE_METHOD")
                add(InClass("kotlinx/coroutines/internal/FastServiceLoader"), "UNRESOLVABLE_REFLECTION_CLASS")
                add(InClass("kotlinx/coroutines/internal/FastServiceLoader"), "UNRESOLVABLE_REFLECTION_METHOD")
                add(InClass("kotlinx/coroutines/internal/FastServiceLoaderKt"), "UNRESOLVABLE_CLASS",
                    StringValue("android/os/Build"))
                add(InMethod("kotlin/jvm/internal/Intrinsics", "checkHasClass"), "UNRESOLVABLE_REFLECTION_CLASS")
            }
        }
    }

    override fun apply(context: ClassRelocatorPluginContext) {
        // TODO: parameters from external
        val parameters = Parameters(context.relocationMapping, context.getPlugin("exclude") as ExcludePlugin)
        context.addClassRelocator(ClassRelocatorStep.LanguageProcessing, KotlinSupportRelocator(parameters))
    }
}

class Parameters(
    val mapping: RelocationMapping,
    val excludePlugin: ExcludePlugin,
    val libraryUseMode: LibraryUseMode = LibraryUseMode.DoNotProvide,
    val provideForReflection: Boolean = true,
    val annotationSet: AnnotationSet = AnnotationSet.JetbrainsAndKotlinJvm,
) {
    val isKotlinMetadataMapped: Boolean
    val mappedKotlinMetadata: ClassReference
    val kotlinMetadatas: Set<ClassReference>

    init {
        val mappedKotlinMetadata = mapping.mapClassRef(kotlinMetadata)
        isKotlinMetadataMapped = mappedKotlinMetadata != null
        this.mappedKotlinMetadata = mappedKotlinMetadata ?: kotlinMetadata
        kotlinMetadatas = setOf(this.mappedKotlinMetadata, kotlinMetadata)
        mapping.excludeMapping(kotlinMetadata)
        mapping.excludeMapping(this.mappedKotlinMetadata)
    }

    companion object {
        private val rsKotlin = StringBuilder()
            .append('k').append('o').append('t').append('l').append('i').append('n').toString()
        val kotlinMetadata = ClassReference("${rsKotlin}/Metadata")
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
