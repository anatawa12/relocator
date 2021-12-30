package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.relocation.ClassRelocator
import com.anatawa12.relocator.relocation.RelocationMapping

class SimpleClassRelocator(
    val mapping: RelocationMapping
) : ClassRelocator {
    override fun relocate(classFile: ClassFile) {
        classFile.name.let(mapping::mapClass)?.let { classFile.name = it }
        classFile.signature?.let(mapping::mapClassSignature)?.let { classFile.signature = it }
        classFile.superName?.let(mapping::mapClassRef)?.let { classFile.superName = it }
        classFile.interfaces.replaceAll { mapping.mapClassRef(it) ?: it }
        classFile.outerClass?.let(mapping::mapClassRef)?.let { classFile.outerClass = it }
        classFile.outerMethodDesc?.let(mapping::mapMethodDescriptor)?.let { classFile.outerMethodDesc = it }
        classFile.visibleAnnotations.onEach(::relocateAnnotation)
        classFile.invisibleAnnotations.onEach(::relocateAnnotation)
        classFile.visibleTypeAnnotations.onEach(::relocateTypeAnnotation)
        classFile.invisibleTypeAnnotations.onEach(::relocateTypeAnnotation)
        classFile.innerClasses.onEach { innerClass ->
            innerClass.name.let(mapping::mapClassRef)?.let { innerClass.name = it }
            innerClass.outerName?.let(mapping::mapClassRef)?.let { innerClass.outerName = it }
        }
        classFile.nestHostClass?.let(mapping::mapClassRef)?.let { classFile.nestHostClass = it }
        classFile.nestMembers.replaceAll { mapping.mapClassRef(it) ?: it }
        classFile.permittedSubclasses.replaceAll { mapping.mapClassRef(it) ?: it }
    }

    override fun relocate(method: ClassMethod) {
        method.descriptor.let(mapping::mapMethodDescriptor)?.let { method.descriptor = it }
        method.signature?.let(mapping::mapMethodSignature)?.let { method.signature = it }
        method.exceptions.replaceAll { mapping.mapClassRef(it) ?: it }
        method.visibleAnnotations.onEach(::relocateAnnotation)
        method.invisibleAnnotations.onEach(::relocateAnnotation)
        method.visibleTypeAnnotations.onEach(::relocateTypeAnnotation)
        method.invisibleTypeAnnotations.onEach(::relocateTypeAnnotation)
        method.annotationDefault?.let(::relocateAnnotationValue)
        method.visibleParameterAnnotations.onEach { l -> l?.onEach { relocateAnnotation(it) } }
        method.invisibleParameterAnnotations.onEach { l -> l?.onEach { relocateAnnotation(it) } }
        method.classCode?.let(::relocateCode)
    }

    private fun relocateCode(code: ClassCode) {
        code.instructions.onEach(::relocateInsn)
        code.tryCatchBlocks.map { catch ->
            catch.type?.let(mapping::mapClassRef)?.let { catch.type = it }
            catch.visibleAnnotations.onEach(::relocateTypeAnnotation)
            catch.invisibleAnnotations.onEach(::relocateTypeAnnotation)
        }
        code.localVariables.onEach { variable ->
            variable.descriptor.let(mapping::mapTypeDescriptor)?.let { variable.descriptor = it }
            variable.signature?.let(mapping::mapTypeSignature)?.let { variable.signature = it }
        }
        code.visibleLocalVariableAnnotations.onEach { ann ->
            relocateAnnotation(ann.annotation)
        }
        code.invisibleLocalVariableAnnotations.onEach { ann ->
            relocateAnnotation(ann.annotation)
        }
    }

    private fun relocateInsn(insn: Insn) {
        when (val frame = insn.frame) {
            is AppendFrame -> mapList(frame.locals, ::mapFrameElement)?.let(::AppendFrame)
            is Same1Frame -> mapFrameElement(frame.stack)?.let(::Same1Frame)
            is FullFrame -> {
                val locals = mapList(frame.locals, ::mapFrameElement)
                val stacks = mapList(frame.stacks, ::mapFrameElement)
                if (locals == null && stacks == null) null
                else FullFrame(locals ?: frame.locals, stacks ?: frame.stacks)
            }
            is ChopFrame -> null
            SameFrame -> null
            null -> null
        }?.let { insn.frame = it }
        insn.visibleAnnotations.map(::relocateTypeAnnotation)
        insn.invisibleAnnotations.map(::relocateTypeAnnotation)
        when (insn) {
            is SimpleInsn -> Unit
            is TypedInsn -> Unit
            is CastInsn -> Unit
            is VarInsn -> Unit
            is RetInsn -> Unit
            is TypeInsn -> mapping.mapClassRef(insn.type)?.let { insn.type = it }
            is FieldInsn -> mapping.mapFieldRef(insn.field)?.let { insn.field = it }
            is MethodInsn -> mapping.mapMethodRef(insn.method)?.let { insn.method = it }
            is InvokeDynamicInsn -> mapping.mapConstantDynamic(insn.target)?.let { insn.target = it }
            is JumpInsn -> Unit
            is LdcInsn -> mapping.mapConstant(insn.value)?.let { insn.value = it }
            is IIncInsn -> Unit
            is TableSwitchInsn -> Unit
            is LookupSwitchInsn -> Unit
            is MultiANewArrayInsn -> mapping.mapClassRef(insn.type)?.let { insn.type = it }
        }
    }

    private fun mapFrameElement(frameElement: FrameElement): FrameElement? = when (frameElement) {
        is FrameElement.Reference -> mapping.mapClassRef(frameElement.type)?.let(FrameElement::Reference)
        else -> null
    }

    override fun relocate(field: ClassField) {
        field.descriptor.let(mapping::mapTypeDescriptor)?.let { field.descriptor = it }
        field.signature?.let(mapping::mapTypeSignature)?.let { field.signature = it }
        field.value?.let(mapping::mapConstant)?.let { field.value = it }
        field.visibleAnnotations.onEach(::relocateAnnotation)
        field.invisibleAnnotations.onEach(::relocateAnnotation)
        field.visibleTypeAnnotations.onEach(::relocateTypeAnnotation)
        field.invisibleTypeAnnotations.onEach(::relocateTypeAnnotation)
    }

    override fun relocate(recordField: ClassRecordField) {
        recordField.descriptor.let(mapping::mapTypeDescriptor)?.let { recordField.descriptor = it }
        recordField.signature?.let(mapping::mapTypeSignature)?.let { recordField.signature = it }
        recordField.visibleAnnotations.onEach(::relocateAnnotation)
        recordField.invisibleAnnotations.onEach(::relocateAnnotation)
        recordField.visibleTypeAnnotations.onEach(::relocateTypeAnnotation)
        recordField.invisibleTypeAnnotations.onEach(::relocateTypeAnnotation)
    }

    private fun relocateAnnotationValue(value: AnnotationValue) {
        when (value) {
            is AnnotationArray -> value.values.onEach(::relocateAnnotationValue)
            is AnnotationBoolean -> Unit
            is AnnotationByte -> Unit
            is AnnotationChar -> Unit
            is AnnotationDouble -> Unit
            is AnnotationFloat -> Unit
            is AnnotationInt -> Unit
            is AnnotationLong -> Unit
            is AnnotationShort -> Unit
            is AnnotationString -> Unit
            is AnnotationClass -> mapping.mapTypeDescriptor(value.descriptor)?.let { value.descriptor = it }
            is AnnotationEnum -> mapping.mapClassRef(value.owner)?.let { value.owner = it }
            is ClassAnnotation -> relocateAnnotation(value)
        }
    }

    private fun relocateAnnotation(annotation: ClassAnnotation) {
        mapping.mapClassRef(annotation.annotationClass)?.let { annotation.annotationClass = it }
        annotation.values.onEach { relocateAnnotationValue(it.value) }
    }

    private fun relocateTypeAnnotation(annotation: ClassTypeAnnotation) {
        relocateAnnotation(annotation.annotation)
    }
}
