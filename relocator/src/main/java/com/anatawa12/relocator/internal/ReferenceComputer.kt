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
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.AbstractInsnNode.*

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
    collectReferencesOfInsnList(env, main.instructions, this)

    // additional: owner class
    fromInternalName(innerClasses.owner)?.let(::add)
}

internal fun computeReferencesOfField(
    env: ComputeReferenceEnvironment,
    main: FieldNode,
    innerClasses: InnerClassContainer,
) = buildSet<ClassReference> {
    Type.getType(main.desc).let(::fromType)?.let(::add)
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

internal fun collectReferencesOfInsnList(
    env: ComputeReferenceEnvironment,
    list: InsnList,
    references: MutableCollection<in Reference>,
) {
    fun processConstant(value: Any?, references: MutableCollection<in Reference>) {
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
                    processConstant(value.getBootstrapMethodArgument(i), references)
            }
        }
    }

    for (insnNode in list) {
        when (insnNode.type) {
            TYPE_INSN -> {
                insnNode as TypeInsnNode
                fromInternalName(insnNode.desc)?.let(references::add)
            }
            FIELD_INSN -> {
                insnNode as FieldInsnNode
                references.add(FieldReference(insnNode.owner, insnNode.name, insnNode.desc))
            }
            METHOD_INSN -> {
                insnNode as MethodInsnNode
                references.add(MethodReference(insnNode.owner, insnNode.name, insnNode.desc))
            }
            INVOKE_DYNAMIC_INSN -> {
                insnNode as InvokeDynamicInsnNode
                references.add(fromHandle(insnNode.bsm))
                insnNode.bsmArgs.forEach { processConstant(it, references) }
            }
            MULTIANEWARRAY_INSN -> {
                insnNode as MultiANewArrayInsnNode
                fromDescriptor(insnNode.desc)?.let(references::add)
            }
            FRAME -> {
                insnNode as FrameNode
                insnNode.local?.asSequence()?.filterIsInstance<String>()?.mapNotNullTo(references, ::fromInternalName)
                insnNode.stack?.asSequence()?.filterIsInstance<String>()?.mapNotNullTo(references, ::fromInternalName)
            }
        }
        ExtraReferenceDetector.detectExtraReference(insnNode)
    }
}

/**
 * The object (as a namespace) to resolve extra member references.
 * Currently, this handles reflection.
 */
internal object ExtraReferenceDetector {
    internal class InsnContainer private constructor(private var inner: AbstractInsnNode?) {
        fun get() = inner!!
        fun getOrNull() = inner
        fun getAndPrev() = inner.also { prev() }
        fun has() = inner != null
        fun prev() = apply {
            inner = inner?.prev
        }

        companion object {
            fun get(inner: AbstractInsnNode) = InsnContainer(inner)
        }
    }

    private val AbstractInsnNode.prev: AbstractInsnNode?
        get() {
            var cur: AbstractInsnNode? = previous
            while (cur != null && cur.type in setOf(LABEL, LINE))
                cur = cur.previous
            return cur
        }

    private fun descToInternalName(desc: String?): String? {
        desc ?: return null
        if (desc[0] != 'L' && desc[0] != '[') return null
        return desc.substring(1, desc.length - 1)
    }

    fun detectExtraReference(insnNode: AbstractInsnNode): Reference? {
        resolveOnStackClass(InsnContainer.get(insnNode))?.let { descriptor ->
            return fromDescriptor(descriptor)
        }
        if (insnNode.opcode == INVOKEVIRTUAL) {
            insnNode as MethodInsnNode
            if (insnNode.owner == "java/lang/Class"
                && insnNode.name == "getField"
                && insnNode.desc == "(L${"java/lang/String"};)L${"java/lang/reflect/Field"};"
            ) {
                val insn = InsnContainer.get(insnNode).prev()
                val fieldName = ldcString(insn) ?: return null
                val ownerClass = descToInternalName(resolveOnStackClass(insn)) ?: return null
                return FieldReference(ownerClass, fieldName, null)
            }
            if (insnNode.owner == "java/lang/Class"
                && insnNode.name == "getMethod"
                && insnNode.desc == "(L${"java/lang/String"};[L${"java/lang/Class"};)L${"java/lang/reflect/Method"};"
            ) {
                val insn = InsnContainer.get(insnNode).prev()
                val methodArgs = resolveOnStackClassArray(insn) ?: return null
                val methodName = ldcString(insn) ?: return null
                val ownerClass = descToInternalName(resolveOnStackClass(insn)) ?: return null
                return MethodReference(ownerClass, methodName, methodArgs.joinToString("", "(", ")"))
            }
        }
        return null
    }

    internal fun resolveOnStackClass(insn: InsnContainer): String? {
        when (insn.getOrNull()?.opcode ?: return null) {
            LDC -> {
                return ((insn.getAndPrev() as? LdcInsnNode)?.cst as? Type)
                    ?.takeIf { it.sort == Type.OBJECT || it.sort == Type.ARRAY }
                    ?.descriptor
            }
            GETSTATIC -> {
                val insnNode = insn.getAndPrev() as FieldInsnNode

                if (insnNode.name == "TYPE" && insnNode.desc == "Ljava/lang/Class;") {
                    when (insnNode.owner) {
                        "java/lang/Byte" -> return "B"
                        "java/lang/Short" -> return "S"
                        "java/lang/Integer" -> return "I"
                        "java/lang/Long" -> return "J"
                        "java/lang/Float" -> return "F"
                        "java/lang/Double" -> return "D"
                        "java/lang/Void" -> return "V"
                    }
                }
            }
            INVOKESTATIC -> {
                val insnNode = insn.getAndPrev() as MethodInsnNode
                if (insnNode.owner == "java/lang/Class"
                    && insnNode.name == "forName"
                ) {
                    when (insnNode.desc) {
                        "(L${"java/lang/String"};)L${"java/lang/Class"};" -> {
                            val ldc = ldcString(insn) ?: return null
                            return "L${ldc.replace('.', '/')};"
                        }
                        "(L${"java/lang/String"};BL${"java/lang/ClassLoader"};)L${"java/lang/Class"};" -> {
                            // skip ClassLoader and Boolean
                            skipValues(insn, 2) ?: return null
                            val ldc = ldcString(insn) ?: return null
                            return "L${ldc.replace('.', '/')};"
                        }
                    }
                }
            }
            INVOKEVIRTUAL, INVOKESPECIAL -> {
                val insnNode = insn.getAndPrev() as MethodInsnNode
                if (insnNode.owner == "java/lang/ClassLoader"
                    && insnNode.name == "loadClass"
                ) {
                    when (insnNode.desc) {
                        "(L${"java/lang/String"};)L${"java/lang/Class"};" -> {
                            val ldc = ldcString(insn) ?: return null
                            return "L${ldc.replace('.', '/')};"
                        }
                        "(L${"java/lang/String"};B)L${"java/lang/Class"};" -> {
                            // skip boolean
                            skipValues(insn, 1) ?: return null
                            val ldc = ldcString(insn) ?: return null
                            return "L${ldc.replace('.', '/')};"
                        }
                    }
                }
            }
        }
        return null
    }

    internal fun resolveOnStackClassArray(insn: InsnContainer): List<String>? {
        val classes = arrayListOf<String?>()

        while (insn.has() && insn.get().opcode == AASTORE) {
            insn.prev()
            val classDesc = resolveOnStackClass(insn) ?: return null
            val index = loadInt(insn) ?: return null
            if (insn.getAndPrev()?.opcode != DUP) return null
            while (classes.size <= index) classes.add(null)
            classes[index] = classDesc
        }

        if (!(insn.has() 
            && insn.get().opcode == ANEWARRAY 
            && (insn.get() as TypeInsnNode).desc == "java/lang/Class"))
            return null
        insn.prev()
        val size = loadInt(insn)
        if (size != classes.size) return null
        if (classes.any { it == null }) return null

        @Suppress("UNCHECKED_CAST")
        return classes as List<String>
    }

    private fun ldcString(insn: InsnContainer): String? =
        (insn.getAndPrev() as? LdcInsnNode)?.cst as? String

    private fun loadInt(insn: InsnContainer): Int? {
        val value: Int = when (insn.get().opcode) {
            in ICONST_M1..ICONST_5 -> insn.get().opcode - ICONST_0
            BIPUSH, SIPUSH -> (insn.get() as IntInsnNode).operand
            LDC -> (insn.getAndPrev() as? LdcInsnNode)?.cst as? Int ?: return null
            else -> return null
        }
        insn.prev()
        return value
    }

    private val diffs = ByteArray(256).apply {
        // those insn push one value
        this[ACONST_NULL] = -1
        this[ICONST_M1] = -1
        this[ICONST_0] = -1
        this[ICONST_1] = -1
        this[ICONST_2] = -1
        this[ICONST_3] = -1
        this[ICONST_4] = -1
        this[ICONST_5] = -1
        // those insn push one long, two sized value
        this[LCONST_0] = -2
        this[LCONST_1] = -2
        // those insn push one value
        this[FCONST_0] = -1
        this[FCONST_1] = -1
        this[FCONST_2] = -1
        // those insn push one double, two sized value
        this[DCONST_0] = -2
        this[DCONST_1] = -2
        // those insn push one value
        this[BIPUSH] = -1
        this[SIPUSH] = -1
        // local loads
        this[ILOAD] = -1
        this[LLOAD] = -2
        this[FLOAD] = -1
        this[DLOAD] = -2
        this[ALOAD] = -1
        // array loads. +2: loads two values(array + index integer)
        this[IALOAD] = +2 - 1
        this[LALOAD] = +2 - 2
        this[FALOAD] = +2 - 1
        this[DALOAD] = +2 - 2
        this[AALOAD] = +2 - 1
        this[BALOAD] = +2 - 1
        this[CALOAD] = +2 - 1
        this[SALOAD] = +2 - 1
        // local stores
        this[ISTORE] = +1
        this[LSTORE] = +2
        this[FSTORE] = +1
        this[DSTORE] = +2
        this[ASTORE] = +1
        // array loads. + 2: additionally loads two values(array + index integer)
        this[IASTORE] = +1 + 2
        this[LASTORE] = +2 + 2
        this[FASTORE] = +1 + 2
        this[DASTORE] = +2 + 2
        this[AASTORE] = +1 + 2
        this[BASTORE] = +1 + 2
        this[CASTORE] = +1 + 2
        this[SASTORE] = +1 + 2
        // stack operations
        this[POP] = +1
        this[POP2] = +2
        this[DUP] = +1 - 2
        this[DUP_X1] = +2 - 3
        this[DUP_X2] = +3 - 4
        this[DUP2] = +2 - 3
        this[DUP2_X1] = +3 - 4
        this[DUP2_X2] = +4 - 5
        this[SWAP] = +2 - 2
        // binary number operations
        this[IADD] = +2 - 1
        this[LADD] = +4 - 2
        this[FADD] = +2 - 1
        this[DADD] = +4 - 2
        this[ISUB] = +2 - 1
        this[LSUB] = +4 - 2
        this[FSUB] = +2 - 1
        this[DSUB] = +4 - 2
        this[IMUL] = +2 - 1
        this[LMUL] = +4 - 2
        this[FMUL] = +2 - 1
        this[DMUL] = +4 - 2
        this[IREM] = +2 - 1
        this[LREM] = +4 - 2
        this[FREM] = +2 - 1
        this[DREM] = +4 - 2
        this[IDIV] = +2 - 1
        this[LDIV] = +4 - 2
        this[FDIV] = +2 - 1
        this[DDIV] = +4 - 2
        // negative number operations
        this[INEG] = +1 - 1
        this[LNEG] = +2 - 2
        this[FNEG] = +1 - 1
        this[DNEG] = +2 - 2
        // shift operations
        this[ISHL] = +2 - 1
        this[LSHL] = +3 - 2
        this[ISHR] = +2 - 1
        this[LSHR] = +3 - 2
        this[IUSHR] = +2 - 1
        this[LUSHR] = +3 - 2
        // bit operations
        this[IAND] = +2 - 1
        this[LAND] = +4 - 2
        this[IOR] = +2 - 1
        this[LOR] = +4 - 2
        this[IXOR] = +2 - 1
        this[LXOR] = +4 - 2
        // cast operation
        this[I2L] = +1 - 2
        this[I2F] = +1 - 1
        this[I2D] = +1 - 2
        this[L2I] = +2 - 1
        this[L2F] = +2 - 1
        this[L2D] = +2 - 2
        this[F2I] = +1 - 1
        this[F2L] = +1 - 2
        this[F2D] = +1 - 2
        this[D2I] = +2 - 1
        this[D2L] = +2 - 2
        this[D2F] = +2 - 1
        this[I2B] = +1 - 1
        this[I2C] = +1 - 1
        this[I2S] = +1 - 1
        // compare operation
        this[LCMP] = +4 - 1
        this[FCMPL] = +2 - 1
        this[FCMPG] = +2 - 1
        this[DCMPL] = +4 - 1
        this[DCMPG] = +4 - 1
        // object operations
        this[NEW] = -1
        this[NEWARRAY] = +1 - 1
        this[ANEWARRAY] = +1 - 1
        this[ARRAYLENGTH] = +1 - 1
        this[INSTANCEOF] = +1 - 1
    }

    private fun skipValues(insn: InsnContainer, onStackSize: Int): Unit? {
        fun valueSizeOfField(insn: AbstractInsnNode): Int {
            insn as FieldInsnNode
            return if (insn.desc[0] == 'D' && insn.desc[0] == 'J') 2 else 1
        }
        fun stackDiffOfStaticMethodDesc(desc: String): Int {
            val sizes = Type.getArgumentsAndReturnSizes(desc)
            val args = sizes shr 2
            val ret = sizes and 0b11
            return args - ret
        }
        fun stackDiffOfVirtualMethod(insn: AbstractInsnNode): Int =
            stackDiffOfStaticMethodDesc((insn as MethodInsnNode).desc)
        fun stackDiffOfIndy(insn: AbstractInsnNode): Int =
            stackDiffOfStaticMethodDesc((insn as InvokeDynamicInsnNode).desc)

        // subtract: store
        // addition: load/pop
        var toBeRemoved = onStackSize
        while (insn.has() && toBeRemoved > 0) {
            toBeRemoved += diffs
                .getOrNull(insn.get().opcode)
                ?.toInt()
                ?.takeUnless { it == 0 }
                ?: when (insn.get().opcode) {
                    NOP -> 0
                    // LDC
                    LDC -> when ((insn.get() as LdcInsnNode).cst::class) {
                        // one sized values
                        Int::class -> -1
                        Float::class -> -1
                        String::class -> -1
                        Type::class -> -1
                        Handle::class -> -1
                        ConstantDynamic::class -> -1
                        // two sized values
                        Double::class -> -2
                        Long::class -> -2
                        else -> return null
                    }
                    IINC -> 0
                    GETSTATIC -> -valueSizeOfField(insn.get())
                    PUTSTATIC -> +valueSizeOfField(insn.get())
                    GETFIELD -> +1 - valueSizeOfField(insn.get())
                    PUTFIELD -> +1 + valueSizeOfField(insn.get())
                    INVOKEVIRTUAL -> +stackDiffOfVirtualMethod(insn.get())
                    INVOKESPECIAL -> +stackDiffOfVirtualMethod(insn.get())
                    INVOKESTATIC -> +stackDiffOfVirtualMethod(insn.get()) - 1 // remove this arg
                    INVOKEINTERFACE -> +stackDiffOfVirtualMethod(insn.get())
                    INVOKEDYNAMIC -> +stackDiffOfIndy(insn.get())
                    CHECKCAST -> 0
                    else -> return null
                }
            insn.prev()
        }
        return Unit
    }
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

    override fun visitArray(name: String?): AnnotationVisitor {
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

        fun acceptAnnotation(
            visitor: ClassRefCollectingAnnotationVisitor,
            annotation: AnnotationNode,
        ) {
            fromDescriptor(annotation.desc)?.let(visitor.references::add)
            annotation.accept(visitor)
        }

        fun acceptAnnotations(
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            annotations: List<AnnotationNode>?,
        ) {
            if (annotations == null) return
            val visitor = ClassRefCollectingAnnotationVisitor(references, env)
            annotations.forEach { acceptAnnotation(visitor, it) }
        }

        fun acceptAnnotations(
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            annotations: Array<List<AnnotationNode>?>?,
        ) {
            if (annotations == null) return
            val visitor = ClassRefCollectingAnnotationVisitor(references, env)
            for (annotationNodes in annotations) {
                annotationNodes?.forEach { acceptAnnotation(visitor, it) }
            }
        }
    }
}

