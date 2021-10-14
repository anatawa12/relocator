@file:JvmName("ReferenceComputer")
package com.anatawa12.relocator.internal

import com.anatawa12.relocator.internal.ClassRefCollectingAnnotationVisitor.Utils.acceptAnnotations
import com.anatawa12.relocator.internal.ClassRefCollectingAnnotationVisitor.Utils.acceptValue
import com.anatawa12.relocator.internal.ClassRefCollectingSignatureVisitor.Utils.acceptSignature
import com.anatawa12.relocator.internal.Reference.Utils.fromDescriptor
import com.anatawa12.relocator.internal.Reference.Utils.fromHandle
import com.anatawa12.relocator.internal.Reference.Utils.fromInternalName
import com.anatawa12.relocator.internal.Reference.Utils.fromType
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ACC_RECORD
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

internal abstract class ComputeReferenceEnvironment(
    val keepRuntimeInvisibleAnnotation: Boolean,
) {
    abstract fun addDiagnostic(diagnostic: Diagnostic)
}

internal fun computeReferencesOfClass(
    env: ComputeReferenceEnvironment,
    main: ClassNode,
    innerClasses: InnerClassContainer,
) = buildSet<Reference> {
    acceptSignature(this, env, innerClasses, main.signature)
    main.superName.let(::fromInternalName)?.let(::add)
    // nest member classes are not required to exist
    // add them if exists
    //main.interfaces.let(::fromInternalName)?.let(::add)
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
    }
    main.nestHostClass?.let(::fromInternalName)?.let(::add)
    // nest member classes are not required to exist
    //main.nestMembers?.mapNotNullTo(this, ::fromInternalName)
    // permitted subclasses are not required to exist
    //main.permittedSubclasses?.mapNotNullTo(this, ::fromInternalName)

    // record support
    if (main.access.hasFlag(ACC_RECORD)) {
        // record components (including default constructor) will be keep
        // if this class will be kept
        for (recordComponent in main.recordComponents) {
            recordComponent.descriptor.let(::fromDescriptor)?.let(::add)
            acceptSignature(this, env, innerClasses, recordComponent.signature)
            acceptAnnotations(this, env, recordComponent.visibleAnnotations)
            acceptAnnotations(this, env, recordComponent.visibleTypeAnnotations)
            add(MethodReference(main.name, recordComponent.name, "()${recordComponent.descriptor}"))
            if (env.keepRuntimeInvisibleAnnotation) {
                acceptAnnotations(this, env, recordComponent.invisibleAnnotations)
                acceptAnnotations(this, env, recordComponent.invisibleTypeAnnotations)
            }
        }
        val recordDefaultCtor = "(${main.recordComponents.joinToString("") { it.descriptor }})V"
        add(MethodReference(main.name, "<init>", recordDefaultCtor))
    }

    // other special implementations

    // if static initializer exists, it should be kept
    if (main.methods.any { it.name == "<clinit>" }) {
        add(MethodReference(main.name, "<clinit>", "()V"))
    }
}

internal fun computeReferencesOfMethod(
    env: ComputeReferenceEnvironment, 
    main: MethodNode,
    innerClasses: InnerClassContainer,
) = buildSet<Reference> {
    Type.getArgumentTypes(main.desc).mapNotNullTo(this, Reference.Utils::fromType)
    Type.getReturnType(main.desc).let(Reference.Utils::fromType)?.let(::add)
    acceptSignature(this, env, innerClasses, main.signature)
    main.exceptions.mapNotNullTo(this, Reference.Utils::fromInternalName)
    acceptValue(this, main.annotationDefault)
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    acceptAnnotations(this, env, main.visibleParameterAnnotations)
    acceptAnnotations(this, env, main.visibleLocalVariableAnnotations)
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
        acceptAnnotations(this, env, main.invisibleParameterAnnotations)
        acceptAnnotations(this, env, main.invisibleLocalVariableAnnotations)
    }
    for (tryCatchBlock in main.tryCatchBlocks) {
        tryCatchBlock.type?.let(Reference.Utils::fromInternalName)?.let(::add)
        acceptAnnotations(this, env, tryCatchBlock.visibleTypeAnnotations)
        if (env.keepRuntimeInvisibleAnnotation) {
            acceptAnnotations(this,
                env,
                tryCatchBlock.invisibleTypeAnnotations)
        }
    }
    main.localVariables?.forEach { localVariable ->
        localVariable.desc?.let(Reference.Utils::fromDescriptor)?.let(::add)
        acceptSignature(this, env, innerClasses, localVariable.signature)
    }
    main.instructions.accept(ReferenceCollectionMethodVisitor(this, env))

    // additional: owner class
    fromInternalName(innerClasses.owner)?.let(::add)
}

internal fun computeReferencesOfField(
    env: ComputeReferenceEnvironment,
    main: FieldNode,
    innerClasses: InnerClassContainer,
) = buildSet<ClassReference> {
    Type.getArgumentTypes(main.desc).mapNotNullTo(this, ::fromType)
    Type.getReturnType(main.desc).let(::fromType)?.let(::add)
    acceptSignature(this, env, innerClasses, main.signature)
    acceptValue(this, main.value)
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
    }

    // additional: owner class
    fromInternalName(innerClasses.owner)?.let(::add)
}

internal class ClassRefCollectingSignatureVisitor private constructor(
    val references: MutableCollection<in ClassReference>,
    val env: ComputeReferenceEnvironment,
    val innerClasses: InnerClassContainer,
) : SignatureVisitor(ASM9) {
    private val child by lazy(LazyThreadSafetyMode.NONE) {
        ClassRefCollectingSignatureVisitor(references, env, innerClasses)
    }

    private var classType: String? = null

    override fun visitClassType(name: String) {
        classType = name
    }

    override fun visitInnerClassType(name: String) {
        classType = classType?.let { classType ->
            val foundInner = innerClasses.findInner(classType, name)
            if (foundInner == null)
                env.addDiagnostic(UnresolvableInnerClass(classType, name))
            foundInner
        }
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor = child

    override fun visitEnd() {
        classType?.let { classType ->
            references.add(ClassReference(classType))
        }
        classType = null
    }

    companion object Utils {
        fun acceptSignature(
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            innerClasses: InnerClassContainer,
            signature: String?,
        ) {
            if (signature != null) {
                SignatureReader(signature)
                    .accept(ClassRefCollectingSignatureVisitor(references, env, innerClasses))
            }
        }
    }
}

internal class ClassRefCollectingAnnotationVisitor(
    val references: MutableCollection<in ClassReference>,
    val env: ComputeReferenceEnvironment,
): AnnotationVisitor(ASM9) {
    override fun visit(name: String?, value: Any?) {
        acceptValue(references, value)
    }

    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
        fromType(Type.getType(descriptor))?.let(references::add)
        return this
    }

    override fun visitEnum(name: String?, descriptor: String, value: String) {
        fromType(Type.getType(descriptor))?.let(references::add)
    }

    companion object Utils {
        fun acceptValue(references: MutableCollection<in ClassReference>, value: Any?) {
            if (value is Type)
                fromType(value)?.let(references::add)
        }

        fun acceptAnnotations(
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            annotations: List<AnnotationNode>?,
        ) {
            if (annotations == null) return
            val visitor = ClassRefCollectingAnnotationVisitor(references, env)
            annotations.forEach { it.accept(visitor) }
        }

        fun acceptAnnotations(
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            annotations: Array<List<AnnotationNode>>?,
        ) {
            if (annotations == null) return
            val visitor = ClassRefCollectingAnnotationVisitor(references, env)
            for (annotationNodes in annotations) {
                annotationNodes.forEach { it.accept(visitor) }
            }
        }
    }
}

internal class ReferenceCollectionMethodVisitor(
    val references: MutableCollection<in Reference>,
    val env: ComputeReferenceEnvironment,
): MethodVisitor(ASM9) {
    override fun visitTypeInsn(opcode: Int, type: String) {
        fromInternalName(type)?.let(references::add)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        references.add(FieldReference(owner, name, descriptor))
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) {
        local?.asSequence()?.filterIsInstance<String>()?.mapNotNullTo(references, ::fromInternalName)
        stack?.asSequence()?.filterIsInstance<String>()?.mapNotNullTo(references, ::fromInternalName)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        references.add(MethodReference(owner, name, descriptor))
    }

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any?
    ) {
        references.add(fromHandle(bootstrapMethodHandle))
        bootstrapMethodArguments.forEach(::visitLdcInsn)
    }

    override fun visitLdcInsn(value: Any?) {
        when (value) {
            is Type -> {
                if (value.sort != Type.METHOD) {
                    fromType(value)?.let(references::add)
                } else {
                    value.argumentTypes.mapNotNullTo(references, ::fromType)
                    fromType(value.returnType)?.let(references::add)
                }
            }
            is Handle -> references.add(fromHandle(value))
            is ConstantDynamic -> {
                references.add(fromHandle(value.bootstrapMethod))
                for (i in 0 until value.bootstrapMethodArgumentCount)
                    visitLdcInsn(value.getBootstrapMethodArgument(i))
            }
        }
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        fromDescriptor(descriptor)?.let(references::add)
    }
}
