package com.anatawa12.relocator.internal

import com.anatawa12.relocator.file.FileObject
import com.anatawa12.relocator.plugin.*

class SimpleFileRelocator(
    val mapping: RelocationMapping
) : FileRelocator() {
    override fun relocate(file: FileObject): RelocateResult {
        mapping.mapFilePath(file.path)?.let { file.path = it }
        return RelocateResult.Continue
    }
}
