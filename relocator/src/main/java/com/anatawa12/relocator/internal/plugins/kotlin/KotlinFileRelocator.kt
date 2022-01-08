package com.anatawa12.relocator.internal.plugins.kotlin

import com.anatawa12.relocator.file.FileObject
import com.anatawa12.relocator.plugin.FileRelocator
import com.anatawa12.relocator.plugin.RelocateResult
import kotlinx.metadata.jvm.KotlinModuleMetadata

class KotlinFileRelocator(val parameters: Parameters) : FileRelocator() {
    override fun relocate(file: FileObject): RelocateResult {
        if (file.path.endsWith(".kotlin_module")) {
            if (!parameters.provideForReflection && parameters.libraryUseMode == LibraryUseMode.DoNotProvide)
                return RelocateResult.Remove
            val iter = file.binaries.listIterator()
            for (bytes in iter) {
                val writer = KotlinModuleMetadata.Writer()
                val metadata = KotlinModuleMetadata.read(bytes) ?: continue
                metadata.toKmModule().accept(parameters.visitors.KmModuleVisitorImpl(writer))
                iter.set(writer.write(/* TODO: version name */).bytes)
            }
        }
        // TODO: kotlin_builtins
        return RelocateResult.Continue
    }
}
