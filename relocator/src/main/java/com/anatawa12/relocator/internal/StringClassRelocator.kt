package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.relocation.*

class StringClassRelocator(
    val mapping: RelocationMapping
) : ClassRelocator() {
    override fun relocate(method: ClassMethod): RelocateResult {
        method.annotationDefault?.let { AnnotationWalkerImpl.walkAnnotationValue(this, it) }
        method.classCode?.let(::relocateCode)
        return RelocateResult.Continue
    }

    private fun relocateCode(code: ClassCode) {
        code.instructions.onEach(::relocateInsn)
    }

    private fun relocateInsn(insn: Insn) {
        if (insn is LdcInsn) insn.value = ConstantMapperImpl.mapConstant(this, insn.value)
    }

    override fun relocate(annotation: ClassAnnotation, visible: Boolean, location: AnnotationLocation): RelocateResult {
        AnnotationWalkerImpl.walkClassAnnotation(this, annotation)
        return RelocateResult.Continue
    }

    private fun mapString(string: String): String? {
        val (pre, name, end) = tryAsDescriptor(string)
        val mapped = when (checkNameKind(name)) {
            NameKind.NonClass -> null
            NameKind.Slashed -> mapping.mapClass(name)
            NameKind.Dotted -> mapping.mapClass(name.replace('.', '/'))?.replace('/', '.')
        } ?: return null
        return "$pre$mapped$end"
    }

    private enum class NameKind {
        NonClass,
        Slashed,
        Dotted,
    }

    private fun checkNameKind(name: String): NameKind {
        var wasSeparator = false
        /*
        0: initial
        1: slash
        2: dot
         */
        var stat = 0
        for (c in name) when (c) {
            '/' -> {
                if (wasSeparator || stat == 2) return NameKind.NonClass
                stat = 1
                wasSeparator = true
            }
            '.' -> {
                if (wasSeparator || stat == 1) return NameKind.NonClass
                stat = 2
                wasSeparator = true
            }
            else -> wasSeparator = false
        }
        return if (stat == 2) NameKind.Dotted else NameKind.Slashed
    }

    private fun tryAsDescriptor(string: String): Triple<String, String, String> {
        if (string.endsWith(';')) {
            var i = 0
            while (i in string.indices && string[i] == '[') i++
            if (i in string.indices && string[i] == 'L') {
                i++
                return Triple(string.substring(0, i), string.substring(i, string.length - 1), ";")
            }
        }
        return Triple("", string, "")
    }

    private object ConstantMapperImpl : ConstantMapper<StringClassRelocator>() {
        override fun mapConstantString(attachment: StringClassRelocator, value: ConstantString): ConstantString {
            return attachment.mapString(value.value)?.let(::ConstantString) ?: value
        }
    }

    private object AnnotationWalkerImpl : AnnotationWalker<StringClassRelocator>() {
        override fun walkAnnotationString(attachment: StringClassRelocator, value: AnnotationString) {
            attachment.mapString(value.value)?.let { value.value = it }
        }
    }
}
