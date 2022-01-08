package com.anatawa12.relocator.plugin

import com.anatawa12.relocator.file.FileObject

abstract class FileRelocator {
    open fun relocate(file: FileObject): RelocateResult = RelocateResult.Continue
}
