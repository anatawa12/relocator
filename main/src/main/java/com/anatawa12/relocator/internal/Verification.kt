@file:Suppress("SameParameterValue", "SpellCheckingInspection", "GrazieInspection")

package com.anatawa12.relocator.internal

import com.anatawa12.relocator.internal.Verifications.checkAccess
import com.anatawa12.relocator.internal.Verifications.checkClassSignature
import com.anatawa12.relocator.internal.Verifications.checkConstant
import com.anatawa12.relocator.internal.Verifications.checkDescriptor
import com.anatawa12.relocator.internal.Verifications.checkFieldSignature
import com.anatawa12.relocator.internal.Verifications.checkFullyQualifiedName
import com.anatawa12.relocator.internal.Verifications.checkIdentifier
import com.anatawa12.relocator.internal.Verifications.checkInternalName
import com.anatawa12.relocator.internal.Verifications.checkMethodAccess
import com.anatawa12.relocator.internal.Verifications.checkMethodDescriptor
import com.anatawa12.relocator.internal.Verifications.checkMethodIdentifier
import com.anatawa12.relocator.internal.Verifications.checkMethodSignature
import com.anatawa12.relocator.internal.Verifications.checkOpcodeMethod
import com.anatawa12.relocator.internal.Verifications.checkSignedByte
import com.anatawa12.relocator.internal.Verifications.checkSignedShort
import com.anatawa12.relocator.internal.Verifications.checkTypeRef
import com.anatawa12.relocator.internal.Verifications.checkUnqualifiedName
import com.anatawa12.relocator.internal.Verifications.checkUnsignedShort
import com.anatawa12.relocator.internal.Verifications.packageName
import org.objectweb.asm.*

// all classes in this file is based on asm-util by OW2 Consortium
// Modifications overview:
// - static analyze functions are removed
// - static verification helpers are moved to top-level
// - checkDataFlow is removed
// - unused functions are removed
// - allow some extra flags
// 
// ====================================================================
// ========================= ORIGINAL LICENSE =========================
// ====================================================================
// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.

internal class CheckAnnotationAdapter internal constructor(
    annotationVisitor: AnnotationVisitor?,
    private val useNamedValue: Boolean,
) : AnnotationVisitor(Opcodes.ASM9, annotationVisitor) {
    private var visitEndCalled = false

    constructor(annotationVisitor: AnnotationVisitor?) : this(annotationVisitor, true)

    override fun visit(name: String?, value: Any) {
        checkVisitEndNotCalled()
        checkName(name)
        require(value is Byte || value is Boolean || value is Char || value is Short || value is Int
                || value is Long || value is Float || value is Double || value is String || value is Type
                || value is ByteArray || value is BooleanArray || value is CharArray || value is ShortArray
                || value is IntArray || value is LongArray || value is FloatArray || value is DoubleArray) {
            "Invalid annotation value"
        }
        require(!(value is Type && value.sort == Type.METHOD)) { "Invalid annotation value" }
        super.visit(name, value)
    }

    override fun visitEnum(name: String?, descriptor: String, value: String) {
        checkVisitEndNotCalled()
        checkName(name)
        // Annotations can only appear in V1_5 or more classes.
        checkDescriptor(Opcodes.V1_5, descriptor, false)
        super.visitEnum(name, descriptor, value)
    }

    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
        checkVisitEndNotCalled()
        checkName(name)
        // Annotations can only appear in V1_5 or more classes.
        checkDescriptor(Opcodes.V1_5, descriptor, false)
        return CheckAnnotationAdapter(super.visitAnnotation(name, descriptor))
    }

    override fun visitArray(name: String?): AnnotationVisitor {
        checkVisitEndNotCalled()
        checkName(name)
        return CheckAnnotationAdapter(super.visitArray(name), false)
    }

    override fun visitEnd() {
        checkVisitEndNotCalled()
        visitEndCalled = true
        super.visitEnd()
    }

    private fun checkName(name: String?) {
        if (useNamedValue) requireNotNull(name) { "Annotation value name must not be null" }
    }

    private fun checkVisitEndNotCalled() {
        check(!visitEndCalled) { "Cannot call a visit method after visitEnd has been called" }
    }
}

internal class CheckClassAdapter constructor(classVisitor: ClassVisitor?) : ClassVisitor(Opcodes.ASM9, classVisitor) {
    private var version = 0
    private var visitCalled = false
    private var visitModuleCalled = false
    private var visitSourceCalled = false
    private var visitOuterClassCalled = false
    private var visitNestHostCalled = false

    /**
     * The common package of all the nest members. Not null if the visitNestMember method
     * has been called.
     */
    private var nestMemberPackageName: String? = null

    private var visitEndCalled = false

    /** The index of the instruction designated by each visited label so far.  */
    private val labelInsnIndices: MutableMap<Label, Int> = HashMap()

    // -----------------------------------------------------------------------------------------------
    // Implementation of the ClassVisitor interface
    // -----------------------------------------------------------------------------------------------
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?,
    ) {
        if (visitCalled) {
            throw IllegalStateException("visit must be called only once")
        }
        visitCalled = true
        checkState()
        checkAccess(access, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
                or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_ANNOTATION
                or Opcodes.ACC_ENUM or Opcodes.ACC_DEPRECATED or Opcodes.ACC_RECORD or Opcodes.ACC_MODULE)
        if (!name.endsWith("package-info") && !name.endsWith("module-info")) {
            checkInternalName(version, name, "class name")
        }
        if ("java/lang/Object" == name) {
            require(superName == null) { "The super class name of the Object class must be 'null'" }
        } else if (name.endsWith("module-info")) {
            require(superName == null) { "The super class name of a module-info class must be 'null'" }
        } else {
            require(superName != null) { "Invalid super class name (must not be null or empty)" }
            checkInternalName(version, superName, "super class name")
        }
        if (signature != null) {
            checkClassSignature(signature)
        }
        if ((access and Opcodes.ACC_INTERFACE) != 0) {
            require("java/lang/Object" == superName) { "The super class name of interfaces must be 'java/lang/Object'" }
        }
        if (interfaces != null) {
            for (i in interfaces.indices) {
                checkInternalName(
                    version, interfaces[i], "interface name at index $i")
            }
        }
        this.version = version
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitSource(file: String?, debug: String?) {
        checkState()
        if (visitSourceCalled) {
            throw IllegalStateException("visitSource can be called only once.")
        }
        visitSourceCalled = true
        super.visitSource(file, debug)
    }

    override fun visitModule(name: String, access: Int, version: String): ModuleVisitor {
        checkState()
        if (visitModuleCalled) {
            throw IllegalStateException("visitModule can be called only once.")
        }
        visitModuleCalled = true
        checkFullyQualifiedName(this.version, name, "module name")
        checkAccess(access, Opcodes.ACC_OPEN or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED)
        val checkModuleAdapter = CheckModuleAdapter(
            api, super.visitModule(name, access, version), (access and Opcodes.ACC_OPEN) != 0)
        checkModuleAdapter.classVersion = this.version
        return checkModuleAdapter
    }

    override fun visitNestHost(nestHost: String) {
        checkState()
        checkInternalName(version, nestHost, "nestHost")
        if (visitNestHostCalled) {
            throw IllegalStateException("visitNestHost can be called only once.")
        }
        if (nestMemberPackageName != null) {
            throw IllegalStateException("visitNestHost and visitNestMember are mutually exclusive.")
        }
        visitNestHostCalled = true
        super.visitNestHost(nestHost)
    }

    override fun visitNestMember(nestMember: String) {
        checkState()
        checkInternalName(version, nestMember, "nestMember")
        if (visitNestHostCalled) {
            throw IllegalStateException(
                "visitMemberOfNest and visitNestHost are mutually exclusive.")
        }
        val packageName = packageName(nestMember)
        if (nestMemberPackageName == null) {
            nestMemberPackageName = packageName
        } else if (nestMemberPackageName != packageName) {
            throw IllegalStateException(
                "nest member $nestMember should be in the package $nestMemberPackageName")
        }
        super.visitNestMember(nestMember)
    }

    override fun visitPermittedSubclass(permittedSubclass: String) {
        checkState()
        checkInternalName(version, permittedSubclass, "permittedSubclass")
        super.visitPermittedSubclass(permittedSubclass)
    }

    override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
        checkState()
        if (visitOuterClassCalled) {
            throw IllegalStateException("visitOuterClass can be called only once.")
        }
        visitOuterClassCalled = true
        if (descriptor != null) {
            checkMethodDescriptor(version, descriptor)
        }
        super.visitOuterClass(owner, name, descriptor)
    }

    override fun visitInnerClass(
        name: String, outerName: String?, innerName: String?, access: Int,
    ) {
        checkState()
        checkInternalName(version, name, "class name")
        if (outerName != null) {
            checkInternalName(version, outerName, "outer class name")
        }
        if (innerName != null) {
            var startIndex = 0
            while (startIndex < innerName.length && innerName[startIndex].isDigit()) {
                startIndex++
            }
            if (startIndex == 0 || startIndex < innerName.length) {
                checkIdentifier(version, innerName, startIndex, -1, "inner class name")
            }
        }
        checkAccess(access, Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED
                or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER or Opcodes.ACC_INTERFACE
                or Opcodes.ACC_ABSTRACT or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_ANNOTATION or Opcodes.ACC_ENUM)
        super.visitInnerClass(name, outerName, innerName, access)
    }

    override fun visitRecordComponent(
        name: String, descriptor: String, signature: String?,
    ): RecordComponentVisitor {
        checkState()
        checkUnqualifiedName(version, name, "record component name")
        checkDescriptor(version, descriptor,  /* canBeVoid = */false)
        if (signature != null) {
            checkFieldSignature(signature)
        }
        return CheckRecordComponentAdapter(
            api, super.visitRecordComponent(name, descriptor, signature))
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor {
        checkState()
        checkAccess(access, Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED
                or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_VOLATILE or Opcodes.ACC_TRANSIENT
                or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_ENUM or Opcodes.ACC_MANDATED or Opcodes.ACC_DEPRECATED)
        checkUnqualifiedName(version, name, "field name")
        checkDescriptor(version, descriptor,  /* canBeVoid = */false)
        if (signature != null) {
            checkFieldSignature(signature)
        }
        if (value != null) {
            checkConstant(value)
        }
        return CheckFieldAdapter(api,
            super.visitField(access, name, descriptor, signature, value))
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor {
        checkState()
        checkMethodAccess(version, access, Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE
                or Opcodes.ACC_PROTECTED or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNCHRONIZED
                or Opcodes.ACC_BRIDGE or Opcodes.ACC_VARARGS or Opcodes.ACC_NATIVE or Opcodes.ACC_ABSTRACT
                or Opcodes.ACC_STRICT or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED or Opcodes.ACC_DEPRECATED)
        if ("<init>" != name && "<clinit>" != name) {
            checkMethodIdentifier(version, name, "method name")
        }
        checkMethodDescriptor(version, descriptor)
        if (signature != null) {
            checkMethodSignature(signature)
        }
        if (exceptions != null) {
            for (i in exceptions.indices) {
                checkInternalName(
                    version, exceptions[i], "exception name at index $i")
            }
        }
        val checkMethodAdapter = CheckMethodAdapter(
            api,
            super.visitMethod(access, name, descriptor, signature, exceptions),
            labelInsnIndices)
        checkMethodAdapter.version = version
        return checkMethodAdapter
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        checkState()
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitAnnotation(descriptor, visible))
    }

    override fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkState()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.CLASS_TYPE_PARAMETER || sort == TypeReference.CLASS_TYPE_PARAMETER_BOUND
                || sort == TypeReference.CLASS_EXTENDS) { "Invalid type reference sort 0x${Integer.toHexString(sort)}" }
        checkTypeRef(typeRef)
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitTypeAnnotation(typeRef, typePath, descriptor, visible))
    }

    override fun visitAttribute(attribute: Attribute) {
        checkState()
        super.visitAttribute(attribute)
    }

    override fun visitEnd() {
        checkState()
        visitEndCalled = true
        super.visitEnd()
    }

    // -----------------------------------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------------------------------
    /** Checks that the visit method has been called and that visitEnd has not been called.  */
    private fun checkState() {
        if (!visitCalled) {
            throw IllegalStateException("Cannot visit member before visit has been called.")
        }
        if (visitEndCalled) {
            throw IllegalStateException("Cannot visit member after visitEnd has been called.")
        }
    }
}

internal class CheckFieldAdapter internal constructor(api: Int, fieldVisitor: FieldVisitor?) : FieldVisitor(api, fieldVisitor) {
    private var visitEndCalled = false

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        checkVisitEndNotCalled()
        // Annotations can only appear in V1_5 or more classes.
        checkDescriptor(Opcodes.V1_5, descriptor, false)
        return CheckAnnotationAdapter(super.visitAnnotation(descriptor, visible))
    }

    @Suppress("DuplicatedCode")
    override fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkVisitEndNotCalled()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.FIELD) { "Invalid type reference sort 0x${Integer.toHexString(sort)}" }
        checkTypeRef(typeRef)
        checkDescriptor(Opcodes.V1_5, descriptor, false)
        return CheckAnnotationAdapter(super.visitTypeAnnotation(typeRef, typePath, descriptor, visible))
    }

    override fun visitAttribute(attribute: Attribute) {
        checkVisitEndNotCalled()
        super.visitAttribute(attribute)
    }

    override fun visitEnd() {
        checkVisitEndNotCalled()
        visitEndCalled = true
        super.visitEnd()
    }

    private fun checkVisitEndNotCalled() {
        check(!visitEndCalled) { "Cannot call a visit method after visitEnd has been called" }
    }
}

internal class CheckMethodAdapter internal constructor(
    api: Int,
    methodVisitor: MethodVisitor?,
    /** The index of the instruction designated by each visited label.  */
    private val labelInsnIndices: MutableMap<Label, Int>,
) : MethodVisitor(api, methodVisitor) {
    var version = 0

    private var access = 0
    private var visibleAnnotableParameterCount = 0
    private var invisibleAnnotableParameterCount = 0
    private var visitCodeCalled = false
    private var visitMaxCalled = false
    private var visitEndCalled = false

    /** The number of visited instructions so far.  */
    private var insnCount = 0

    /** The labels referenced by the visited method.  */
    private val referencedLabels: MutableSet<Label> = HashSet()

    /** The index of the instruction corresponding to the last visited stack map frame.  */
    private var lastFrameInsnIndex = -1

    /** The number of visited frames in expanded form.  */
    private var numExpandedFrames = 0

    /** The number of visited frames in compressed form.  */
    private var numCompressedFrames = 0

    /**
     * The exception handler ranges. Each pair of list element contains the start and end labels of an
     * exception handler block.
     */
    private val handlers: MutableList<Label> = ArrayList()

    override fun visitParameter(name: String, access: Int) {
        checkUnqualifiedName(version, name, "name")
        checkAccess(access, Opcodes.ACC_FINAL or Opcodes.ACC_MANDATED or Opcodes.ACC_SYNTHETIC)
        super.visitParameter(name, access)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        checkVisitEndNotCalled()
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitAnnotation(descriptor, visible))
    }

    override fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkVisitEndNotCalled()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.METHOD_TYPE_PARAMETER || sort == TypeReference.METHOD_TYPE_PARAMETER_BOUND
                || sort == TypeReference.METHOD_RETURN || sort == TypeReference.METHOD_RECEIVER
                || sort == TypeReference.METHOD_FORMAL_PARAMETER || sort == TypeReference.THROWS) {
            "Invalid type reference sort 0x${Integer.toHexString(sort)}"
        }
        checkTypeRef(typeRef)
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitTypeAnnotation(typeRef, typePath, descriptor, visible))
    }

    override fun visitAnnotationDefault(): AnnotationVisitor {
        checkVisitEndNotCalled()
        return CheckAnnotationAdapter(super.visitAnnotationDefault(), false)
    }

    override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
        checkVisitEndNotCalled()
        if (visible) {
            visibleAnnotableParameterCount = parameterCount
        } else {
            invisibleAnnotableParameterCount = parameterCount
        }
        super.visitAnnotableParameterCount(parameterCount, visible)
    }

    override fun visitParameterAnnotation(
        parameter: Int, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkVisitEndNotCalled()
        if (visible) {
            require(visibleAnnotableParameterCount <= 0 || parameter < visibleAnnotableParameterCount) {
                "Invalid parameter index"
            }
        } else {
            require(invisibleAnnotableParameterCount <= 0 || parameter < invisibleAnnotableParameterCount) {
                "Invalid parameter index"
            }
        }
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitParameterAnnotation(parameter, descriptor, visible))
    }

    override fun visitAttribute(attribute: Attribute) {
        checkVisitEndNotCalled()
        super.visitAttribute(attribute)
    }

    override fun visitCode() {
        if (access and Opcodes.ACC_ABSTRACT != 0) {
            throw UnsupportedOperationException("Abstract methods cannot have code")
        }
        visitCodeCalled = true
        super.visitCode()
    }

    override fun visitFrame(
        type: Int,
        numLocal: Int,
        local: Array<Any>?,
        numStack: Int,
        stack: Array<Any>?,
    ) {
        if (insnCount == lastFrameInsnIndex) {
            throw IllegalStateException("At most one frame can be visited at a given code location.")
        }
        lastFrameInsnIndex = insnCount
        val maxNumLocal: Int
        val maxNumStack: Int
        when (type) {
            Opcodes.F_NEW, Opcodes.F_FULL -> {
                maxNumLocal = Int.MAX_VALUE
                maxNumStack = Int.MAX_VALUE
            }
            Opcodes.F_SAME -> {
                maxNumLocal = 0
                maxNumStack = 0
            }
            Opcodes.F_SAME1 -> {
                maxNumLocal = 0
                maxNumStack = 1
            }
            Opcodes.F_APPEND, Opcodes.F_CHOP -> {
                maxNumLocal = 3
                maxNumStack = 0
            }
            else -> throw IllegalArgumentException("Invalid frame type $type")
        }
        require(numLocal <= maxNumLocal) { "Invalid numLocal=$numLocal for frame type $type" }
        require(numStack <= maxNumStack) { "Invalid numStack=$numStack for frame type $type" }
        if (type != Opcodes.F_CHOP) {
            if (numLocal > 0) {
                require(local != null && local.size >= numLocal) { "Array local[] is shorter than numLocal" }
                for (i in 0 until numLocal) {
                    checkFrameValue(local[i])
                }
            }
        }
        if (numStack > 0) {
            require(stack != null && stack.size >= numStack) { "Array stack[] is shorter than numStack" }
            for (i in 0 until numStack) {
                checkFrameValue(stack[i])
            }
        }
        if (type == Opcodes.F_NEW) {
            ++numExpandedFrames
        } else {
            ++numCompressedFrames
        }
        require(numExpandedFrames <= 0 || numCompressedFrames <= 0) { "Expanded and compressed frames must not be mixed." }
        super.visitFrame(type, numLocal, local, numStack, stack)
    }

    override fun visitInsn(opcode: Int) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_INSN)
        super.visitInsn(opcode)
        ++insnCount
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_INT_INSN)
        when (opcode) {
            Opcodes.BIPUSH -> checkSignedByte(operand, "Invalid operand")
            Opcodes.SIPUSH -> checkSignedShort(operand, "Invalid operand")
            Opcodes.NEWARRAY -> require(Opcodes.T_BOOLEAN <= operand && operand <= Opcodes.T_LONG) {
                "Invalid operand (must be an array type code T_...): $operand"
            }
            else -> throw AssertionError()
        }
        super.visitIntInsn(opcode, operand)
        ++insnCount
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_VAR_INSN)
        checkUnsignedShort(`var`, "Invalid local variable index")
        super.visitVarInsn(opcode, `var`)
        ++insnCount
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_TYPE_INSN)
        checkInternalName(version, type, "type")
        if (opcode == Opcodes.NEW) require(type[0] != '[') { "NEW cannot be used to create arrays: $type" }
        super.visitTypeInsn(opcode, type)
        ++insnCount
    }

    override fun visitFieldInsn(
        opcode: Int, owner: String, name: String, descriptor: String,
    ) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_FIELD_INSN)
        checkInternalName(version, owner, "owner")
        checkUnqualifiedName(version, name, "name")
        checkDescriptor(version, descriptor, false)
        super.visitFieldInsn(opcode, owner, name, descriptor)
        ++insnCount
    }

    override fun visitMethodInsn(
        opcodeAndSource: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        if (api < Opcodes.ASM5 && opcodeAndSource and Opcodes.SOURCE_DEPRECATED == 0) {
            // Redirect the call to the deprecated version of this method.
            super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
            return
        }
        val opcode = opcodeAndSource and Opcodes.SOURCE_MASK.inv()
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_METHOD_INSN)
        if (opcode != Opcodes.INVOKESPECIAL || "<init>" != name) {
            checkMethodIdentifier(version, name, "name")
        }
        checkInternalName(version, owner, "owner")
        checkMethodDescriptor(version, descriptor)
        when (opcode) {
            Opcodes.INVOKEVIRTUAL -> require(!isInterface) { "INVOKEVIRTUAL can't be used with interfaces" }
            Opcodes.INVOKEINTERFACE -> require(isInterface) { "INVOKEINTERFACE can't be used with classes" }
            Opcodes.INVOKESPECIAL -> require(!isInterface || Opcodes.V1_8 <= version and 0xFFFF) {
                "INVOKESPECIAL can't be used with interfaces prior to Java 8"
            }
        }
        super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
        ++insnCount
    }

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any,
    ) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkMethodIdentifier(version, name, "name")
        checkMethodDescriptor(version, descriptor)
        require(bootstrapMethodHandle.tag == Opcodes.H_INVOKESTATIC
                || bootstrapMethodHandle.tag == Opcodes.H_NEWINVOKESPECIAL) {
            "invalid handle tag ${bootstrapMethodHandle.tag}"
        }
        for (bootstrapMethodArgument: Any in bootstrapMethodArguments) {
            checkLdcConstant(bootstrapMethodArgument)
        }
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
        ++insnCount
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkOpcodeMethod(opcode, Verifications.Method.VISIT_JUMP_INSN)
        checkLabel(label, false, "label")
        super.visitJumpInsn(opcode, label)
        referencedLabels.add(label)
        ++insnCount
    }

    override fun visitLabel(label: Label) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkLabel(label, false, "label")
        if (labelInsnIndices[label] != null) {
            throw IllegalStateException("Already visited label")
        }
        labelInsnIndices[label] = insnCount
        super.visitLabel(label)
    }

    override fun visitLdcInsn(value: Any) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkLdcConstant(value)
        super.visitLdcInsn(value)
        ++insnCount
    }

    override fun visitIincInsn(`var`: Int, increment: Int) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkUnsignedShort(`var`, "Invalid local variable index")
        checkSignedShort(increment, "Invalid increment")
        super.visitIincInsn(`var`, increment)
        ++insnCount
    }

    override fun visitTableSwitchInsn(
        min: Int, max: Int, dflt: Label, vararg labels: Label,
    ) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        require(max >= min) { "Max = $max must be greater than or equal to min = $min" }
        checkLabel(dflt, false, "default label")
        require(labels.size == max - min + 1) { "There must be max - min + 1 labels" }
        for (i in labels.indices) {
            checkLabel(labels[i], false, "label at index $i")
        }
        super.visitTableSwitchInsn(min, max, dflt, *labels)
        referencedLabels.addAll(labels)
        ++insnCount
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<Label>) {
        checkVisitMaxsNotCalled()
        checkVisitCodeCalled()
        checkLabel(dflt, false, "default label")
        require(keys.size == labels.size) { "There must be the same number of keys and labels" }
        for (i in labels.indices) {
            checkLabel(labels[i], false, "label at index $i")
        }
        super.visitLookupSwitchInsn(dflt, keys, labels)
        referencedLabels.add(dflt)
        referencedLabels.addAll(labels)
        ++insnCount
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkDescriptor(version, descriptor, false)
        require(descriptor[0] == '[') { "Invalid descriptor (must be an array type descriptor): $descriptor" }
        require(1 <= numDimensions) { "Invalid dimensions (must be greater than 0): $numDimensions" }
        require(descriptor.lastIndexOf('[') + 1 >= numDimensions) {
            "Invalid dimensions (must not be greater than numDimensions(descriptor)): $numDimensions"
        }
        super.visitMultiANewArrayInsn(descriptor, numDimensions)
        ++insnCount
    }

    override fun visitInsnAnnotation(
        typeRef: Int, typePath: TypePath, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.INSTANCEOF || sort == TypeReference.NEW
                || sort == TypeReference.CONSTRUCTOR_REFERENCE || sort == TypeReference.METHOD_REFERENCE
                || sort == TypeReference.CAST || sort == TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT
                || sort == TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT
                || sort == TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT
                || sort == TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT) {
            "Invalid type reference sort 0x${Integer.toHexString(sort)}"
        }
        checkTypeRef(typeRef)
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitInsnAnnotation(typeRef, typePath, descriptor, visible))
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkLabel(start, false, "start label")
        checkLabel(end, false, "end label")
        checkLabel(handler, false, "handler label")
        if (labelInsnIndices[start] != null || labelInsnIndices[end] != null || labelInsnIndices[handler] != null) {
            throw IllegalStateException("Try catch blocks must be visited before their labels")
        }
        if (type != null) {
            checkInternalName(version, type, "type")
        }
        super.visitTryCatchBlock(start, end, handler, type)
        handlers.add(start)
        handlers.add(end)
    }

    override fun visitTryCatchAnnotation(
        typeRef: Int, typePath: TypePath, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.EXCEPTION_PARAMETER) { "Invalid type reference sort 0x${Integer.toHexString(sort)}" }
        checkTypeRef(typeRef)
        checkDescriptor(version, descriptor, false)
        return CheckAnnotationAdapter(super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible))
    }

    override fun visitLocalVariable(
        name: String,
        descriptor: String,
        signature: String?,
        start: Label,
        end: Label,
        index: Int,
    ) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkUnqualifiedName(version, name, "name")
        checkDescriptor(version, descriptor, false)
        if (signature != null) {
            checkFieldSignature(signature)
        }
        checkLabel(start, true, "start label")
        checkLabel(end, true, "end label")
        checkUnsignedShort(index, "Invalid local variable index")
        val startInsnIndex = labelInsnIndices[start]!!.toInt()
        val endInsnIndex = labelInsnIndices[end]!!.toInt()
        require(startInsnIndex <= endInsnIndex) { "Invalid start and end labels (end must be greater than start)" }
        super.visitLocalVariable(name, descriptor, signature, start, end, index)
    }

    override fun visitLocalVariableAnnotation(
        typeRef: Int,
        typePath: TypePath,
        start: Array<Label>,
        end: Array<Label>,
        index: IntArray,
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.LOCAL_VARIABLE || sort == TypeReference.RESOURCE_VARIABLE) {
            "Invalid type reference sort 0x${Integer.toHexString(sort)}"
        }
        checkTypeRef(typeRef)
        checkDescriptor(version, descriptor, false)
        require(end.size == start.size && index.size == start.size) {
            "Invalid start, end and index arrays (must be non null and of identical length"
        }
        for (i in start.indices) {
            checkLabel(start[i], true, "start label")
            checkLabel(end[i], true, "end label")
            checkUnsignedShort(index[i], "Invalid local variable index")
            val startInsnIndex = labelInsnIndices[start[i]]!!.toInt()
            val endInsnIndex = labelInsnIndices[end[i]]!!.toInt()
            require(startInsnIndex <= endInsnIndex) { "Invalid start and end labels (end must be greater than start)" }
        }
        return super.visitLocalVariableAnnotation(
            typeRef, typePath, start, end, index, descriptor, visible)
    }

    override fun visitLineNumber(line: Int, start: Label) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        checkUnsignedShort(line, "Invalid line number")
        checkLabel(start, true, "start label")
        super.visitLineNumber(line, start)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        checkVisitCodeCalled()
        checkVisitMaxsNotCalled()
        visitMaxCalled = true
        for (l: Label? in referencedLabels) {
            if (labelInsnIndices[l] == null) {
                throw IllegalStateException("Undefined label used")
            }
        }
        var i = 0
        while (i < handlers.size) {
            val startInsnIndex = labelInsnIndices[handlers[i]]
            val endInsnIndex = labelInsnIndices[handlers[i + 1]]
            if (startInsnIndex == null || endInsnIndex == null) {
                throw IllegalStateException("Undefined try catch block labels")
            }
            if (endInsnIndex.toInt() <= startInsnIndex.toInt()) {
                throw IllegalStateException("Emty try catch block handler range")
            }
            i += 2
        }
        checkUnsignedShort(maxStack, "Invalid max stack")
        checkUnsignedShort(maxLocals, "Invalid max locals")
        super.visitMaxs(maxStack, maxLocals)
    }

    override fun visitEnd() {
        checkVisitEndNotCalled()
        visitEndCalled = true
        super.visitEnd()
    }
    // -----------------------------------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------------------------------
    private fun checkVisitCodeCalled() {
        if (!visitCodeCalled) {
            throw IllegalStateException("Cannot visit instructions before visitCode has been called.")
        }
    }

    private fun checkVisitMaxsNotCalled() {
        if (visitMaxCalled) {
            throw IllegalStateException("Cannot visit instructions after visitMaxs has been called.")
        }
    }

    private fun checkVisitEndNotCalled() {
        if (visitEndCalled) {
            throw IllegalStateException("Cannot visit elements after visitEnd has been called.")
        }
    }

    private fun checkFrameValue(value: Any) {
        @Suppress("IMPLICIT_BOXING_IN_IDENTITY_EQUALS")
        if (value === Opcodes.TOP || value === Opcodes.INTEGER || value === Opcodes.FLOAT || value === Opcodes.LONG
            || value === Opcodes.DOUBLE || value === Opcodes.NULL || value === Opcodes.UNINITIALIZED_THIS
        ) {
            return
        }
        when (value) {
            is String -> checkInternalName(version, value, "Invalid stack frame value")
            is Label -> referencedLabels.add(value)
            else -> throw IllegalArgumentException("Invalid stack frame value: $value")
        }
    }

    private fun checkLdcConstant(value: Any) {
        if (value is Type) {
            val sort = value.sort
            require(sort == Type.OBJECT || sort == Type.ARRAY || sort == Type.METHOD) { "Illegal LDC constant value" }
            require(sort == Type.METHOD || Opcodes.V1_5 <= version and 0xFFFF) {
                "ldc of a constant class requires at least version 1.5"
            }
            require(sort != Type.METHOD || Opcodes.V1_7 <= version and 0xFFFF) {
                "ldc of a method type requires at least version 1.7"
            }
        } else if (value is Handle) {
            require(Opcodes.V1_7 <= version and 0xFFFF) { "ldc of a Handle requires at least version 1.7" }
            val tag = value.tag
            require(Opcodes.H_GETFIELD <= tag && tag <= Opcodes.H_INVOKEINTERFACE) { "invalid handle tag $tag" }
            checkInternalName(version, value.owner, "handle owner")
            if (tag <= Opcodes.H_PUTSTATIC) {
                checkDescriptor(version, value.desc, false)
            } else {
                checkMethodDescriptor(version, value.desc)
            }
            val handleName = value.name
            if (!("<init>" == handleName && tag == Opcodes.H_NEWINVOKESPECIAL)) {
                checkMethodIdentifier(version, handleName, "handle name")
            }
        } else if (value is ConstantDynamic) {
            require(Opcodes.V11 <= version and 0xFFFF) { "ldc of a ConstantDynamic requires at least version 11" }
            checkMethodIdentifier(version, value.name, "constant dynamic name")
            checkDescriptor(version, value.descriptor, false)
            checkLdcConstant(value.bootstrapMethod)
            val bootstrapMethodArgumentCount = value.bootstrapMethodArgumentCount
            for (i in 0 until bootstrapMethodArgumentCount) {
                checkLdcConstant(value.getBootstrapMethodArgument(i))
            }
        } else {
            checkConstant(value)
        }
    }

    private fun checkLabel(label: Label, checkVisited: Boolean, message: String) {
        if (checkVisited) {
            requireNotNull(label in labelInsnIndices) { "Invalid $message (must be visited first)" }
        }
    }
}

internal class CheckModuleAdapter internal constructor(
    api: Int, moduleVisitor: ModuleVisitor?,
    /** Whether the visited module is open.  */
    private val isOpen: Boolean,
) : ModuleVisitor(api, moduleVisitor) {
    private val requiredModules = NameSet("Modules requires")
    private val exportedPackages = NameSet("Module exports")
    private val openedPackages = NameSet("Module opens")
    private val usedServices = NameSet("Module uses")
    private val providedServices = NameSet("Module provides")
    var classVersion = 0
    private var visitEndCalled = false

    override fun visitMainClass(mainClass: String) {
        // Modules can only appear in V9 or more classes.
        checkInternalName(Opcodes.V9, mainClass, "module main class")
        super.visitMainClass(mainClass)
    }

    override fun visitPackage(packaze: String) {
        checkInternalName(Opcodes.V9, packaze, "module package")
        super.visitPackage(packaze)
    }

    override fun visitRequire(module: String, access: Int, version: String) {
        checkVisitEndNotCalled()
        checkFullyQualifiedName(Opcodes.V9, module, "required module")
        requiredModules.checkNameNotAlreadyDeclared(module)
        checkAccess(access, Opcodes.ACC_STATIC_PHASE or Opcodes.ACC_TRANSITIVE
                or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED)
        if (classVersion >= Opcodes.V10 && module == "java.base") {
            require(access and (Opcodes.ACC_STATIC_PHASE or Opcodes.ACC_TRANSITIVE) == 0) {
                "Invalid access flags: $access java.base can not be declared ACC_TRANSITIVE or ACC_STATIC_PHASE"
            }
        }
        super.visitRequire(module, access, version)
    }

    override fun visitExport(packaze: String, access: Int, modules: Array<out String>?) {
        checkVisitEndNotCalled()
        checkInternalName(Opcodes.V9, packaze, "package name")
        exportedPackages.checkNameNotAlreadyDeclared(packaze)
        checkAccess(access, Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED)
        if (modules != null) {
            for (module in modules) {
                checkFullyQualifiedName(Opcodes.V9, module, "module export to")
            }
        }
        super.visitExport(packaze, access, *modules.orEmpty())
    }

    override fun visitOpen(packaze: String, access: Int, modules: Array<out String>?) {
        checkVisitEndNotCalled()
        if (isOpen) {
            throw UnsupportedOperationException("An open module can not use open directive")
        }
        checkInternalName(Opcodes.V9, packaze, "package name")
        openedPackages.checkNameNotAlreadyDeclared(packaze)
        checkAccess(access, Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED)
        if (modules != null) {
            for (module in modules) {
                checkFullyQualifiedName(Opcodes.V9, module, "module open to")
            }
        }
        super.visitOpen(packaze, access, *modules.orEmpty())
    }

    override fun visitUse(service: String) {
        checkVisitEndNotCalled()
        checkInternalName(Opcodes.V9, service, "service")
        usedServices.checkNameNotAlreadyDeclared(service)
        super.visitUse(service)
    }

    override fun visitProvide(service: String, vararg providers: String) {
        checkVisitEndNotCalled()
        checkInternalName(Opcodes.V9, service, "service")
        providedServices.checkNameNotAlreadyDeclared(service)
        require(providers.isNotEmpty()) { "Providers cannot be null or empty" }
        for (provider in providers) {
            checkInternalName(Opcodes.V9, provider, "provider")
        }
        super.visitProvide(service, *providers)
    }

    override fun visitEnd() {
        checkVisitEndNotCalled()
        visitEndCalled = true
        super.visitEnd()
    }

    private fun checkVisitEndNotCalled() {
        check(!visitEndCalled) { "Cannot call a visit method after visitEnd has been called" }
    }

    private class NameSet(private val type: String) {
        private val names = HashSet<String>()

        fun checkNameNotAlreadyDeclared(name: String) {
            require(names.add(name)) { "$type '$name' already declared" }
        }
    }
}

internal class CheckRecordComponentAdapter internal constructor(
    api: Int, recordComponentVisitor: RecordComponentVisitor?,
) : RecordComponentVisitor(api, recordComponentVisitor) {
    private var visitEndCalled = false

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        checkVisitEndNotCalled()
        // Annotations can only appear in V1_5 or more classes.
        checkDescriptor(Opcodes.V1_5, descriptor, false)
        return CheckAnnotationAdapter(super.visitAnnotation(descriptor, visible))
    }

    @Suppress("DuplicatedCode")
    override fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean,
    ): AnnotationVisitor {
        checkVisitEndNotCalled()
        val sort = TypeReference(typeRef).sort
        require(sort == TypeReference.FIELD) { "Invalid type reference sort 0x${Integer.toHexString(sort)}" }
        checkTypeRef(typeRef)
        checkDescriptor(Opcodes.V1_5, descriptor, false)
        return CheckAnnotationAdapter(super.visitTypeAnnotation(typeRef, typePath, descriptor, visible))
    }

    override fun visitAttribute(attribute: Attribute) {
        checkVisitEndNotCalled()
        super.visitAttribute(attribute)
    }

    override fun visitEnd() {
        checkVisitEndNotCalled()
        visitEndCalled = true
        super.visitEnd()
    }

    private fun checkVisitEndNotCalled() {
        check(!visitEndCalled) { "Cannot call a visit method after visitEnd has been called" }
    }
}

// verification helpers
internal object Verifications {
    @JvmStatic
    fun checkAccess(access: Int, possibleAccess: Int) {
        require((access and possibleAccess.inv()) == 0) { "Invalid access flags: $access" }
        val publicProtectedPrivate = Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED or Opcodes.ACC_PRIVATE
        require((access and publicProtectedPrivate).countOneBits() <= 1) {
            "public, protected and private are mutually exclusive: $access"
        }
        require((access and (Opcodes.ACC_FINAL or Opcodes.ACC_ABSTRACT)).countOneBits() <= 1) {
            "final and abstract are mutually exclusive: $access"
        }
    }

    @JvmStatic
    fun checkMethodAccess(version: Int, access: Int, possibleAccess: Int) {
        checkAccess(access, possibleAccess)
        if ((version and 0xFFFF) < Opcodes.V17) {
            (access and (Opcodes.ACC_STRICT or Opcodes.ACC_ABSTRACT)).countOneBits()
            require((access and (Opcodes.ACC_STRICT or Opcodes.ACC_ABSTRACT)).countOneBits() <= 1) {
                "strictfp and abstract are mutually exclusive: $access"
            }
        }
    }

    @JvmStatic
    fun checkFullyQualifiedName(version: Int, name: String, source: String?, separator: Char, message: String) {
        try {
            var startIndex = 0
            var separatorIndex: Int
            while ((name.indexOf(separator, startIndex + 1).also { separatorIndex = it }) != -1) {
                checkIdentifier(version, name, startIndex, separatorIndex, null)
                startIndex = separatorIndex + 1
            }
            checkIdentifier(version, name, startIndex, name.length, null)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid $source ($message): $name", e)
        }
    }

    @JvmStatic
    fun checkFullyQualifiedName(version: Int, name: String, source: String) =
        checkFullyQualifiedName(version, name, source, '.', "must be a fully qualified name")

    @JvmStatic
    fun checkClassSignature(signature: String) {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // ClassSignature:
        //   [TypeParameters] SuperclassSignature SuperinterfaceSignature*
        // SuperclassSignature:
        //   ClassTypeSignature
        // SuperinterfaceSignature:
        //   ClassTypeSignature
        var pos = 0
        if (getChar(signature, 0) == '<') {
            pos = checkTypeParameters(signature, pos)
        }
        pos = checkClassTypeSignature(signature, pos)
        while (getChar(signature, pos) == 'L') {
            pos = checkClassTypeSignature(signature, pos)
        }
        require(pos == signature.length) { "$signature: error at index $pos" }
    }

    @JvmStatic
    fun checkMethodSignature(signature: String) {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // MethodSignature:
        //   [TypeParameters] ( JavaTypeSignature* ) Result ThrowsSignature*
        // Result:
        //   JavaTypeSignature
        //   VoidDescriptor
        // ThrowsSignature:
        //   ^ ClassTypeSignature
        //   ^ TypeVariableSignature
        var pos = 0
        if (getChar(signature, 0) == '<') {
            pos = checkTypeParameters(signature, pos)
        }
        pos = checkChar('(', signature, pos)
        while ("ZCBSIFJDL[T".indexOf(getChar(signature, pos)) != -1) {
            pos = checkJavaTypeSignature(signature, pos)
        }
        pos = checkChar(')', signature, pos)
        if (getChar(signature, pos) == 'V') {
            ++pos
        } else {
            pos = checkJavaTypeSignature(signature, pos)
        }
        while (getChar(signature, pos) == '^') {
            ++pos
            pos = if (getChar(signature, pos) == 'L') {
                checkClassTypeSignature(signature, pos)
            } else {
                checkTypeVariableSignature(signature, pos)
            }
        }
        require(pos == signature.length) { "$signature: error at index $pos" }
    }

    @JvmStatic
    fun checkFieldSignature(signature: String) {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // FieldSignature:
        //   ReferenceTypeSignature
        val pos = checkReferenceTypeSignature(signature, 0)
        require(pos == signature.length) { "$signature: error at index $pos" }
    }

    @JvmStatic
    private fun checkTypeParameters(signature: String, startPos: Int): Int {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // TypeParameters:
        //   < TypeParameter TypeParameter* >
        var pos = startPos
        pos = checkChar('<', signature, pos)
        pos = checkTypeParameter(signature, pos)
        while (getChar(signature, pos) != '>') {
            pos = checkTypeParameter(signature, pos)
        }
        return pos + 1
    }

    @JvmStatic
    private fun checkTypeParameter(signature: String, startPos: Int): Int {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // TypeParameter:
        //   Identifier ClassBound InterfaceBound*
        // ClassBound:
        //   : [ReferenceTypeSignature]
        // InterfaceBound:
        //   : ReferenceTypeSignature
        var pos = startPos
        pos = checkSignatureIdentifier(signature, pos)
        pos = checkChar(':', signature, pos)
        if ("L[T".indexOf(getChar(signature, pos)) != -1) {
            pos = checkReferenceTypeSignature(signature, pos)
        }
        while (getChar(signature, pos) == ':') {
            pos = checkReferenceTypeSignature(signature, pos + 1)
        }
        return pos
    }

    // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
    // ReferenceTypeSignature:
    //   ClassTypeSignature
    //   TypeVariableSignature
    //   ArrayTypeSignature
    // ArrayTypeSignature:
    //   [ JavaTypeSignature
    @JvmStatic
    private fun checkReferenceTypeSignature(signature: String, pos: Int): Int = when (getChar(signature, pos)) {
        'L' -> checkClassTypeSignature(signature, pos)
        '[' -> checkJavaTypeSignature(signature, pos + 1)
        else -> checkTypeVariableSignature(signature, pos)
    }

    @JvmStatic
    private fun checkClassTypeSignature(signature: String, startPos: Int): Int {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // ClassTypeSignature:
        //   L [PackageSpecifier] SimpleClassTypeSignature ClassTypeSignatureSuffix* ;
        // PackageSpecifier:
        //   Identifier / PackageSpecifier*
        // SimpleClassTypeSignature:
        //   Identifier [TypeArguments]
        // ClassTypeSignatureSuffix:
        //   . SimpleClassTypeSignature
        var pos = startPos
        pos = checkChar('L', signature, pos)
        pos = checkSignatureIdentifier(signature, pos)
        while (getChar(signature, pos) == '/') {
            pos = checkSignatureIdentifier(signature, pos + 1)
        }
        if (getChar(signature, pos) == '<') {
            pos = checkTypeArguments(signature, pos)
        }
        while (getChar(signature, pos) == '.') {
            pos = checkSignatureIdentifier(signature, pos + 1)
            if (getChar(signature, pos) == '<') {
                pos = checkTypeArguments(signature, pos)
            }
        }
        return checkChar(';', signature, pos)
    }

    @JvmStatic
    private fun checkTypeArguments(signature: String, startPos: Int): Int {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // TypeArguments:
        //   < TypeArgument TypeArgument* >
        var pos = startPos
        pos = checkChar('<', signature, pos)
        pos = checkTypeArgument(signature, pos)
        while (getChar(signature, pos) != '>') {
            pos = checkTypeArgument(signature, pos)
        }
        return pos + 1
    }

    @JvmStatic
    private fun checkTypeArgument(signature: String, startPos: Int): Int {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // TypeArgument:
        //   [WildcardIndicator] ReferenceTypeSignature
        //   *
        // WildcardIndicator:
        //   +
        //   -
        var pos = startPos
        val c = getChar(signature, pos)
        if (c == '*') {
            return pos + 1
        } else if (c == '+' || c == '-') {
            pos++
        }
        return checkReferenceTypeSignature(signature, pos)
    }

    @JvmStatic
    private fun checkTypeVariableSignature(signature: String, startPos: Int): Int {
        // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
        // TypeVariableSignature:
        //  T Identifier ;
        var pos = startPos
        pos = checkChar('T', signature, pos)
        pos = checkSignatureIdentifier(signature, pos)
        return checkChar(';', signature, pos)
    }

    // From https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.9.1:
    // JavaTypeSignature:
    //   ReferenceTypeSignature
    //   BaseType
    // BaseType:
    //   (one of)
    //   B C D F I J S Z
    @JvmStatic
    private fun checkJavaTypeSignature(signature: String, startPos: Int): Int =
        when (getChar(signature, startPos)) {
            'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> startPos + 1
            else -> checkReferenceTypeSignature(signature, startPos)
        }

    @JvmStatic
    private fun checkSignatureIdentifier(signature: String, startPos: Int): Int {
        var pos = startPos
        while (pos < signature.length && ".;[/<>:".indexOf(signature.codePointAt(pos).toChar()) == -1) {
            pos = signature.offsetByCodePoints(pos, 1)
        }
        require(pos != startPos) { "$signature: identifier expected at index $startPos" }
        return pos
    }

    @JvmStatic
    private fun checkChar(c: Char, signature: String, pos: Int): Int {
        require(getChar(signature, pos) == c) { "$signature: '$c' expected at index $pos" }
        return pos + 1
    }

    @JvmStatic
    private fun getChar(string: String, pos: Int): Char {
        return if (pos < string.length) string[pos] else 0.toChar()
    }

    @JvmStatic
    fun checkTypeRef(typeRef: Int) {
        var mask = 0
        when (typeRef ushr 24) {
            TypeReference.CLASS_TYPE_PARAMETER, TypeReference.METHOD_TYPE_PARAMETER, TypeReference.METHOD_FORMAL_PARAMETER -> mask =
                -0x10000
            TypeReference.FIELD, TypeReference.METHOD_RETURN, TypeReference.METHOD_RECEIVER, TypeReference.LOCAL_VARIABLE, TypeReference.RESOURCE_VARIABLE, TypeReference.INSTANCEOF, TypeReference.NEW, TypeReference.CONSTRUCTOR_REFERENCE, TypeReference.METHOD_REFERENCE -> mask =
                -0x1000000
            TypeReference.CLASS_EXTENDS, TypeReference.CLASS_TYPE_PARAMETER_BOUND, TypeReference.METHOD_TYPE_PARAMETER_BOUND, TypeReference.THROWS, TypeReference.EXCEPTION_PARAMETER -> mask =
                -0x100
            TypeReference.CAST, TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT, TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT -> mask =
                -0xffff01
            else -> {}
        }
        require(mask != 0 && (typeRef and mask.inv()) == 0) { "Invalid type reference 0x${Integer.toHexString(typeRef)}" }
    }

    @JvmStatic
    fun packageName(name: String): String {
        val index = name.lastIndexOf('/')
        return if (index == -1) "" else name.substring(0, index)
    }

    /** The 'generic' instruction visit methods (i.e. those that take an opcode argument).  */
    internal enum class Method {
        VISIT_INSN, VISIT_INT_INSN, VISIT_VAR_INSN, VISIT_TYPE_INSN, VISIT_FIELD_INSN, VISIT_METHOD_INSN, VISIT_JUMP_INSN
    }

    /** The method to use to visit each instruction. Only generic methods are represented here.  */
    @JvmStatic
    private val OPCODE_METHODS = arrayOf(
        Method.VISIT_INSN,  // NOP
        Method.VISIT_INSN,  // ACONST_NULL
        Method.VISIT_INSN,  // ICONST_M1
        Method.VISIT_INSN,  // ICONST_0
        Method.VISIT_INSN,  // ICONST_1
        Method.VISIT_INSN,  // ICONST_2
        Method.VISIT_INSN,  // ICONST_3
        Method.VISIT_INSN,  // ICONST_4
        Method.VISIT_INSN,  // ICONST_5
        Method.VISIT_INSN,  // LCONST_0
        Method.VISIT_INSN,  // LCONST_1
        Method.VISIT_INSN,  // FCONST_0
        Method.VISIT_INSN,  // FCONST_1
        Method.VISIT_INSN,  // FCONST_2
        Method.VISIT_INSN,  // DCONST_0
        Method.VISIT_INSN,  // DCONST_1
        Method.VISIT_INT_INSN,  // BIPUSH
        Method.VISIT_INT_INSN,  // SIPUSH
        null,  // LDC
        null,  // LDC_W
        null,  // LDC2_W
        Method.VISIT_VAR_INSN,  // ILOAD
        Method.VISIT_VAR_INSN,  // LLOAD
        Method.VISIT_VAR_INSN,  // FLOAD
        Method.VISIT_VAR_INSN,  // DLOAD
        Method.VISIT_VAR_INSN,  // ALOAD
        null,  // ILOAD_0
        null,  // ILOAD_1
        null,  // ILOAD_2
        null,  // ILOAD_3
        null,  // LLOAD_0
        null,  // LLOAD_1
        null,  // LLOAD_2
        null,  // LLOAD_3
        null,  // FLOAD_0
        null,  // FLOAD_1
        null,  // FLOAD_2
        null,  // FLOAD_3
        null,  // DLOAD_0
        null,  // DLOAD_1
        null,  // DLOAD_2
        null,  // DLOAD_3
        null,  // ALOAD_0
        null,  // ALOAD_1
        null,  // ALOAD_2
        null,  // ALOAD_3
        Method.VISIT_INSN,  // IALOAD
        Method.VISIT_INSN,  // LALOAD
        Method.VISIT_INSN,  // FALOAD
        Method.VISIT_INSN,  // DALOAD
        Method.VISIT_INSN,  // AALOAD
        Method.VISIT_INSN,  // BALOAD
        Method.VISIT_INSN,  // CALOAD
        Method.VISIT_INSN,  // SALOAD
        Method.VISIT_VAR_INSN,  // ISTORE
        Method.VISIT_VAR_INSN,  // LSTORE
        Method.VISIT_VAR_INSN,  // FSTORE
        Method.VISIT_VAR_INSN,  // DSTORE
        Method.VISIT_VAR_INSN,  // ASTORE
        null,  // ISTORE_0
        null,  // ISTORE_1
        null,  // ISTORE_2
        null,  // ISTORE_3
        null,  // LSTORE_0
        null,  // LSTORE_1
        null,  // LSTORE_2
        null,  // LSTORE_3
        null,  // FSTORE_0
        null,  // FSTORE_1
        null,  // FSTORE_2
        null,  // FSTORE_3
        null,  // DSTORE_0
        null,  // DSTORE_1
        null,  // DSTORE_2
        null,  // DSTORE_3
        null,  // ASTORE_0
        null,  // ASTORE_1
        null,  // ASTORE_2
        null,  // ASTORE_3
        Method.VISIT_INSN,  // IASTORE
        Method.VISIT_INSN,  // LASTORE
        Method.VISIT_INSN,  // FASTORE
        Method.VISIT_INSN,  // DASTORE
        Method.VISIT_INSN,  // AASTORE
        Method.VISIT_INSN,  // BASTORE
        Method.VISIT_INSN,  // CASTORE
        Method.VISIT_INSN,  // SASTORE
        Method.VISIT_INSN,  // POP
        Method.VISIT_INSN,  // POP2
        Method.VISIT_INSN,  // DUP
        Method.VISIT_INSN,  // DUP_X1
        Method.VISIT_INSN,  // DUP_X2
        Method.VISIT_INSN,  // DUP2
        Method.VISIT_INSN,  // DUP2_X1
        Method.VISIT_INSN,  // DUP2_X2
        Method.VISIT_INSN,  // SWAP
        Method.VISIT_INSN,  // IADD
        Method.VISIT_INSN,  // LADD
        Method.VISIT_INSN,  // FADD
        Method.VISIT_INSN,  // DADD
        Method.VISIT_INSN,  // ISUB
        Method.VISIT_INSN,  // LSUB
        Method.VISIT_INSN,  // FSUB
        Method.VISIT_INSN,  // DSUB
        Method.VISIT_INSN,  // IMUL
        Method.VISIT_INSN,  // LMUL
        Method.VISIT_INSN,  // FMUL
        Method.VISIT_INSN,  // DMUL
        Method.VISIT_INSN,  // IDIV
        Method.VISIT_INSN,  // LDIV
        Method.VISIT_INSN,  // FDIV
        Method.VISIT_INSN,  // DDIV
        Method.VISIT_INSN,  // IREM
        Method.VISIT_INSN,  // LREM
        Method.VISIT_INSN,  // FREM
        Method.VISIT_INSN,  // DREM
        Method.VISIT_INSN,  // INEG
        Method.VISIT_INSN,  // LNEG
        Method.VISIT_INSN,  // FNEG
        Method.VISIT_INSN,  // DNEG
        Method.VISIT_INSN,  // ISHL
        Method.VISIT_INSN,  // LSHL
        Method.VISIT_INSN,  // ISHR
        Method.VISIT_INSN,  // LSHR
        Method.VISIT_INSN,  // IUSHR
        Method.VISIT_INSN,  // LUSHR
        Method.VISIT_INSN,  // IAND
        Method.VISIT_INSN,  // LAND
        Method.VISIT_INSN,  // IOR
        Method.VISIT_INSN,  // LOR
        Method.VISIT_INSN,  // IXOR
        Method.VISIT_INSN,  // LXOR
        null,  // IINC
        Method.VISIT_INSN,  // I2L
        Method.VISIT_INSN,  // I2F
        Method.VISIT_INSN,  // I2D
        Method.VISIT_INSN,  // L2I
        Method.VISIT_INSN,  // L2F
        Method.VISIT_INSN,  // L2D
        Method.VISIT_INSN,  // F2I
        Method.VISIT_INSN,  // F2L
        Method.VISIT_INSN,  // F2D
        Method.VISIT_INSN,  // D2I
        Method.VISIT_INSN,  // D2L
        Method.VISIT_INSN,  // D2F
        Method.VISIT_INSN,  // I2B
        Method.VISIT_INSN,  // I2C
        Method.VISIT_INSN,  // I2S
        Method.VISIT_INSN,  // LCMP
        Method.VISIT_INSN,  // FCMPL
        Method.VISIT_INSN,  // FCMPG
        Method.VISIT_INSN,  // DCMPL
        Method.VISIT_INSN,  // DCMPG
        Method.VISIT_JUMP_INSN,  // IFEQ
        Method.VISIT_JUMP_INSN,  // IFNE
        Method.VISIT_JUMP_INSN,  // IFLT
        Method.VISIT_JUMP_INSN,  // IFGE
        Method.VISIT_JUMP_INSN,  // IFGT
        Method.VISIT_JUMP_INSN,  // IFLE
        Method.VISIT_JUMP_INSN,  // IF_ICMPEQ
        Method.VISIT_JUMP_INSN,  // IF_ICMPNE
        Method.VISIT_JUMP_INSN,  // IF_ICMPLT
        Method.VISIT_JUMP_INSN,  // IF_ICMPGE
        Method.VISIT_JUMP_INSN,  // IF_ICMPGT
        Method.VISIT_JUMP_INSN,  // IF_ICMPLE
        Method.VISIT_JUMP_INSN,  // IF_ACMPEQ
        Method.VISIT_JUMP_INSN,  // IF_ACMPNE
        Method.VISIT_JUMP_INSN,  // GOTO
        Method.VISIT_JUMP_INSN,  // JSR
        Method.VISIT_VAR_INSN,  // RET
        null,  // TABLESWITCH
        null,  // LOOKUPSWITCH
        Method.VISIT_INSN,  // IRETURN
        Method.VISIT_INSN,  // LRETURN
        Method.VISIT_INSN,  // FRETURN
        Method.VISIT_INSN,  // DRETURN
        Method.VISIT_INSN,  // ARETURN
        Method.VISIT_INSN,  // RETURN
        Method.VISIT_FIELD_INSN,  // GETSTATIC
        Method.VISIT_FIELD_INSN,  // PUTSTATIC
        Method.VISIT_FIELD_INSN,  // GETFIELD
        Method.VISIT_FIELD_INSN,  // PUTFIELD
        Method.VISIT_METHOD_INSN,  // INVOKEVIRTUAL
        Method.VISIT_METHOD_INSN,  // INVOKESPECIAL
        Method.VISIT_METHOD_INSN,  // INVOKESTATIC
        Method.VISIT_METHOD_INSN,  // INVOKEINTERFACE
        null,  // INVOKEDYNAMIC
        Method.VISIT_TYPE_INSN,  // NEW
        Method.VISIT_INT_INSN,  // NEWARRAY
        Method.VISIT_TYPE_INSN,  // ANEWARRAY
        Method.VISIT_INSN,  // ARRAYLENGTH
        Method.VISIT_INSN,  // ATHROW
        Method.VISIT_TYPE_INSN,  // CHECKCAST
        Method.VISIT_TYPE_INSN,  // INSTANCEOF
        Method.VISIT_INSN,  // MONITORENTER
        Method.VISIT_INSN,  // MONITOREXIT
        null,  // WIDE
        null,  // MULTIANEWARRAY
        Method.VISIT_JUMP_INSN,  // IFNULL
        Method.VISIT_JUMP_INSN // IFNONNULL
    )

    @JvmStatic
    internal fun checkOpcodeMethod(opcode: Int, method: Method) {
        require(Opcodes.NOP <= opcode && opcode <= Opcodes.IFNONNULL) { "Invalid opcode: $opcode" }
        require(OPCODE_METHODS[opcode] == method) { "Invalid opcode: $opcode" }
    }

    @JvmStatic
    internal fun checkSignedByte(value: Int, message: String) {
        require(Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE) { "$message (must be a signed byte): $value" }
    }

    @JvmStatic
    fun checkSignedShort(value: Int, message: String) {
        require(Short.MIN_VALUE <= value && value <= Short.MAX_VALUE) { "$message (must be a signed short): $value" }
    }

    @JvmStatic
    fun checkUnsignedShort(value: Int, message: String) {
        require(value in 0..65535) { "$message (must be an unsigned short): $value" }
    }

    @JvmStatic
    fun checkConstant(value: Any) {
        require(value is Int || value is Float || value is Long || value is Double || value is String) {
            "Invalid constant: $value"
        }
    }

    @JvmStatic
    fun checkUnqualifiedName(version: Int, name: String, message: String?) {
        checkIdentifier(version, name, 0, -1, message)
    }

    @JvmStatic
    fun checkIdentifier(
        version: Int,
        name: String,
        startPos: Int,
        endPos: Int,
        message: String?,
    ) {
        if (endPos == -1) {
            require(startPos < name.length) { "Invalid $message (must not be null or empty)" }
        } else {
            require(startPos < endPos) { "Invalid $message (must not be null or empty)" }
        }
        val max = if (endPos == -1) name.length else endPos
        if (version and 0xFFFF >= Opcodes.V1_5) {
            var i = startPos
            while (i < max) {
                require(".;[/".indexOf(name.codePointAt(i)
                    .toChar()) == -1) { "Invalid $message (must not contain . ; [ or /): $name" }
                i = name.offsetByCodePoints(i, 1)
            }
            return
        }
        var i = startPos
        while (i < max) {
            if (i == startPos) {
                require(Character.isJavaIdentifierStart(name.codePointAt(i))) {
                    "Invalid $message (must be a valid Java identifier): $name"
                }
            } else {
                require(Character.isJavaIdentifierPart(name.codePointAt(i))) {
                    "Invalid $message (must be a valid Java identifier): $name"
                }
            }
            i = name.offsetByCodePoints(i, 1)
        }
    }

    @JvmStatic
    fun checkMethodIdentifier(version: Int, name: String, message: String) {
        require(name.isNotEmpty()) { "Invalid $message (must not be null or empty)" }
        if (version and 0xFFFF >= Opcodes.V1_5) {
            var i = 0
            while (i < name.length) {
                require(".;[/<>".indexOf(name.codePointAt(i).toChar()) == -1) {
                    "Invalid $message (must be a valid unqualified name): $name"
                }
                i = name.offsetByCodePoints(i, 1)
            }
            return
        }
        var i = 0
        while (i < name.length) {
            if (i == 0) {
                require(Character.isJavaIdentifierStart(name.codePointAt(i))) {
                    "Invalid $message (must be a '<init>', '<clinit>' or a valid Java identifier): $name"
                }
            } else {
                require(Character.isJavaIdentifierPart(name.codePointAt(i))) {
                    "Invalid $message (must be a '<init>', '<clinit>' or a valid Java identifier): $name"
                }
            }
            i = name.offsetByCodePoints(i, 1)
        }
    }

    @JvmStatic
    fun checkInternalName(version: Int, name: String, message: String) {
        require(name.isNotEmpty()) { "Invalid $message (must not be null or empty)" }
        if (name[0] == '[') {
            checkDescriptor(version, name, false)
        } else {
            checkInternalClassName(version, name, message)
        }
    }

    @JvmStatic
    private fun checkInternalClassName(version: Int, name: String, message: String?) =
        checkFullyQualifiedName(version, name, message, '/', "must be an internal class name")

    @JvmStatic
    fun checkDescriptor(version: Int, descriptor: String, canBeVoid: Boolean) {
        val endPos = checkDescriptor(version, descriptor, 0, canBeVoid)
        require(endPos == descriptor.length) { "Invalid descriptor: $descriptor" }
    }

    @JvmStatic
    private fun checkDescriptor(
        version: Int, descriptor: String, startPos: Int, canBeVoid: Boolean,
    ): Int {
        require(startPos < descriptor.length) { "Invalid type descriptor (must not be null or empty)" }
        when (descriptor[startPos]) {
            'V' -> {
                require(canBeVoid) { "Invalid descriptor: $descriptor" }
                return startPos + 1
            }
            'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> return startPos + 1
            '[' -> {
                var pos = startPos + 1
                while (pos < descriptor.length && descriptor[pos] == '[') {
                    ++pos
                }
                require(pos < descriptor.length) { "Invalid descriptor: $descriptor" }
                return checkDescriptor(version, descriptor, pos, false)
            }
            'L' -> {
                val endPos = descriptor.indexOf(';', startPos)
                require(endPos - startPos >= 2) { "Invalid descriptor: $descriptor" }
                try {
                    checkInternalClassName(version, descriptor.substring(startPos + 1, endPos), null)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid descriptor: $descriptor", e)
                }
                return endPos + 1
            }
            else -> throw IllegalArgumentException("Invalid descriptor: $descriptor")
        }
    }

    @JvmStatic
    fun checkMethodDescriptor(version: Int, descriptor: String) {
        require(descriptor.isNotEmpty()) { "Invalid method descriptor (must not be empty)" }
        require(descriptor[0] == '(' && descriptor.length >= 3) { "Invalid descriptor: $descriptor" }
        var pos = 1
        if (descriptor[pos] != ')') {
            do {
                require(descriptor[pos] != 'V') { "Invalid descriptor: $descriptor" }
                pos = checkDescriptor(version, descriptor, pos, false)
            } while (pos < descriptor.length && descriptor[pos] != ')')
        }
        pos = checkDescriptor(version, descriptor, pos + 1, true)
        require(pos == descriptor.length) { "Invalid descriptor: $descriptor" }
    }
}
