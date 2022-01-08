package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.classes.ConstantDynamic
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.ConstantDynamic as ASMConstantDynamic
import org.objectweb.asm.Handle as ASMHandle
import org.objectweb.asm.Label as ASMLabel
import org.objectweb.asm.TypePath as ASMTypePath

class Writer {
    private val labelMap = LabelMap()

    fun writeClass(file: ClassFile, visitor: ClassVisitor) {
        visitor.visit(file.version, file.access, file.name, file.signature?.signature,
            file.superName?.name, file.interfaces.mapToArray { it.name })
        visitor.visitSource(file.sourceFile, file.sourceDebug)
        // no module support for now
        file.nestHostClass?.name?.let(visitor::visitNestHost)
        file.outerClass?.name?.let {
            visitor.visitOuterClass(it, file.outerMethod, file.outerMethodDesc?.descriptor)
        }
        writeAnnotations(file.visibleAnnotations, true, visitor::visitAnnotation)
        writeAnnotations(file.invisibleAnnotations, false, visitor::visitAnnotation)
        writeTypeAnnotations(file.visibleTypeAnnotations, true, visitor::visitTypeAnnotation)
        writeTypeAnnotations(file.invisibleTypeAnnotations, false, visitor::visitTypeAnnotation)
        file.innerClasses.forEach { visitor.visitInnerClass(it.name.name, it.outerName?.name, it.innerName, it.access) }
        file.nestMembers.forEach { visitor.visitNestMember(it.name) }
        file.permittedSubclasses.forEach { visitor.visitPermittedSubclass(it.name) }
        file.methods.forEach { method ->
            visitor.visitMethod(method.access, method.name, method.descriptor.descriptor,
                method.signature?.signature, method.exceptions.mapToArray { it.name })
                ?.let { writeMethod(method, it) }
        }
        file.fields.forEach { field ->
            visitor.visitField(field.access, field.name, field.descriptor.descriptor,
                field.signature?.signature, field.value?.toASM())
                ?.let { writeField(field, it) }
        }
        file.recordFields.forEach { field ->
            visitor.visitRecordComponent(field.name, field.descriptor.descriptor, field.signature?.signature)
                ?.let { writeRecordField(field, it) }
        }
        visitor.visitEnd()
    }

    private fun writeMethod(method: ClassMethod, visitor: MethodVisitor) {
        method.parameters.forEach { visitor.visitParameter(it.name, it.access) }
        method.annotationDefault?.let { value ->
            visitor.visitAnnotationDefault()?.let { writeAnnotationValue(null, value, it) }
        }
        writeAnnotations(method.visibleAnnotations, true, visitor::visitAnnotation)
        writeAnnotations(method.invisibleAnnotations, false, visitor::visitAnnotation)
        writeTypeAnnotations(method.visibleTypeAnnotations, true, visitor::visitTypeAnnotation)
        writeTypeAnnotations(method.invisibleTypeAnnotations, false, visitor::visitTypeAnnotation)
        visitor.visitAnnotableParameterCount(method.visibleParameterAnnotations.size, true)
        method.visibleParameterAnnotations.forEachIndexed { index, annotations ->
            annotations?.forEach { annotation ->
                visitor.visitParameterAnnotation(index, annotation.annotationClass.asTypeDescriptor().descriptor, true)
                    ?.let { writeAnnotation(annotation, it) }
            }
        }
        visitor.visitAnnotableParameterCount(method.invisibleParameterAnnotations.size, false)
        method.invisibleParameterAnnotations.forEachIndexed { index, annotations ->
            annotations?.forEach { annotation ->
                visitor.visitParameterAnnotation(index, annotation.annotationClass.asTypeDescriptor().descriptor, false)
                    ?.let { writeAnnotation(annotation, it) }
            }
        }
        method.classCode?.let { code ->
            visitor.visitCode()
            for (insn in code.instructions) {
                insn.frame?.let { frame ->
                    when (frame) {
                        is AppendFrame -> visitor.visitFrame(F_FULL,
                            frame.locals.size,
                            frame.locals.mapToArray { it.toASM() },
                            0,
                            null)
                        is ChopFrame -> visitor.visitFrame(F_CHOP, frame.locals, null, 0, null)
                        is FullFrame -> visitor.visitFrame(F_FULL,
                            frame.locals.size,
                            frame.locals.mapToArray { it.toASM() },
                            frame.stacks.size,
                            frame.stacks.mapToArray { it.toASM() })
                        is Same1Frame -> visitor.visitFrame(F_SAME1, 0, null, 1, arrayOf(frame.stack.toASM()))
                        SameFrame -> visitor.visitFrame(F_SAME, 0, null, 0, null)
                    }
                }
                insn.labelsToMe.forEach { visitor.visitLabel(labelMap.map(it)) }
                if (insn.lineNumber != -1) {
                    val label = insn.labelsToMe.firstOrNull()?.let(labelMap::map)
                        ?: ASMLabel().apply(visitor::visitLabel)
                    visitor.visitLineNumber(insn.lineNumber, label)
                }
                when (insn) {
                    is CastInsn -> visitor.visitInsn(Insns.casts[insn.from to insn.to]!!)
                    is FieldInsn -> visitor.visitFieldInsn(Insns.fieldInsns.indexOf(insn.insn) + GETSTATIC,
                        insn.field.owner.name,
                        insn.field.name,
                        insn.field.descriptor.descriptor)
                    is IIncInsn -> visitor.visitIincInsn(insn.variable, insn.value)
                    is InvokeDynamicInsn -> {
                        val dynamic = insn.target.toASM()
                        visitor.visitInvokeDynamicInsn(dynamic.name, dynamic.descriptor, dynamic.bootstrapMethod,
                            *Array(dynamic.bootstrapMethodArgumentCount) { dynamic.getBootstrapMethodArgument(it) })
                    }
                    is JumpInsn -> when (insn.insn) {
                        JumpInsnType.IFNULL -> visitor.visitJumpInsn(IFNULL, labelMap.map(insn.target))
                        JumpInsnType.IFNONNULL -> visitor.visitJumpInsn(IFNONNULL, labelMap.map(insn.target))
                        else -> visitor.visitJumpInsn(Insns.jumpInsns.indexOf(insn.insn) + IFEQ,
                            labelMap.map(insn.target))
                    }
                    is LdcInsn -> visitor.visitLdcInsn(insn.value.toASM())
                    is LookupSwitchInsn -> {
                        val entries = insn.labels.entries.toList()
                        visitor.visitLookupSwitchInsn(labelMap.map(insn.default),
                            entries.mapToIntArray { it.key },
                            entries.mapToArray { labelMap.map(it.value) })
                    }
                    is MethodInsn -> visitor.visitMethodInsn(Insns.methodInsns.indexOf(insn.insn) + INVOKEVIRTUAL,
                        insn.method.owner.name,
                        insn.method.name,
                        insn.method.descriptor.descriptor,
                        insn.isInterface)
                    is MultiANewArrayInsn -> visitor.visitMultiANewArrayInsn(insn.type.name, insn.dimensions)
                    is RetInsn -> visitor.visitVarInsn(RET, insn.variable)
                    is SimpleInsn -> visitor.visitInsn(Insns.simpleInsns[insn.insn]!!)
                    is TableSwitchInsn -> visitor.visitTableSwitchInsn(insn.min, insn.min + insn.labels.size - 1,
                        labelMap.map(insn.default), *insn.labels.mapToArray { labelMap.map(it) })
                    is TypeInsn -> when(insn.insn) {
                        TypeInsnType.NEW -> visitor.visitTypeInsn(NEW, insn.type.name)
                        TypeInsnType.ANEWARRAY -> visitor.visitTypeInsn(ANEWARRAY, insn.type.name)
                        TypeInsnType.CHECKCAST -> visitor.visitTypeInsn(CHECKCAST, insn.type.name)
                        TypeInsnType.INSTANCEOF -> visitor.visitTypeInsn(INSTANCEOF, insn.type.name)
                    }
                    is TypedInsn -> {
                        when (insn.insn) {
                            TypedInsnType.ALOAD -> visitor.visitInsn(Insns.typedInsnDiffs[insn.type]!! + IALOAD)
                            TypedInsnType.ASTORE -> visitor.visitInsn(Insns.typedInsnDiffs[insn.type]!! + IASTORE)
                            TypedInsnType.RETURN -> visitor.visitInsn(Insns.typedInsnDiffs[insn.type]!! + IRETURN)
                            TypedInsnType.NEWARRAY ->
                                visitor.visitIntInsn(NEWARRAY, Insns.newArrayTypeMapping.inverse()[insn.type]!!)
                            TypedInsnType.ADD, TypedInsnType.SUB, TypedInsnType.MUL, TypedInsnType.DIV,
                            TypedInsnType.REM, TypedInsnType.NEG, TypedInsnType.SHL, TypedInsnType.SHR,
                            TypedInsnType.USHR, TypedInsnType.AND, TypedInsnType.OR, TypedInsnType.XOR ->
                                visitor.visitInsn(Insns.typedOperatorInsnMapping[insn.insn to insn.type]!!)
                        }
                    }
                    is VarInsn -> {
                        val diff = Insns.typedInsnDiffs[insn.type]!!
                        when (insn.insn) {
                            VarInsnType.LOAD -> visitor.visitVarInsn(ILOAD + diff, insn.variable)
                            VarInsnType.STORE -> visitor.visitVarInsn(ISTORE + diff, insn.variable)
                        }
                    }
                }
                // after
                writeTypeAnnotations(insn.visibleAnnotations, true, visitor::visitInsnAnnotation)
                writeTypeAnnotations(insn.invisibleAnnotations, false, visitor::visitInsnAnnotation)
            }
            code.tryCatchBlocks
            code.localVariables
            code.visibleLocalVariableAnnotations
            code.invisibleLocalVariableAnnotations
            visitor.visitMaxs(code.maxStack, code.maxLocals)
        }
        visitor.visitEnd()
    }

    private fun writeField(method: ClassField, visitor: FieldVisitor) {
        writeAnnotations(method.visibleAnnotations, true, visitor::visitAnnotation)
        writeAnnotations(method.invisibleAnnotations, false, visitor::visitAnnotation)
        writeTypeAnnotations(method.visibleTypeAnnotations, true, visitor::visitTypeAnnotation)
        writeTypeAnnotations(method.invisibleTypeAnnotations, false, visitor::visitTypeAnnotation)
        visitor.visitEnd()
    }

    private fun writeRecordField(method: ClassRecordField, visitor: RecordComponentVisitor) {
        writeAnnotations(method.visibleAnnotations, true, visitor::visitAnnotation)
        writeAnnotations(method.invisibleAnnotations, false, visitor::visitAnnotation)
        writeTypeAnnotations(method.visibleTypeAnnotations, true, visitor::visitTypeAnnotation)
        writeTypeAnnotations(method.invisibleTypeAnnotations, false, visitor::visitTypeAnnotation)
        visitor.visitEnd()
    }

    private fun writeAnnotation(annotation: ClassAnnotation, visitor: AnnotationVisitor) {
        for (value in annotation.values) {
            writeAnnotationValue(value.key, value.value, visitor)
        }
        visitor.visitEnd()
    }

    private fun writeAnnotationValue(name: String?, value: AnnotationValue, visitor: AnnotationVisitor) {
        when (value) {
            is AnnotationArray -> visitor.visitArray(name)?.let { arrayVisitor ->
                value.forEach { writeAnnotationValue(null, it, arrayVisitor) }
                arrayVisitor.visitEnd()
            }
            is AnnotationBoolean -> visitor.visit(name, value.value)
            is AnnotationByte -> visitor.visit(name, value.value)
            is AnnotationChar -> visitor.visit(name, value.value)
            is AnnotationDouble -> visitor.visit(name, value.value)
            is AnnotationFloat -> visitor.visit(name, value.value)
            is AnnotationInt -> visitor.visit(name, value.value)
            is AnnotationLong -> visitor.visit(name, value.value)
            is AnnotationShort -> visitor.visit(name, value.value)
            is AnnotationString -> visitor.visit(name, value.value)
            is AnnotationClass -> visitor.visit(name, Type.getType(value.descriptor.descriptor))
            is AnnotationEnum -> visitor.visitEnum(name, value.owner.asTypeDescriptor().descriptor, value.value)
            is ClassAnnotation -> visitor.visitAnnotation(name, value.annotationClass.asTypeDescriptor().descriptor)
                ?.let { writeAnnotation(value, it) }
        }
    }

    // utils

    private class LabelMap() {
        private val map = mutableMapOf<CodeLabel, ASMLabel>()
        fun map(label: CodeLabel): ASMLabel = map.computeIfAbsent(label) { ASMLabel() }
    }

    private inline fun writeAnnotations(
        annotations: List<ClassAnnotation>,
        visible: Boolean,
        visitAnnotation: (String, Boolean) -> AnnotationVisitor?,
    ) {
        for (annotation in annotations)
            visitAnnotation(annotation.annotationClass.asTypeDescriptor().descriptor, visible)
                ?.let { writeAnnotation(annotation, it) }
    }

    private inline fun writeTypeAnnotations(
        annotations: List<ClassTypeAnnotation>,
        visible: Boolean,
        visitAnnotation: (Int, ASMTypePath?, String, Boolean) -> AnnotationVisitor?,
    ) {
        for (annotation in annotations)
            visitAnnotation(annotation.type.value, annotation.typePath?.toASM(),
                annotation.annotation.annotationClass.asTypeDescriptor().descriptor, visible)
                ?.let { writeAnnotation(annotation.annotation, it) }
    }

    private fun TypePath.toASM(): ASMTypePath = ASMTypePath.fromString(toString())

    private fun FrameElement.toASM(): Any = when (this) {
        FrameElement.Top -> TOP
        FrameElement.Integer -> INTEGER
        FrameElement.Float -> FLOAT
        FrameElement.Long -> LONG
        FrameElement.Double -> DOUBLE
        FrameElement.Null -> NULL
        FrameElement.UninitializedThis -> UNINITIALIZED_THIS
        is FrameElement.Reference -> type.name
        is FrameElement.Uninitialized -> labelMap.map(at)
    }

    private fun Constant.toASM(): Any = when (this) {
        is ConstantClass -> Type.getType(descriptor.descriptor)
        is ConstantDouble -> value
        is ConstantDynamic -> toASM()
        is ConstantFloat -> value
        is ConstantHandle -> toASM()
        is ConstantInt -> value
        is ConstantLong -> value
        is ConstantMethodType -> Type.getType(descriptor.descriptor)
        is ConstantString -> value
    }

    private fun ConstantDynamic.toASM(): ASMConstantDynamic =
        ASMConstantDynamic(name, descriptor.descriptor, bootstrapMethod.toASM(), *args.mapToArray { it.toASM() })

    private fun ConstantHandle.toASM(): ASMHandle = when (this) {
        is ConstantFieldHandle -> {
            val tag = when (type) {
                FieldHandleType.GETFIELD -> H_GETFIELD
                FieldHandleType.GETSTATIC -> H_GETSTATIC
                FieldHandleType.PUTFIELD -> H_PUTFIELD
                FieldHandleType.PUTSTATIC -> H_PUTSTATIC
            }
            ASMHandle(tag, field.owner.name, field.name, field.descriptor.descriptor, false)
        }
        is ConstantMethodHandle -> {
            val tag = when (type) {
                MethodHandleType.INVOKEVIRTUAL -> H_INVOKEVIRTUAL
                MethodHandleType.INVOKESTATIC -> H_INVOKESTATIC
                MethodHandleType.INVOKESPECIAL -> H_INVOKESPECIAL
                MethodHandleType.NEWINVOKESPECIAL -> H_NEWINVOKESPECIAL
                MethodHandleType.INVOKEINTERFACE -> H_INVOKEINTERFACE
            }
            ASMHandle(tag, method.owner.name, method.name, method.descriptor.descriptor, isInterface)
        }
    }
}
