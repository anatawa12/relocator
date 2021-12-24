package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import org.objectweb.asm.Opcodes.*
import kotlin.text.StringBuilder

internal class IndentedBuilder {
    private val builder = StringBuilder()
    private var indent = ""

    fun <T: Any> T.appendPrefixed(prefix: String) {
        appendln("$prefix: $this")
    }

    fun <T: Any> Iterable<T>.appendList(headingLine: String) {
        val iterator = iterator()
        if (iterator.hasNext()) {
            "$headingLine:" {
                do {
                    appendln("${iterator.next()}")
                } while (iterator.hasNext())
            }
        }
    }

    fun indent() {
        indent += "  "
    }

    fun outdent() {
        indent = indent.substring(2)
    }

    inline operator fun String.invoke(block: IndentedBuilder.() -> Unit) {
        appendln("$this:")
        indent()
        block()
        outdent()
    }

    fun appendln(value: String) {
        builder.appendLine(indent + value)
    }

    override fun toString(): String = builder.toString()

    companion object {
        inline operator fun invoke(block: IndentedBuilder.() -> Unit) : String {
            return IndentedBuilder().apply(block).toString()
        }
        inline operator fun invoke(prefix: String, block: IndentedBuilder.() -> Unit) : String {
            return IndentedBuilder {
                prefix {
                    block()
                }
            }
        }
    }
}

internal fun IndentedBuilder.dumpClassFile(item: ClassFile) {
    "class ${item.version} ${modifiers(item.access, 0)}${item.name}" {
        item.signature?.appendPrefixed("Signature")
        item.superName?.appendPrefixed("Extends")
        item.interfaces.appendList("Interfaces")
        item.sourceFile?.appendPrefixed("SourceFile")
        item.sourceDebug?.appendPrefixed("SourceDebug")
        item.outerClass?.appendPrefixed("OuterClass")
        item.outerMethod?.appendPrefixed("OuterMethod")
        item.outerMethodDesc?.appendPrefixed("OuterMethodDesc")
        item.visibleAnnotations.appendList("VisibleAnnotations")
        item.invisibleAnnotations.appendList("InvisibleAnnotations")
        item.visibleTypeAnnotations.appendList("VisibleTypeAnnotations")
        item.invisibleTypeAnnotations.appendList("InvisibleTypeAnnotations")
        item.innerClasses.appendList("InnerClasses")
        item.nestHostClass?.appendPrefixed("NestHost")
        item.nestMembers.appendList("NestMembers")
        item.permittedSubclasses.appendList("PermittedSubclasses")
        for (method in item.methods) dumpMethod(method)
        for (field in item.fields) dumpField(field)
        for (field in item.recordFields) dumpRecordFields(field)
    }
}

private fun IndentedBuilder.dumpMethod(method: ClassMethod) {
    "method ${modifiers(method.access, 2)}${method.name} ${method.descriptor}"{
        method.signature?.appendPrefixed("Signature")
        method.exceptions.appendList("Exceptions")
        method.parameters.appendList("Parameters")
        method.visibleAnnotations.appendList("VisibleAnnotations")
        method.invisibleAnnotations.appendList("InvisibleAnnotations")
        method.visibleTypeAnnotations.appendList("VisibleTypeAnnotations")
        method.invisibleTypeAnnotations.appendList("InvisibleTypeAnnotations")
        method.annotationDefault?.appendPrefixed("AnnotationDefault")
        if (method.visibleParameterAnnotations.any { !it.isNullOrEmpty() }) "VisibleParameterAnnotations" {
            for (withIndex in method.visibleParameterAnnotations.withIndex()) {
                withIndex.value?.appendList("#${withIndex.index}")
            }
        }
        if (method.invisibleParameterAnnotations.any { !it.isNullOrEmpty() }) "InvisibleParameterAnnotations" {
            for (withIndex in method.invisibleParameterAnnotations.withIndex()) {
                withIndex.value?.appendList("#${withIndex.index}")
            }
        }
        method.classCode?.let(::dumpClassCode)
    }
}

private fun IndentedBuilder.dumpClassCode(classCode: ClassCode) {
    "Code" {
        for (insn in classCode.instructions) {
            when (insn) {
                is CastInsn -> appendln("CAST ${insn.from} to ${insn.to}")
                is FieldInsn -> appendln("${insn.insn} ${insn.field}")
                is IIncInsn -> appendln("IINC #${insn.variable} ${insn.value}")
                is InvokeDynamicInsn -> appendln("INVOKEDYNAMIC ${insn.target}")
                is JumpInsn -> appendln("${insn.insn} ${insn.target}")
                is LdcInsn -> appendln("LDC ${insn.value}")
                is LookupSwitchInsn -> {
                    appendln("LOOKUPSWITCH")
                    for ((value, label) in insn.labels) {
                        appendln("  $value: $label")
                    }
                    appendln("  default: ${insn.default}")
                }
                is MethodInsn -> if (insn.isInterface) appendln("${insn.insn} interface ${insn.method}")
                else appendln("${insn.insn} ${insn.method}")
                is MultiANewArrayInsn -> appendln("MULTIANEWARRAY ${insn.type} ${insn.dimensions}")
                is RetInsn -> appendln("RET ${insn.variable}")
                is SimpleInsn -> appendln("${insn.insn}")
                is TableSwitchInsn -> {
                    appendln("TABLESWITCH ${insn.min}..${insn.min + insn.labels.size - 1}")
                    for (label in insn.labels) {
                        appendln("  $label")
                    }
                    appendln("  default: ${insn.default}")
                }
                is TypeInsn -> appendln("${insn.insn} ${insn.type}")
                is TypedInsn -> appendln("${insn.insn} ${insn.type}")
                is VarInsn -> appendln("${insn.insn} ${insn.type} ${insn.variable}")
            }
        }
        if (classCode.tryCatchBlocks.isNotEmpty()) {
            "TryCatchBlocks" {
                for (tryCatchBlock in classCode.tryCatchBlocks) {
                    appendln("handles ${tryCatchBlock.type}")
                    appendln("  since:  ${tryCatchBlock.start}")
                    appendln("  end:    ${tryCatchBlock.end}")
                    appendln("  handle: ${tryCatchBlock.handler}")
                }
            }
        }
        classCode.maxStack.appendPrefixed("MaxStack")
        classCode.maxLocals.appendPrefixed("MaxLocals")
        if (classCode.localVariables.isNotEmpty()) {
            "LocalVariables" {
                for ((i, localVariable) in classCode.localVariables.withIndex()) {
                    appendln("Variable#$i")
                    appendln("  Name: ${localVariable.name}")
                    appendln("  Descriptor: ${localVariable.descriptor}")
                    appendln("  Signature: ${localVariable.signature}")
                    appendln("  Start: ${localVariable.start}")
                    appendln("  End: ${localVariable.end}")
                    appendln("  Index: ${localVariable.index}")
                }
            }
        }
        if (classCode.visibleLocalVariableAnnotations.isNotEmpty()) {
            "VisibleLocalVariableAnnotations" {
                for (annotation in classCode.visibleLocalVariableAnnotations) {
                    if (annotation.typePath != null) {
                        appendln("${annotation.type} ${annotation.typePath} ${annotation.rangeList(classCode.localVariables)} ${annotation.annotation}")
                    } else {
                        appendln("${annotation.type} ${annotation.rangeList(classCode.localVariables)} ${annotation.annotation}")
                    }
                }
            }
        }
        if (classCode.invisibleLocalVariableAnnotations.isNotEmpty()) {
            "InvisibleLocalVariableAnnotations" {
                for (annotation in classCode.invisibleLocalVariableAnnotations) {
                    if (annotation.typePath != null) {
                        appendln("${annotation.type} ${annotation.typePath} ${annotation.rangeList(classCode.localVariables)} ${annotation.annotation}")
                    } else {
                        appendln("${annotation.type} ${annotation.rangeList(classCode.localVariables)} ${annotation.annotation}")
                    }
                }
            }
        }
    }
}

private fun IndentedBuilder.dumpField(field: ClassField) {
    "field ${modifiers(field.access, 3)}${field.name} ${field.descriptor}" {
        field.signature?.appendPrefixed("Signature")
        field.value?.appendPrefixed("Value")
        field.visibleAnnotations.appendList("VisibleAnnotations")
        field.invisibleAnnotations.appendList("InvisibleAnnotations")
        field.visibleTypeAnnotations.appendList("VisibleTypeAnnotations")
        field.invisibleTypeAnnotations.appendList("InvisibleTypeAnnotations")
    }
}

private fun IndentedBuilder.dumpRecordFields(field: ClassRecordField) {
    "record field ${field.name} ${field.descriptor}" {
        field.signature?.appendPrefixed("Signature")
        field.visibleAnnotations.appendList("VisibleAnnotations")
        field.invisibleAnnotations.appendList("InvisibleAnnotations")
        field.visibleTypeAnnotations.appendList("VisibleTypeAnnotations")
        field.invisibleTypeAnnotations.appendList("InvisibleTypeAnnotations")
    }
}

private fun ClassLocalVariableAnnotation.rangeList(localVariables: List<LocalVariable>) = ranges.map { range ->
    localVariables.indexOfFirst { it.index == range.index && it.start == range.begin && it.end == range.end }
}

// 0: class
// 2: method
// 3: field
// 4: module
// 5: module requires
internal fun modifiers(flags: Int, type: Int) = buildString {
    if (flags.hasFlag(ACC_PUBLIC)) append("public ")
    if (flags.hasFlag(ACC_PRIVATE)) append("private ")
    if (flags.hasFlag(ACC_PROTECTED)) append("protected ")
    if (flags.hasFlag(ACC_STATIC)) append("static ")
    if (flags.hasFlag(ACC_FINAL)) append("final ")
    if (type == 0 && flags.hasFlag(ACC_SUPER)) append("super ")
    if (type == 2 && flags.hasFlag(ACC_SYNCHRONIZED)) append("synchronized ")
    if (type == 4 && flags.hasFlag(ACC_OPEN)) append("open ")
    if (type == 5 && flags.hasFlag(ACC_TRANSITIVE)) append("transitive ")
    if (type == 3 && flags.hasFlag(ACC_VOLATILE)) append("volatile ")
    if (type == 2 && flags.hasFlag(ACC_BRIDGE)) append("bridge ")
    if (type == 5 && flags.hasFlag(ACC_STATIC_PHASE)) append("static_phase ")
    if (type == 2 && flags.hasFlag(ACC_VARARGS)) append("varargs ")
    if (type == 3 && flags.hasFlag(ACC_TRANSIENT)) append("transient ")
    if (flags.hasFlag(ACC_NATIVE)) append("native ")
    if (flags.hasFlag(ACC_INTERFACE)) append("interface ")
    if (flags.hasFlag(ACC_ABSTRACT)) append("abstract ")
    if (flags.hasFlag(ACC_STRICT)) append("strict ")
    if (flags.hasFlag(ACC_SYNTHETIC)) append("synthetic ")
    if (flags.hasFlag(ACC_ANNOTATION)) append("annotation ")
    if (flags.hasFlag(ACC_ENUM)) append("enum ")
    if (type != 0 && flags.hasFlag(ACC_MANDATED)) append("mandated ")
    if (type == 0 && flags.hasFlag(ACC_MODULE)) append("module ")
}
