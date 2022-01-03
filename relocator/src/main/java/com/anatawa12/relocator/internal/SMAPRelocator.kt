package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.relocation.ClassRelocator
import com.anatawa12.relocator.relocation.RelocationMapping

class SMAPRelocator(
    val mapping: RelocationMapping
) : ClassRelocator() {
    override fun relocate(classFile: ClassFile) {
        classFile.sourceDebug?.let { debug ->
            if (debug.startsWith("SMAP")) {
                val smap = SMAPParser(debug).readSMAP()
                relocateSMAP(smap)
                classFile.sourceDebug = buildString { smap.appendTo(this) }
            }
        }
    }

    private fun relocateSMAP(smap: SMAP) {
        for (smapElement in smap.elements) when (smapElement) {
            is EmbedSMAP -> smapElement.smaps.forEach(::relocateSMAP)
            is SMAPStratum -> for (element in smapElement.elements) when (element) {
                is SMAPFileSection -> for (file in element.files) {
                    file.path?.let { mapping.mapFilePath(it) }?.let { file.path = it }
                }
                is SMAPLineSection -> {}
            }
            is SMAPVendorSection -> {}
        }
    }

    override fun relocate(method: ClassMethod) {
    }

    override fun relocate(field: ClassField) {
    }

    override fun relocate(recordField: ClassRecordField) {
    }
}
