@file:JvmName("ReferenceComputer")
package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.diagnostic.Diagnostic
import com.anatawa12.relocator.diagnostic.DiagnosticHandler
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_CLASS
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_INNER_CLASS
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_REFLECTION_CLASS
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_REFLECTION_FIELD
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_REFLECTION_METHOD
import com.anatawa12.relocator.internal.ClassRefCollectingAnnotationVisitor.acceptAnnotations
import com.anatawa12.relocator.internal.ClassRefCollectingAnnotationVisitor.acceptValue
import com.anatawa12.relocator.internal.ClassRefCollectingSignatureVisitor.Utils.acceptSignature
import com.anatawa12.relocator.reference.*
import com.anatawa12.relocator.reflect.ReflectionMappingContainer
import com.google.common.annotations.VisibleForTesting
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.util.*

internal class ComputeReferenceEnvironment(
    val keepRuntimeInvisibleAnnotation: Boolean,
    val reflectionMap: ReflectionMappingContainer,
    val classpath: CombinedClassPath,
    val addDiagnostic: DiagnosticHandler,
)

internal fun computeReferencesOfClass(
    env: ComputeReferenceEnvironment,
    main: ClassFile,
) = buildSet<Reference> {
    acceptSignature(this, env, main.innerClassesContainer, main.signature, Location.Class(main.name))
    main.superName?.let(::add)
    main.outerClass?.let(::add)
    // super interfaces is not required to be exists
    //main.interfaces.let(::addAll)
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
    }
    main.nestHostClass?.let(::add)
    // nAnyest member classes are not required to exist
    //main.nestMembers.let(::addAll)
    // permitted subclasses are not required to exist
    //main.permittedSubclasses.let(::addAll)

    // record support
    if (main.access.hasFlag(ACC_RECORD)) {
        // record components (including default constructor) will be keep
        // if this class will be kept
        for (recordField in main.recordFields)
            add(RecordFieldReference(main.name, recordField.name, recordField.descriptor))
        val recordDefaultCtor = "(${main.recordFields.joinToString("") { it.descriptor }})V"
        add(MethodReference(main.name, "<init>", recordDefaultCtor))
    }

    // other special implementations

    // if static initializer exists, it should be kept
    if (main.methods.any { it.name == "<clinit>" }) {
        add(MethodReference(main.name, "<clinit>", "()V"))
    }
}

internal suspend fun computeReferencesOfMethod(
    env: ComputeReferenceEnvironment,
    main: ClassMethod,
) = buildSet<Reference> {
    val owner = main.owner
    Type.getArgumentTypes(main.descriptor).mapNotNullTo(this, ::newReference)
    Type.getReturnType(main.descriptor).let(::newReference)?.let(::add)
    acceptSignature(this, env, owner.innerClassesContainer, main.signature, Location.Method(main))
    main.exceptions.let(::addAll)
    acceptValue(this, main.annotationDefault)
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    acceptAnnotations(this, env, main.visibleParameterAnnotations)
    main.classCode?.visibleLocalVariableAnnotations?.let { acceptAnnotations(this, env, it) }
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
        acceptAnnotations(this, env, main.invisibleParameterAnnotations)
        main.classCode?.invisibleLocalVariableAnnotations?.let { acceptAnnotations(this, env, it) }
    }
    main.classCode?.let { computeReferencesOfClassCode(env, it) }

    // additional: owner class
    newReference(owner.name)?.let(::add)
    // additional: parent class's method to current one
    if (main.name != "<init>" && main.name != "<clinit>" && (main.access and ACC_PRIVATE) == 0) {
        val refToThisMethod = MethodReference(owner.name, main.name, main.descriptor)
        ParentClasses(env, owner).forEach { parentClass ->
            val parentMethod = parentClass.findMethod(main.name, main.descriptor) ?: return@forEach true
            parentMethod.externalReferences += refToThisMethod
            false
        }
    }
}

internal suspend fun computeReferencesOfField(
    env: ComputeReferenceEnvironment,
    main: ClassField,
) = buildSet<ClassReference> {
    val owner = main.owner
    Type.getType(main.descriptor).let(::newReference)?.let(::add)
    acceptSignature(this, env, owner.innerClassesContainer, main.signature, Location.Field(main))
    acceptValue(this, main.value)
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
    }

    // additional: owner class
    newReference(owner.name)?.let(::add)
    // additional: parent class's field to current one
    val refToThisField = FieldReference(owner.name, main.name, main.descriptor)
    ParentClasses(env, owner).forEach { parentClass ->
        val parentField = parentClass.findField(main.name, main.descriptor)  ?: return@forEach true
        parentField.externalReferences += refToThisField
        false
    }
}

@Suppress("RedundantSuspendModifier")
internal suspend fun computeReferencesOfRecordField(
    env: ComputeReferenceEnvironment,
    main: ClassRecordField,
) = buildSet<Reference> {
    val owner = main.owner
    main.descriptor.let(::newReferenceDesc)?.let(::add)
    acceptSignature(this,
        env,
        owner.innerClassesContainer,
        main.signature,
        Location.RecordField(main))
    acceptAnnotations(this, env, main.visibleAnnotations)
    acceptAnnotations(this, env, main.visibleTypeAnnotations)
    add(MethodReference(main.name, main.name, "()${main.descriptor}"))
    if (env.keepRuntimeInvisibleAnnotation) {
        acceptAnnotations(this, env, main.invisibleAnnotations)
        acceptAnnotations(this, env, main.invisibleTypeAnnotations)
    }
}

@Suppress("RedundantSuspendModifier")
internal suspend fun computeReferencesOfClassCode(
    env: ComputeReferenceEnvironment,
    main: ClassCode,
) = buildSet<Reference> {
    for (tryCatchBlock in main.tryCatchBlocks) {
        tryCatchBlock.type?.let(::add)
        acceptAnnotations(this, env, tryCatchBlock.visibleAnnotations)
        if (env.keepRuntimeInvisibleAnnotation) {
            acceptAnnotations(this, env, tryCatchBlock.invisibleAnnotations)
        }
    }
    main.localVariables.forEach { localVariable ->
        localVariable.descriptor.let(::newReferenceDesc)?.let(::add)
        acceptSignature(this, env, main.owner.owner.innerClassesContainer, localVariable.signature,
            Location.MethodLocal(localVariable))
    }
    collectReferencesOfInsnList(env, main.instructions, this, Location.Method(main.owner))
}

fun processConstant(value: Constant, references: MutableCollection<in Reference>) {
    when (value) {
        is ConstantMethodType -> {
            val method = Type.getType(value.descriptor)
            method.argumentTypes.mapNotNullTo(references, ::newReference)
            newReference(method.returnType)?.let(references::add)
        }
        is ConstantClass -> {
            newReference(Type.getType(value.descriptor))?.let(references::add)
        }
        is ConstantDynamic -> {
            val method = Type.getType(value.descriptor)
            method.argumentTypes.mapNotNullTo(references, ::newReference)
            newReference(method.returnType)?.let(references::add)
            processConstant(value.bootstrapMethod, references)
            for (arg in value.args)
                processConstant(arg, references)
        }
        is ConstantFieldHandle -> references.add(value.field)
        is ConstantMethodHandle -> references.add(value.method)
        is ConstantDouble -> {}
        is ConstantFloat -> {}
        is ConstantInt -> {}
        is ConstantLong -> {}
        is ConstantString -> {}
    }
}

internal fun collectReferencesOfInsnList(
    env: ComputeReferenceEnvironment,
    list: InsnList,
    references: MutableCollection<in Reference>,
    location: Location,
) {
    val definedLabels = hashSetOf<CodeLabel>()
    val backJumpLabels = hashSetOf<CodeLabel>()
    for (insn in list) {
        definedLabels += insn.labelsToMe
        when (insn::class) {
            SimpleInsn::class -> {}
            TypedInsn::class -> {}
            CastInsn::class -> {}
            VarInsn::class -> {}
            RetInsn::class -> {}
            TypeInsn::class -> references.add((insn as TypeInsn).type)
            FieldInsn::class -> references.add((insn as FieldInsn).field)
            MethodInsn::class -> references.add((insn as MethodInsn).method)
            InvokeDynamicInsn::class -> processConstant((insn as InvokeDynamicInsn).target, references)
            JumpInsn::class -> {
                if ((insn as JumpInsn).target in definedLabels)
                    backJumpLabels += insn.target
            }
            LdcInsn::class -> processConstant((insn as LdcInsn).value, references)

            IIncInsn::class -> {}
            TableSwitchInsn::class -> {
                for (label in (insn as TableSwitchInsn).labels) {
                    if (label in definedLabels)
                        backJumpLabels += label
                }
                if (insn.default in definedLabels)
                    backJumpLabels += insn.default
            }
            LookupSwitchInsn::class -> {
                for ((_, label) in (insn as LookupSwitchInsn).labels) {
                    if (label in definedLabels)
                        backJumpLabels += label
                }
                if (insn.default in definedLabels)
                    backJumpLabels += insn.default
            }
            MultiANewArrayInsn::class -> references.add((insn as MultiANewArrayInsn).type)
        }
    }

    ExtraReferenceDetector(
        list.owner.owner.access and ACC_STATIC != 0,
        list.owner.owner.descriptor,
        list.owner.maxLocals,
        env, location, list, references, backJumpLabels,
    ).collectExtraReferences()
}

// TODO: support user defined extra references

internal class ExtraReferenceDetector(
    isStatic: Boolean,
    methodDescriptor: String,
    maxLocals: Int,
    val env: ComputeReferenceEnvironment,
    val location: Location,
    val list: List<Insn>,
    val references: MutableCollection<in Reference>,
    val backJumpLabels: Set<CodeLabel>,
) {
    private val framesByLabel = hashMapOf<CodeLabel, StackFrame>()
    private var frame: StackFrame? = StackFrame.init(isStatic, methodDescriptor, maxLocals)

    internal fun collectExtraReferences() {
        for (insn in list) {
            for (codeLabel in insn.labelsToMe) {
                val newFrame = framesByLabel[codeLabel] ?: continue
                if (frame == null) frame = newFrame.clone()
                else mergeFrame(frame!!, newFrame)
            }
            if (frame == null) continue
            insn.frame?.let { verifyFrame(frame!!, it) }
            if (insn.labelsToMe.any { it in backJumpLabels })
                frame!!.underBackJump = true
            runInsn(insn)
        }
    }

    private fun verifyFrame(stack: StackFrame, code: CodeFrame) {
        when (code) {
            is FullFrame -> {
                check(stack.stacks.size == code.stacks.size) { "frame verification" }
                for ((stackV, codeV) in stack.stacks.zip(code.stacks)) {
                    when (codeV) {
                        FrameElement.Double,
                        FrameElement.Long,
                        -> check(!stackV.isOneWord) { "frame verification" }
                        else -> check(stackV.isOneWord){ "frame verification" }
                    }
                }
            }
            SameFrame -> {
                check(stack.stacks.isEmpty()) { "frame verification" }
            }
            is Same1Frame -> {
                when (code.stack) {
                    FrameElement.Double,
                    FrameElement.Long,
                    -> check(stack.stacks.singleOrNull()?.isOneWord == false) { "frame verification" }
                    else -> check(stack.stacks.singleOrNull()?.isOneWord == true){ "frame verification" }
                }
            }
            is AppendFrame -> {
                check(stack.stacks.isEmpty()) { "frame verification" }
            }
            is ChopFrame -> {
                check(stack.stacks.isEmpty()) { "frame verification" }
            }
        }
    }

    private fun pop(n: Int) = repeat(n) { frame!!.stacks.removeLast() }
    @VisibleForTesting
    fun pop() = frame!!.stacks.removeLast()
    private fun pop1Word(): Any {
        val v1 = pop()
        check(v1.isOneWord)
        return v1
    }
    private fun pop2Word(): Pair<Any, Any?> {
        val v1 = pop()
        if (v1.isOneWord) {
            val v2 = pop()
            check(v1.isOneWord)
            return v1 to v2
        }
        return v1 to null
    }
    private fun push(push: Any) {
        frame!!.stacks.add(push)
    }
    private fun push(push0: Any, push1: Any) {
        frame!!.stacks.add(push0)
        frame!!.stacks.add(push1)
    }
    private fun push(push: Pair<Any, Any?>) {
        val second = push.second
        if (second != null) push(second)
        push(push.first)
    }
    private fun replace(push: Any) {
        frame!!.stacks[frame!!.stacks.lastIndex] = push
    }
    private fun replace2(push: Any) {
        pop()
        frame!!.stacks[frame!!.stacks.lastIndex] = push
    }

    private fun runInsn(insn: Insn) {
        when (insn::class) {
            SimpleInsn::class -> when ((insn as SimpleInsn).insn) {
                SimpleInsnType.NOP -> {}
                SimpleInsnType.ACONST_NULL -> push(NULL)
                SimpleInsnType.POP -> pop1Word()
                SimpleInsnType.POP2 -> pop2Word()
                SimpleInsnType.DUP -> pop1Word().also { push(it, it) }
                SimpleInsnType.DUP_X1 -> {
                    val v1 = pop1Word()
                    val v2 = pop1Word()
                    push(v1)
                    push(v2)
                    push(v1)
                }
                SimpleInsnType.DUP_X2 -> {
                    val v1 = pop1Word()
                    val v2 = pop2Word()
                    push(v1)
                    push(v2)
                    push(v1)
                }
                SimpleInsnType.DUP2 -> {
                    val v1 = pop2Word()
                    push(v1)
                    push(v1)
                }
                SimpleInsnType.DUP2_X1 -> {
                    val v1 = pop2Word()
                    val v2 = pop1Word()
                    push(v1)
                    push(v2)
                    push(v1)
                }
                SimpleInsnType.DUP2_X2 -> {
                    val v1 = pop2Word()
                    val v2 = pop2Word()
                    push(v1)
                    push(v2)
                    push(v1)
                }
                SimpleInsnType.SWAP -> {
                    val v1 = pop1Word()
                    val v2 = pop1Word()
                    push(v1)
                    push(v2)
                }
                SimpleInsnType.LCMP -> replace2(Word.Single)
                SimpleInsnType.FCMPL -> replace2(Word.Single)
                SimpleInsnType.FCMPG -> replace2(Word.Single)
                SimpleInsnType.DCMPL -> replace2(Word.Single)
                SimpleInsnType.DCMPG -> replace2(Word.Single)
                SimpleInsnType.RETURN -> frame = null
                SimpleInsnType.ARRAYLENGTH -> replace(Word.Single)
                SimpleInsnType.ATHROW -> frame = null
                SimpleInsnType.MONITORENTER -> pop()
                SimpleInsnType.MONITOREXIT -> pop()
            }
            TypedInsn::class -> when ((insn as TypedInsn).insn) {
                TypedInsnType.ALOAD -> {
                    val index = pop()
                    val array = pop()
                    if (index !is Int || array !is MutableList<*> || index !in array.indices) {
                        return push(insn.type.toWord())
                    }
                    push(array[index]!!)
                }
                TypedInsnType.ASTORE -> {
                    val value = pop()
                    val index = pop()
                    val array = pop()
                    if (index !is Int || array !is MutableList<*> || index !in array.indices) {
                        return
                    }
                    @Suppress("UNCHECKED_CAST")
                    (array as MutableList<Any>)[index] = value
                }
                TypedInsnType.ADD -> replace2(insn.type.toWord())
                TypedInsnType.SUB -> replace2(insn.type.toWord())
                TypedInsnType.MUL -> replace2(insn.type.toWord())
                TypedInsnType.DIV -> replace2(insn.type.toWord())
                TypedInsnType.REM -> replace2(insn.type.toWord())
                TypedInsnType.NEG -> replace(insn.type.toWord())
                TypedInsnType.SHL -> replace2(insn.type.toWord())
                TypedInsnType.SHR -> replace2(insn.type.toWord())
                TypedInsnType.USHR -> replace2(insn.type.toWord())
                TypedInsnType.AND -> replace2(insn.type.toWord())
                TypedInsnType.OR -> replace2(insn.type.toWord())
                TypedInsnType.XOR -> replace2(insn.type.toWord())
                TypedInsnType.RETURN -> frame = null
                TypedInsnType.NEWARRAY -> {
                    val count = pop()
                    if (count !is Int || count !in 0..100)
                        return push(Word.Single)
                    val value = when (insn.type) {
                        VMType.Int -> MutableList<Any>(count) { 0 }
                        VMType.Long -> MutableList<Any>(count) { 0L }
                        VMType.Float -> MutableList<Any>(count) { 0f }
                        VMType.Double -> MutableList<Any>(count) { .0 }
                        VMType.Byte -> MutableList<Any>(count) { 0.toByte() }
                        VMType.Char -> MutableList<Any>(count) { 0.toChar() }
                        VMType.Short -> MutableList<Any>(count) { 0.toShort() }
                        VMType.Boolean -> MutableList<Any>(count) { false }
                        else -> assertError("")
                    }
                    push(value)
                }
            }
            CastInsn::class -> replace((insn as CastInsn).to.toWord())
            VarInsn::class -> {
                val frame = frame!!
                when ((insn as VarInsn).insn) {
                    VarInsnType.LOAD -> push(frame.locals[insn.variable] ?: insn.type.toWord())
                    VarInsnType.STORE -> frame.locals[insn.variable] = pop()
                }
            }
            RetInsn::class -> frame!!.locals[(insn as RetInsn).variable] = null
            TypeInsn::class -> when ((insn as TypeInsn).insn) {
                TypeInsnType.NEW -> push(Word.Single)
                TypeInsnType.ANEWARRAY -> {
                    val count = pop()
                    if (count !is Int || count !in 0..100) {
                        push(Word.Single)
                        return
                    }
                    push(MutableList(count) { NULL })
                }
                TypeInsnType.CHECKCAST -> push(pop())
                TypeInsnType.INSTANCEOF -> replace(Word.Single)
            }
            FieldInsn::class -> {
                insn as FieldInsn
                val put = insn.insn == FieldInsnType.PUTFIELD || insn.insn == FieldInsnType.PUTSTATIC
                val hasSelf = insn.insn == FieldInsnType.PUTFIELD || insn.insn == FieldInsnType.GETFIELD
                if (put) {
                    pop() // value
                    if (hasSelf) pop() // self
                } else {
                    val self = if (hasSelf) pop() else null
                    push(processExtraReference(insn.field, self) ?: Word.from(insn.field.descriptor)!!) // value
                }
            }
            MethodInsn::class -> {
                insn as MethodInsn
                val (params, returns) = popAsDescriptorAndReturnWord(insn.method.descriptor)
                val self = if (insn.insn != MethodInsnType.INVOKESTATIC) pop() else null
                if (returns == null) return
                push(processExtraReference(insn.method, self, params) ?: returns)
            }
            InvokeDynamicInsn::class -> {
                insn as InvokeDynamicInsn
                popAsDescriptorAndReturnWord(insn.target.descriptor).second?.let(::push)
            }
            JumpInsn::class -> {
                when ((insn as JumpInsn).insn) {
                    JumpInsnType.IFEQ -> pop(1)
                    JumpInsnType.IFNE -> pop(1)
                    JumpInsnType.IFLT -> pop(1)
                    JumpInsnType.IFGE -> pop(1)
                    JumpInsnType.IFGT -> pop(1)
                    JumpInsnType.IFLE -> pop(1)
                    JumpInsnType.IF_ICMPEQ -> pop(2)
                    JumpInsnType.IF_ICMPNE -> pop(2)
                    JumpInsnType.IF_ICMPLT -> pop(2)
                    JumpInsnType.IF_ICMPGE -> pop(2)
                    JumpInsnType.IF_ICMPGT -> pop(2)
                    JumpInsnType.IF_ICMPLE -> pop(2)
                    JumpInsnType.IF_ACMPEQ -> pop(2)
                    JumpInsnType.IF_ACMPNE -> pop(2)
                    JumpInsnType.GOTO -> Unit
                    JumpInsnType.JSR -> push(Word.Single)
                    JumpInsnType.IFNULL -> pop()
                    JumpInsnType.IFNONNULL -> pop()
                }
                setFrame(framesByLabel, insn.target)
                if (insn.insn == JumpInsnType.GOTO || insn.insn == JumpInsnType.JSR) {
                    frame = null
                }
            }
            LdcInsn::class -> push((insn as LdcInsn).value.toFV())
            IIncInsn::class -> frame!!.locals[(insn as IIncInsn).variable] = Word.Single
            TableSwitchInsn::class -> {
                insn as TableSwitchInsn
                pop()
                setFrame(framesByLabel, insn.default)
                for (label in insn.labels)
                    setFrame(framesByLabel, label)
                frame = null
            }
            LookupSwitchInsn::class -> {
                insn as LookupSwitchInsn
                pop()
                setFrame(framesByLabel, insn.default)
                for ((_, label) in insn.labels)
                    setFrame(framesByLabel, label)
                frame = null
            }
            MultiANewArrayInsn::class -> {
                insn as MultiANewArrayInsn
                val counts = List(insn.dimensions) { pop() }.asReversed()
                if (counts.any { it !is Int }) {
                    push(Word.Single)
                    return
                }
                @Suppress("UNCHECKED_CAST")
                counts as List<Int>
                val initValue = when (insn.type.name[insn.dimensions]) {
                    'Z' -> false
                    'C' -> 0.toChar()
                    'B' -> 0.toByte()
                    'S' -> 0.toShort()
                    'I' -> 0
                    'F' -> 0f
                    'J' -> 0L
                    'D' -> .0
                    'L' -> NULL
                    '[' -> NULL
                    else -> error("unsupported descriptor: ${insn.type}")
                }
                fun newArray(counts: List<Int>, dim: Int, init: Any): MutableList<Any> {
                    return if (dim != counts.lastIndex)
                        MutableList(counts[dim]) { newArray(counts, dim + 1, init) }
                    else
                        MutableList(counts[dim]) { init }
                }
                push(newArray(counts, 0, initValue))
            }
        }
    }

    private fun processExtraReference(field: FieldReference, @Suppress("UNUSED_PARAMETER") self: Any?): Any? {
        if (field.name == "TYPE" && field.descriptor == "L${"java/lang/Class"};") {
            when (field.owner.name) {
                "java/lang/Void" -> return ConstantClass("V")
                "java/lang/Integer" -> return ConstantClass("I")
                "java/lang/Long" -> return ConstantClass("J")
                "java/lang/Float" -> return ConstantClass("F")
                "java/lang/Double" -> return ConstantClass("D")
                "java/lang/Byte" -> return ConstantClass("B")
                "java/lang/Character" -> return ConstantClass("C")
                "java/lang/Short" -> return ConstantClass("S")
                "java/lang/Boolean" -> return ConstantClass("Z")
            }
        }
        return null
    }

    private fun addDiagnostic(diagnostic: Diagnostic): Nothing? {
        env.addDiagnostic(diagnostic)
        return null
    }

    private fun tryResolveClass(name: Any?): Any? {
        if (name !is String) return addDiagnostic(UNRESOLVABLE_REFLECTION_CLASS(location))
        val internalName = name.replace('.', '/')
        references.add(ClassReference(internalName).withLocation(location))
        return ConstantClass("L$internalName;")
    }

    private fun processExtraReference(method: MethodReference, self: Any?, args: List<Any>): Any? {
        when (method.owner.name) {
            "java/lang/ClassLoader" -> when (method.name) {
                "loadClass" -> {
                    when (method.descriptor) {
                        "(L${"java/lang/String"};)L${"java/lang/Class"};" ->
                            return tryResolveClass(args[0])
                        "(L${"java/lang/String"};B)L${"java/lang/Class"};" ->
                            return tryResolveClass(args[0])
                    }
                }
            }
            "java/lang/Class" -> when (method.name) {
                "forName" -> when (method.descriptor) {
                    "(L${"java/lang/Module"};L${"java/lang/String"};)L${"java/lang/Class"};" ->
                        return tryResolveClass(args[1])
                    "(L${"java/lang/String"};)L${"java/lang/Class"};" ->
                        return tryResolveClass(args[0])
                    "(L${"java/lang/String"};BL${"java/lang/ClassLoader"};)L${"java/lang/Class"};" ->
                        return tryResolveClass(args[0])
                }
                "getField" -> when (method.descriptor) {
                    "(L${"java/lang/String"};)L${"java/lang/reflect/Field"};" -> {
                        val selfClass = (self as? ConstantClass)?.descriptor?.let(::newReferenceDesc)
                        val name = args[0] as? String
                        if (selfClass == null || name == null)
                            return addDiagnostic(UNRESOLVABLE_REFLECTION_FIELD(location))
                        references.add(PartialFieldReference(selfClass, name))
                    }
                }
                "getMethod" -> when (method.descriptor) {
                    "(L${"java/lang/String"};[L${"java/lang/Class"};)L${"java/lang/reflect/Method"};" -> {
                        val selfClass = (self as? ConstantClass)?.descriptor?.let(::newReferenceDesc)
                        val name = args[0] as? String
                        val argTypes = args[0] as? MutableList<*>
                        if (selfClass == null || name == null || argTypes == null)
                            return addDiagnostic(UNRESOLVABLE_REFLECTION_METHOD(location))
                        if (argTypes.any { it !is ConstantClass })
                            return addDiagnostic(UNRESOLVABLE_REFLECTION_METHOD(location))
                        references.add(PartialMethodReference(selfClass, name,
                            descriptor = argTypes.joinToString("", "(", ")") {
                                (it as ConstantClass).descriptor
                            }))
                    }
                }
            }
        }
        return null
    }

    private fun setFrame(
        framesByLabel: HashMap<CodeLabel, StackFrame>,
        label: CodeLabel,
    ) {
        val frame = frame!!
        val oldFrame = framesByLabel[label]
        if (oldFrame == null){
            framesByLabel[label] = frame.clone()
            return
        }
        mergeFrame(oldFrame, frame)
    }

    private fun mergeFrame(mergeTo: StackFrame, adds: StackFrame) {
        require(mergeTo.stacks.size == adds.stacks.size) { "merge frame failed" }
        require(mergeTo.locals.size == adds.locals.size) { "merge frame failed" }
        merge(mergeTo.stacks, adds.stacks, true)
        merge(mergeTo.locals, adds.locals, false)
    }

    private fun <T> merge(mergeTo: MutableList<T>, adds: MutableList<T>, stack: Boolean) {
        val iter0 = mergeTo.listIterator()
        val iter1 = adds.iterator()
        while (iter0.hasNext()) {
            val one = iter0.next()
            val two = iter1.next()
            when {
                one == two -> Unit
                one == null -> iter0.set(one)
                two == null -> iter0.set(two)
                else -> {
                    val oneType = one.toWord()
                    val twoType = two.toWord()
                    if (stack) {
                        check(oneType == twoType)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        if (oneType.isOneWord != twoType.isOneWord) iter0.set(null as T)
                        else iter0.set(oneType as T)
                    }
                }
            }
        }
    }

    private fun popAsDescriptorAndReturnWord(descriptor: String): Pair<List<Any>, Word?> {
        val frame = frame!!
        val type = Type.getType(descriptor)
        val parameters = List(type.argumentTypes.size) {
            frame.stacks.removeLast() // pop arguments
        }.asReversed()
        return parameters to Word.from(type.returnType.descriptor)
    }

    // Word for unknowns
    // Constant for known value
    private class StackFrame(
        val locals: MutableList<Any?>,
        val stacks: MutableList<Any>,
    ) {
        var underBackJump = false
        fun clone() = StackFrame(clone(locals), clone(stacks))

        companion object {
            @Suppress("UNCHECKED_CAST")
            private fun <T> clone(values: MutableList<T>) = values.mapTo(arrayListOf()) {
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // compiler bug
                when (it) {
                    null -> it
                    is MutableList<*> -> it.toMutableList() as T
                    else -> when (it!!::class) {
                        IntArray::class -> (it as IntArray).clone() as T
                        LongArray::class -> (it as LongArray).clone() as T
                        FloatArray::class -> (it as FloatArray).clone() as T
                        DoubleArray::class -> (it as DoubleArray).clone() as T
                        ByteArray::class -> (it as ByteArray).clone() as T
                        CharArray::class -> (it as CharArray).clone() as T
                        ShortArray::class -> (it as ShortArray).clone() as T
                        BooleanArray::class -> (it as BooleanArray).clone() as T
                        else -> it
                    }
                }
            }
            
            fun init(
                isStatic: Boolean, // null: static, others for instance method
                methodDescriptor: String, 
                maxLocals: Int,
            ): StackFrame {
                val types = Type.getType(methodDescriptor).argumentTypes
                    .mapTo(arrayListOf<Any?>()) { Word.from(it.descriptor) }
                if (!isStatic) types.add(0, Word.Single)
                while (types.size < maxLocals) types.add(null)
                return StackFrame(types, arrayListOf())
            }
        }
    }
    companion object {
        @JvmStatic
        private fun VMType.toWord(): Word = when (this) {
            VMType.Int -> Word.Single
            VMType.Long -> Word.Double
            VMType.Float -> Word.Single
            VMType.Double -> Word.Double
            VMType.Reference -> Word.Single
            VMType.Byte -> Word.Single
            VMType.Char -> Word.Single
            VMType.Short -> Word.Single
            VMType.Boolean -> Word.Single
        }

        @JvmStatic
        private fun Any.toWord(): Word = when (this) {
            is Long -> Word.Double
            is Double -> Word.Double
            is Word -> this
            else -> Word.Single
        }

        @JvmStatic
        private fun Constant.toFV(): Any = when (this::class) {
            ConstantInt::class -> (this as ConstantInt).value
            ConstantLong::class -> (this as ConstantLong).value
            ConstantFloat::class -> (this as ConstantFloat).value
            ConstantDouble::class -> (this as ConstantDouble).value
            ConstantString::class -> (this as ConstantString).value
            ConstantClass::class -> this
            ConstantMethodType::class -> this
            ConstantFieldHandle::class -> this
            ConstantMethodHandle::class -> this
            ConstantDynamic::class -> this
            else -> assertError("unknwon constant type: ${this::class}")
        }

        @JvmStatic
        private val NULL = Any()
        @JvmStatic
        private val Any.isOneWord get() = this != Word.Double && this !is Long && this !is Double
    }

    private sealed class Word {
        object Single : Word()
        object Double : Word()
        companion object {
            fun from(descriptor: String): Word? = when (descriptor[0]) {
                'V' -> null
                'Z' -> Single
                'C' -> Single
                'B' -> Single
                'S' -> Single
                'I' -> Single
                'F' -> Single
                'J' -> Double
                'D' -> Double
                'L' -> Single
                '[' -> Single
                else -> error("unsupported descriptor: $descriptor")
            }
        }
    }
}

internal class ClassRefCollectingSignatureVisitor private constructor(
    val references: MutableCollection<in ClassReference>,
    val env: ComputeReferenceEnvironment,
    val innerClasses: InnerClassContainer,
    val location: Location,
) : SignatureVisitor(ASM9) {
    private val child by lazy(LazyThreadSafetyMode.NONE) {
        ClassRefCollectingSignatureVisitor(references, env, innerClasses, location)
    }

    private var classType: ClassReference? = null

    override fun visitClassType(name: String) {
        classType = ClassReference(name)
    }

    override fun visitInnerClassType(name: String) {
        classType = classType?.let { classType ->
            val foundInner = innerClasses.findInner(classType, name)
            if (foundInner == null)
                env.addDiagnostic(UNRESOLVABLE_INNER_CLASS(classType.name, name, location))
            foundInner
        }
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor = child

    override fun visitEnd() {
        classType?.let { classType ->
            references.add(classType)
        }
        classType = null
    }

    companion object Utils {
        fun acceptSignature(
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            innerClasses: InnerClassContainer,
            signature: String?,
            location: Location,
        ) {
            if (signature != null) {
                SignatureReader(signature)
                    .accept(ClassRefCollectingSignatureVisitor(references, env, innerClasses, location))
            }
        }
    }
}

internal object ClassRefCollectingAnnotationVisitor {
    fun acceptValue(references: MutableCollection<in ClassReference>, value: Constant?) {
        if (value is ConstantClass)
            newReference(Type.getType(value.descriptor))?.let(references::add)
    }

    fun acceptValue(references: MutableCollection<in ClassReference>, value: AnnotationValue?) {
        if (value is ClassAnnotation)
            references.add(value.annotationClass)
        if (value is AnnotationClass)
            newReference(Type.getType(value.descriptor))?.let(references::add)
        if (value is AnnotationEnum)
            references.add(value.owner)
    }

    fun acceptValue(
        references: MutableCollection<in ClassReference>,
        env: ComputeReferenceEnvironment,
        value: AnnotationValue,
    ): Unit = when (value::class) {
        ClassAnnotation::class -> acceptAnnotation(references, env, value as ClassAnnotation)
        AnnotationByte::class -> {}
        AnnotationBoolean::class -> {}
        AnnotationChar::class -> {}
        AnnotationShort::class -> {}
        AnnotationInt::class -> {}
        AnnotationLong::class -> {}
        AnnotationFloat::class -> {}
        AnnotationDouble::class -> {}
        AnnotationString::class -> {}
        AnnotationEnum::class -> acceptEnum(references, value as AnnotationEnum)
        AnnotationClass::class -> acceptClass(references, value as AnnotationClass)
        AnnotationArray::class -> {
            for (classAnnotationValue in (value as AnnotationArray))
                acceptValue(references, env, classAnnotationValue)
        }
        else -> error("logic faiure")
    }

    fun acceptEnum(
        references: MutableCollection<in ClassReference>,
        enum: AnnotationEnum,
    ) {
        references.add(enum.owner)
    }

    fun acceptClass(
        references: MutableCollection<in ClassReference>,
        clazz: AnnotationClass,
    ) {
        newReferenceDesc(clazz.descriptor)?.let(references::add)
    }

    fun acceptAnnotation(
        references: MutableCollection<in ClassReference>,
        env: ComputeReferenceEnvironment,
        annotation: ClassAnnotation,
    ) {
        references.add(annotation.annotationClass)
        for ((_, value) in annotation.values) {
            acceptValue(references, env, value)
        }
    }

    fun acceptAnnotations(
        references: MutableCollection<in ClassReference>,
        env: ComputeReferenceEnvironment,
        annotations: List<ClassAnnotation>,
    ) {
        annotations.forEach { acceptAnnotation(references, env, it) }
    }

    @JvmName("acceptAnnotations1")
    fun acceptAnnotations(
        references: MutableCollection<in ClassReference>,
        env: ComputeReferenceEnvironment,
        annotations: List<ClassTypeAnnotation>,
    ) {
        annotations.forEach { acceptAnnotation(references, env, it.annotation) }
    }

    @JvmName("acceptAnnotations2")
    fun acceptAnnotations(
        references: MutableCollection<in ClassReference>,
        env: ComputeReferenceEnvironment,
        annotations: List<ClassLocalVariableAnnotation>,
    ) {
        annotations.forEach { acceptAnnotation(references, env, it.annotation) }
    }

    fun acceptAnnotations(
        references: MutableCollection<in ClassReference>,
        env: ComputeReferenceEnvironment,
        annotations: Array<List<ClassAnnotation>?>,
    ) {
        for (annotationNodes in annotations) {
            annotationNodes?.forEach { acceptAnnotation(references, env, it) }
        }
    }
}

internal suspend fun ComputeReferenceEnvironment.findClassOrError(name: ClassReference, location: Location): ClassFile? {
    return classpath.findClass(name) ?: kotlin.run {
        addDiagnostic(UNRESOLVABLE_CLASS(name.name, location))
        null
    }
}

internal class ParentClasses(
    val env: ComputeReferenceEnvironment,
    entry: ClassFile,
) {
    val proceed = mutableSetOf(entry)
    val toBeProceed = LinkedList<ClassFile>().apply { add(entry) }
    val location = Location.Class(entry)
    var superNames = sequenceOf<ClassReference?>().iterator()
    var prevReturned: ClassFile? = null

    init {
        updateSuperNames()
    }

    suspend inline fun forEach(block: (ClassFile) -> Boolean) {
        var goDeep = false
        while (true) goDeep = block(next(goDeep) ?: return)
    }

    suspend fun next(goDeep: Boolean): ClassFile? {
        prevReturned?.let {
            if (goDeep) toBeProceed.add(it)
            prevReturned = null
        }
        while (superNames.hasNext()) {
            val superName = superNames.next() ?: continue
            env.findClassOrError(superName, location)?.let { superClass ->
                if (superClass !in proceed) {
                    proceed.add(superClass)
                    prevReturned = superClass
                    return superClass
                }
            }
        }
        if (!updateSuperNames())
            return null
        return next(false)
    }

    private fun updateSuperNames(): Boolean {
        val first = toBeProceed.pollFirst() ?: return false
        superNames = (sequenceOf(first.superName) + first.interfaces).iterator()
        return true
    }
}

private fun newReference(type: Type): ClassReference? {
    return when (type.sort) {
        Type.VOID -> null
        Type.BOOLEAN -> null
        Type.CHAR -> null
        Type.BYTE -> null
        Type.SHORT -> null
        Type.INT -> null
        Type.FLOAT -> null
        Type.LONG -> null
        Type.DOUBLE -> null
        Type.ARRAY -> newReference(type.elementType)
        Type.OBJECT -> ClassReference(type.internalName)
        Type.METHOD -> throw IllegalArgumentException("The type is not type, a METHOD.")
        else -> throw IllegalArgumentException("Unknown sort of type: ${type.sort}")
    }
}

private fun newReference(internalName: String): ClassReference? =
    newReference(Type.getObjectType(internalName))

private fun newReferenceDesc(descriptor: String): ClassReference? =
    newReference(Type.getType(descriptor))
