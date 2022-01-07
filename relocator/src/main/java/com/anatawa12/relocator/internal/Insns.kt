package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.classes.ConstantDynamic
import com.anatawa12.relocator.classes.TypeReference
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference
import com.anatawa12.relocator.reference.withLocation
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import java.util.*
import org.objectweb.asm.TypePath as ASMTypePath

internal object Insns {
    val shortRange = Short.MIN_VALUE..Short.MAX_VALUE
    val ubyteRange = 0..UByte.MAX_VALUE.toInt()
    val ushortRange = 0 .. UShort.MAX_VALUE.toInt()

    // SimpleInsn
    val simpleInsns = biMapOf(
        SimpleInsnType.NOP to NOP,
        SimpleInsnType.ACONST_NULL to ACONST_NULL,
        SimpleInsnType.POP to POP,
        SimpleInsnType.POP2 to POP2,
        SimpleInsnType.DUP to DUP,
        SimpleInsnType.DUP_X1 to DUP_X1,
        SimpleInsnType.DUP_X2 to DUP_X2,
        SimpleInsnType.DUP2 to DUP2,
        SimpleInsnType.DUP2_X1 to DUP2_X1,
        SimpleInsnType.DUP2_X2 to DUP2_X2,
        SimpleInsnType.SWAP to SWAP,
        SimpleInsnType.LCMP to LCMP,
        SimpleInsnType.FCMPL to FCMPL,
        SimpleInsnType.FCMPG to FCMPG,
        SimpleInsnType.DCMPL to DCMPL,
        SimpleInsnType.DCMPG to DCMPG,
        SimpleInsnType.RETURN to RETURN,
        SimpleInsnType.ARRAYLENGTH to ARRAYLENGTH,
        SimpleInsnType.ATHROW to ATHROW,
        SimpleInsnType.MONITORENTER to MONITORENTER,
        SimpleInsnType.MONITOREXIT to MONITOREXIT,
    )

    // TypedInsn

    val vmAllTypes = setOf(*VMType.values())
    val vmBasicTypes = setOf(VMType.Int, VMType.Long, VMType.Float, VMType.Double, VMType.Reference)
    val vmBasicNumberTypes = setOf(VMType.Int, VMType.Long, VMType.Float, VMType.Double)
    val vmBasicIntegerTypes = setOf(VMType.Int, VMType.Long)
    val vmExtraNumberTypes = setOf(VMType.Byte, VMType.Char, VMType.Short)
    val vmNumberTypes = setOf(
        VMType.Int,
        VMType.Long,
        VMType.Float,
        VMType.Double,
        VMType.Byte,
        VMType.Char,
        VMType.Short,
    )
    val vmPrimitiveTypes = setOf(
        VMType.Int,
        VMType.Long,
        VMType.Float,
        VMType.Double,
        VMType.Byte,
        VMType.Char,
        VMType.Short,
        VMType.Boolean,
    )

    val typeMatches = enumMapOf(
        TypedInsnType.ALOAD to vmAllTypes,
        TypedInsnType.ASTORE to vmAllTypes,
        TypedInsnType.ADD to vmBasicNumberTypes,
        TypedInsnType.SUB to vmBasicNumberTypes,
        TypedInsnType.MUL to vmBasicNumberTypes,
        TypedInsnType.DIV to vmBasicNumberTypes,
        TypedInsnType.REM to vmBasicNumberTypes,
        TypedInsnType.NEG to vmBasicNumberTypes,

        TypedInsnType.SHL to vmBasicIntegerTypes,
        TypedInsnType.SHR to vmBasicIntegerTypes,
        TypedInsnType.USHR to vmBasicIntegerTypes,
        TypedInsnType.AND to vmBasicIntegerTypes,
        TypedInsnType.OR to vmBasicIntegerTypes,
        TypedInsnType.XOR to vmBasicIntegerTypes,

        TypedInsnType.RETURN to vmBasicTypes,
        TypedInsnType.NEWARRAY to vmPrimitiveTypes,
    )

    val typedInsnDiffs = enumMapOf(
        VMType.Int to 0,
        VMType.Long to (LALOAD - IALOAD),
        VMType.Float to (FALOAD - IALOAD),
        VMType.Double to (DALOAD - IALOAD),
        VMType.Reference to (AALOAD - IALOAD),
        VMType.Byte to (BALOAD - IALOAD),
        VMType.Char to (CALOAD - IALOAD),
        VMType.Short to (SALOAD - IALOAD),
    )

    val typedOperatorInsnMapping = mapOf(
        Pair(TypedInsnType.ADD, VMType.Int) to IADD,
        Pair(TypedInsnType.ADD, VMType.Long) to LADD,
        Pair(TypedInsnType.ADD, VMType.Float) to FADD,
        Pair(TypedInsnType.ADD, VMType.Double) to DADD,
        Pair(TypedInsnType.SUB, VMType.Int) to ISUB,
        Pair(TypedInsnType.SUB, VMType.Long) to LSUB,
        Pair(TypedInsnType.SUB, VMType.Float) to FSUB,
        Pair(TypedInsnType.SUB, VMType.Double) to DSUB,
        Pair(TypedInsnType.MUL, VMType.Int) to IMUL,
        Pair(TypedInsnType.MUL, VMType.Long) to LMUL,
        Pair(TypedInsnType.MUL, VMType.Float) to FMUL,
        Pair(TypedInsnType.MUL, VMType.Double) to DMUL,
        Pair(TypedInsnType.DIV, VMType.Int) to IDIV,
        Pair(TypedInsnType.DIV, VMType.Long) to LDIV,
        Pair(TypedInsnType.DIV, VMType.Float) to FDIV,
        Pair(TypedInsnType.DIV, VMType.Double) to DDIV,
        Pair(TypedInsnType.REM, VMType.Int) to IREM,
        Pair(TypedInsnType.REM, VMType.Long) to LREM,
        Pair(TypedInsnType.REM, VMType.Float) to FREM,
        Pair(TypedInsnType.REM, VMType.Double) to DREM,
        Pair(TypedInsnType.NEG, VMType.Int) to INEG,
        Pair(TypedInsnType.NEG, VMType.Long) to LNEG,
        Pair(TypedInsnType.NEG, VMType.Float) to FNEG,
        Pair(TypedInsnType.NEG, VMType.Double) to DNEG,
        Pair(TypedInsnType.SHL, VMType.Int) to ISHL,
        Pair(TypedInsnType.SHL, VMType.Long) to LSHL,
        Pair(TypedInsnType.SHR, VMType.Int) to ISHR,
        Pair(TypedInsnType.SHR, VMType.Long) to LSHR,
        Pair(TypedInsnType.USHR, VMType.Int) to IUSHR,
        Pair(TypedInsnType.USHR, VMType.Long) to LUSHR,
        Pair(TypedInsnType.AND, VMType.Int) to IAND,
        Pair(TypedInsnType.AND, VMType.Long) to LAND,
        Pair(TypedInsnType.OR, VMType.Int) to IOR,
        Pair(TypedInsnType.OR, VMType.Long) to LOR,
        Pair(TypedInsnType.XOR, VMType.Int) to IXOR,
        Pair(TypedInsnType.XOR, VMType.Long) to LXOR,
    )

    val typedVarInsnDiffs = arrayOf(VMType.Int, VMType.Long, VMType.Float, VMType.Double, VMType.Reference)

    val typedInsnMapping = mapOf(
        IALOAD to Pair(TypedInsnType.ALOAD, VMType.Int),
        LALOAD to Pair(TypedInsnType.ALOAD, VMType.Long),
        FALOAD to Pair(TypedInsnType.ALOAD, VMType.Float),
        DALOAD to Pair(TypedInsnType.ALOAD, VMType.Double),
        AALOAD to Pair(TypedInsnType.ALOAD, VMType.Reference),
        BALOAD to Pair(TypedInsnType.ALOAD, VMType.Byte),
        CALOAD to Pair(TypedInsnType.ALOAD, VMType.Char),
        SALOAD to Pair(TypedInsnType.ALOAD, VMType.Short),
        IASTORE to Pair(TypedInsnType.ASTORE, VMType.Int),
        LASTORE to Pair(TypedInsnType.ASTORE, VMType.Long),
        FASTORE to Pair(TypedInsnType.ASTORE, VMType.Float),
        DASTORE to Pair(TypedInsnType.ASTORE, VMType.Double),
        AASTORE to Pair(TypedInsnType.ASTORE, VMType.Reference),
        BASTORE to Pair(TypedInsnType.ASTORE, VMType.Byte),
        CASTORE to Pair(TypedInsnType.ASTORE, VMType.Char),
        SASTORE to Pair(TypedInsnType.ASTORE, VMType.Short),
        IADD to Pair(TypedInsnType.ADD, VMType.Int),
        LADD to Pair(TypedInsnType.ADD, VMType.Long),
        FADD to Pair(TypedInsnType.ADD, VMType.Float),
        DADD to Pair(TypedInsnType.ADD, VMType.Double),
        ISUB to Pair(TypedInsnType.SUB, VMType.Int),
        LSUB to Pair(TypedInsnType.SUB, VMType.Long),
        FSUB to Pair(TypedInsnType.SUB, VMType.Float),
        DSUB to Pair(TypedInsnType.SUB, VMType.Double),
        IMUL to Pair(TypedInsnType.MUL, VMType.Int),
        LMUL to Pair(TypedInsnType.MUL, VMType.Long),
        FMUL to Pair(TypedInsnType.MUL, VMType.Float),
        DMUL to Pair(TypedInsnType.MUL, VMType.Double),
        IDIV to Pair(TypedInsnType.DIV, VMType.Int),
        LDIV to Pair(TypedInsnType.DIV, VMType.Long),
        FDIV to Pair(TypedInsnType.DIV, VMType.Float),
        DDIV to Pair(TypedInsnType.DIV, VMType.Double),
        IREM to Pair(TypedInsnType.REM, VMType.Int),
        LREM to Pair(TypedInsnType.REM, VMType.Long),
        FREM to Pair(TypedInsnType.REM, VMType.Float),
        DREM to Pair(TypedInsnType.REM, VMType.Double),
        INEG to Pair(TypedInsnType.NEG, VMType.Int),
        LNEG to Pair(TypedInsnType.NEG, VMType.Long),
        FNEG to Pair(TypedInsnType.NEG, VMType.Float),
        DNEG to Pair(TypedInsnType.NEG, VMType.Double),
        ISHL to Pair(TypedInsnType.SHL, VMType.Int),
        LSHL to Pair(TypedInsnType.SHL, VMType.Long),
        ISHR to Pair(TypedInsnType.SHR, VMType.Int),
        LSHR to Pair(TypedInsnType.SHR, VMType.Long),
        IUSHR to Pair(TypedInsnType.USHR, VMType.Int),
        LUSHR to Pair(TypedInsnType.USHR, VMType.Long),
        IAND to Pair(TypedInsnType.AND, VMType.Int),
        LAND to Pair(TypedInsnType.AND, VMType.Long),
        IOR to Pair(TypedInsnType.OR, VMType.Int),
        LOR to Pair(TypedInsnType.OR, VMType.Long),
        IXOR to Pair(TypedInsnType.XOR, VMType.Int),
        LXOR to Pair(TypedInsnType.XOR, VMType.Long),
        IRETURN to Pair(TypedInsnType.RETURN, VMType.Int),
        LRETURN to Pair(TypedInsnType.RETURN, VMType.Long),
        FRETURN to Pair(TypedInsnType.RETURN, VMType.Float),
        DRETURN to Pair(TypedInsnType.RETURN, VMType.Double),
        ARETURN to Pair(TypedInsnType.RETURN, VMType.Reference),
    )

    val newArrayTypeMapping = biMapOf(
        T_BOOLEAN to VMType.Boolean, 
        T_CHAR to VMType.Char, 
        T_FLOAT to VMType.Float, 
        T_DOUBLE to VMType.Double, 
        T_BYTE to VMType.Byte, 
        T_SHORT to VMType.Short, 
        T_INT to VMType.Int, 
        T_LONG to VMType.Long,
    )

    // CastInsn

    @Suppress("LocalVariableName")
    val casts = kotlin.run {
        val I = VMType.Int
        val L = VMType.Long
        val F = VMType.Float
        val D = VMType.Double
        val B = VMType.Byte
        val C = VMType.Char
        val S = VMType.Short

        println(booleanArrayOf(false)[0])

        biMapOf(
            Pair(I, L) to I2L,
            Pair(I, F) to I2F,
            Pair(I, D) to I2D,
            Pair(L, I) to L2I,
            Pair(L, F) to L2F,
            Pair(L, D) to L2D,
            Pair(F, I) to F2I,
            Pair(F, L) to F2L,
            Pair(F, D) to F2D,
            Pair(D, I) to D2I,
            Pair(D, L) to D2L,
            Pair(D, F) to D2F,
            Pair(I, B) to I2B,
            Pair(I, C) to I2C,
            Pair(I, S) to I2S,
        )
    }

    // IntInsn
    // VarInsn
    // RetInsn
    // TypeInsn
    // FieldInsn

    val fieldInsns = arrayOf(
        FieldInsnType.GETSTATIC,
        FieldInsnType.PUTSTATIC,
        FieldInsnType.GETFIELD,
        FieldInsnType.PUTFIELD,
    )

    // MethodInsn

    val methodInsns = arrayOf(
        MethodInsnType.INVOKEVIRTUAL, 
        MethodInsnType.INVOKESPECIAL, 
        MethodInsnType.INVOKESTATIC, 
        MethodInsnType.INVOKEINTERFACE,
    )

    // InvokeDynamicInsn
    // JumpInsn

    val jumpInsns = arrayOf(
        JumpInsnType.IFEQ,
        JumpInsnType.IFNE,
        JumpInsnType.IFLT,
        JumpInsnType.IFGE,
        JumpInsnType.IFGT,
        JumpInsnType.IFLE,
        JumpInsnType.IF_ICMPEQ,
        JumpInsnType.IF_ICMPNE,
        JumpInsnType.IF_ICMPLT,
        JumpInsnType.IF_ICMPGE,
        JumpInsnType.IF_ICMPGT,
        JumpInsnType.IF_ICMPLE,
        JumpInsnType.IF_ACMPEQ,
        JumpInsnType.IF_ACMPNE,
        JumpInsnType.GOTO,
        JumpInsnType.JSR,
    )

    // LdcInsn

    val constants = biMapOf(
        ConstantInt(-1) to ICONST_M1,
        ConstantInt(0) to ICONST_0,
        ConstantInt(1) to ICONST_1,
        ConstantInt(2) to ICONST_2,
        ConstantInt(3) to ICONST_3,
        ConstantInt(4) to ICONST_4,
        ConstantInt(5) to ICONST_5,
        ConstantLong(0) to LCONST_0,
        ConstantLong(1) to LCONST_1,
        ConstantFloat(0f) to FCONST_0,
        ConstantFloat(1f) to FCONST_1,
        ConstantFloat(2f) to FCONST_2,
        ConstantDouble(0.0) to DCONST_0,
        ConstantDouble(1.0) to DCONST_1,
    )

    // IIncInsn
    // TableSwitchInsn
    // LookupSwitchInsn

    @Suppress("DEPRECATION")
    class InsnBuilder(
        private val location: Location?,
    ) : MethodVisitor(ASM9) {
        var classCode: ClassCode? = null

        private val insnList = InsnList()
        private val tryCatches = mutableListOf<TryCatchBlock>()
        private val localVars = mutableListOf<LocalVariable>()
        private val localVarVisibleAnnotations: MutableList<ClassLocalVariableAnnotation> = ArrayList(0)
        private val localVarInvisibleAnnotations: MutableList<ClassLocalVariableAnnotation> = ArrayList(0)

        private val labelMapping = HashMap<Label, CodeLabel>()
        private fun mapLabel(label: Label): CodeLabel = labelMapping.getOrPut(label) { CodeLabel() }
        private val lineNumberTable = LinkedList<Pair<CodeLabel, Int>>()

        private val prevLabels = HashSet<CodeLabel>()
        private var prevFrame: CodeFrame? = null

        private fun addInsn(insn: Insn) {
            for (prevLabel in prevLabels)
                assert(insn.labelsToMe.add(prevLabel)) { "adding label to insn failed" }
            insn.frame = prevFrame
            prevLabels.clear()
            prevFrame = null
            insnList.add(insn)
        }

        override fun visitFrame(
            type: Int,
            numLocal: Int,
            local: Array<out Any>?,
            numStack: Int,
            stack: Array<out Any>?
        ) {
            prevFrame = when (type) {
                F_SAME -> SameFrame
                F_SAME1 -> Same1Frame(newFrameElement(stack!![0]))
                F_APPEND -> AppendFrame(local!!.take(numLocal).map(::newFrameElement))
                F_CHOP -> ChopFrame(numLocal)
                F_FULL -> FullFrame(
                    local!!.take(numLocal).map(::newFrameElement),
                    stack!!.take(numStack).map(::newFrameElement),
                )
                else -> error("unknown frame type: $type")
            }
        }

        private fun newFrameElement(any: Any): FrameElement = when (any) {
            TOP -> FrameElement.Top
            INTEGER -> FrameElement.Integer
            FLOAT -> FrameElement.Float
            LONG -> FrameElement.Long
            DOUBLE -> FrameElement.Double
            NULL -> FrameElement.Null
            UNINITIALIZED_THIS -> FrameElement.UninitializedThis
            else -> when(any) {
                is String -> FrameElement.Reference(ClassReference(any).withLocation(location))
                is Label -> FrameElement.Uninitialized(mapLabel(any))
                else -> error("unknown value: $any (typed ${any.javaClass})")
            }
        }

        override fun visitInsn(opcode: Int) {
            simpleInsns.inverse()[opcode]
                ?.let { return addInsn(SimpleInsn(it)) }
            constants.inverse()[opcode]
                ?.let { return addInsn(LdcInsn(it)) }
            typedInsnMapping[opcode]
                ?.let { return addInsn(TypedInsn(it.first, it.second)) }
            casts.inverse()[opcode]
                ?.let { (from, to) -> return addInsn(CastInsn(from, to)) }
            assertError("invalid insn: $opcode")
        }

        override fun visitIntInsn(opcode: Int, operand: Int) {
            when (opcode) {
                BIPUSH -> addInsn(LdcInsn(ConstantInt(operand.toByte().toInt())))
                SIPUSH -> addInsn(LdcInsn(ConstantInt(operand.toShort().toInt())))
                NEWARRAY -> addInsn(TypedInsn(TypedInsnType.NEWARRAY,
                    newArrayTypeMapping[operand] ?: assertError("invalid TAG: NEWARRAY $operand")))
                else -> assertError("invalid insn: $opcode")
            }
        }

        override fun visitVarInsn(opcode: Int, variable: Int) {
            if (opcode in ILOAD .. ALOAD)
                return addInsn(VarInsn(VarInsnType.LOAD, typedVarInsnDiffs[opcode - ILOAD], variable))
            if (opcode in ISTORE .. ASTORE)
                return addInsn(VarInsn(VarInsnType.STORE, typedVarInsnDiffs[opcode - ISTORE], variable))
            if (opcode == RET)
                return addInsn(RetInsn(variable))
            assertError("invalid insn: $opcode")
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            when (opcode) {
                NEW -> addInsn(TypeInsn(TypeInsnType.NEW, ClassReference(type).withLocation(location)))
                ANEWARRAY -> addInsn(TypeInsn(TypeInsnType.ANEWARRAY, ClassReference(type).withLocation(location)))
                CHECKCAST -> addInsn(TypeInsn(TypeInsnType.CHECKCAST, ClassReference(type).withLocation(location)))
                INSTANCEOF -> addInsn(TypeInsn(TypeInsnType.INSTANCEOF, ClassReference(type).withLocation(location)))
                else -> assertError("invalid insn: $opcode")
            }
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            val insn = fieldInsns.getOrNull(opcode - GETSTATIC) ?: assertError("invalid insn: $opcode")
            addInsn(FieldInsn(insn, FieldReference(owner, name, descriptor).withLocation(location)))
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        )  {
            val insn = methodInsns.getOrNull(opcode - INVOKEVIRTUAL) ?: assertError("invalid insn: $opcode")
            addInsn(MethodInsn(insn, MethodReference(owner, name, descriptor).withLocation(location), isInterface))
        }

        override fun visitInvokeDynamicInsn(
            name: String,
            descriptor: String,
            bootstrapMethodHandle: Handle,
            vararg bootstrapMethodArguments: Any,
        ) {
            addInsn(InvokeDynamicInsn(
                newConstantDynamic(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments, location)
            ))
        }

        override fun visitJumpInsn(opcode: Int, label: Label) {
            if (opcode == IFNULL)
                addInsn(JumpInsn(JumpInsnType.IFNULL, mapLabel(label)))
            else if (opcode == IFNONNULL)
                addInsn(JumpInsn(JumpInsnType.IFNONNULL, mapLabel(label)))
            else
                addInsn(JumpInsn(jumpInsns[opcode - IFEQ], mapLabel(label)))
        }

        override fun visitLabel(label: Label) {
            prevLabels += mapLabel(label)
        }

        override fun visitLdcInsn(value: Any) {
            addInsn(LdcInsn(newConstant(value, location)))
        }

        override fun visitIincInsn(variable: Int, increment: Int) {
            addInsn(IIncInsn(variable, increment))
        }

        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
            require (max - min + 1 == labels.size) { "invalid TABLESWITCH insn: table size and min-max mismatch" }
            addInsn(TableSwitchInsn(min, mapLabel(dflt), labels.map(::mapLabel)))
        }

        override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
            require (keys.size == labels.size) { "invalid LOOKUPSWITCH insn: key and label table size mismatch" }
            addInsn(LookupSwitchInsn(
                mapLabel(dflt),
                keys.zip(labels).associateTo(HashMap(keys.size)) { (k, v) -> k to mapLabel(v) }
            ))
        }

        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
            addInsn(MultiANewArrayInsn(ClassReference(descriptor).withLocation(location), numDimensions))
        }

        override fun visitInsnAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor {
            val insn = insnList.last()
            val list = if (visible) insn.visibleAnnotations else insn.invisibleAnnotations
            return Builders.AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location, list)
        }

        override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
            tryCatches += TryCatchBlock(
                mapLabel(start),
                mapLabel(start),
                mapLabel(start),
                type?.let(::ClassReference)?.withLocation(location),
            )
        }

        override fun visitTryCatchAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor {
            val insn = tryCatches.last()
            val list = if (visible) insn.visibleAnnotations else insn.invisibleAnnotations
            return Builders.AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location, list)
        }

        override fun visitLocalVariable(
            name: String,
            descriptor: String,
            signature: String?,
            start: Label,
            end: Label,
            index: Int
        ) {
            localVars += LocalVariable(name,
                TypeDescriptor(descriptor),
                signature?.let(TypeSignature::parse),
                mapLabel(start),
                mapLabel(end),
                index)
        }

        override fun visitLocalVariableAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            start: Array<out Label>,
            end: Array<out Label>,
            index: IntArray,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor {
            val list = if (visible) localVarVisibleAnnotations else localVarInvisibleAnnotations
            return Builders.AnnotationBuilder.ofAnnotation(descriptor, location) { annotation ->
                list += ClassLocalVariableAnnotation(
                    TypeReference(typeRef),
                    typePath?.let(::newTypePath),
                    List(start.size) {
                        AnnotationLocalVariable(mapLabel(start[it]), mapLabel(end[it]), index[it])
                    },
                    annotation,
                )
            }
        }

        override fun visitLineNumber(line: Int, start: Label) {
            lineNumberTable += mapLabel(start) to line.toUShort().toInt()
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            beforeEnd()

            classCode = ClassCode(
                insnList,
                tryCatches,
                maxStack,
                maxLocals,
                localVars,
                localVarVisibleAnnotations,
                localVarInvisibleAnnotations,
            )
        }

        private fun beforeEnd() {
            for ((label, line) in lineNumberTable)
                label.target.lineNumber = line
        }
    }

    fun newConstant(main: Any, location: Location?): Constant = when (main::class) {
        Int::class -> ConstantInt(main as Int)
        Long::class -> ConstantLong(main as Long)
        Float::class -> ConstantFloat(main as Float)
        Double::class -> ConstantDouble(main as Double)
        String::class -> ConstantString(main as String)
        Type::class -> {
            val type = (main as Type)
            if (type.sort == Type.METHOD)
                ConstantMethodType(MethodDescriptor(type.descriptor))
            else
                ConstantClass(TypeDescriptor(type.descriptor))
        }
        Handle::class -> newConstantHandle(main as Handle, location)
        org.objectweb.asm.ConstantDynamic::class -> newConstantDynamic(main as org.objectweb.asm.ConstantDynamic, location)
        else -> throw IllegalArgumentException("unsupported constant value: ${main.javaClass}")
    }

    private fun newConstantHandle(handle: Handle, location: Location?): ConstantHandle = when (handle.tag) {
        H_GETFIELD -> ConstantFieldHandle(FieldHandleType.GETFIELD,
            FieldReference(handle.owner, handle.name, handle.desc).withLocation(location))
        H_GETSTATIC -> ConstantFieldHandle(FieldHandleType.GETSTATIC,
            FieldReference(handle.owner, handle.name, handle.desc).withLocation(location))
        H_PUTFIELD -> ConstantFieldHandle(FieldHandleType.PUTFIELD,
            FieldReference(handle.owner, handle.name, handle.desc).withLocation(location))
        H_PUTSTATIC -> ConstantFieldHandle(FieldHandleType.PUTSTATIC,
            FieldReference(handle.owner, handle.name, handle.desc).withLocation(location))

        H_INVOKEVIRTUAL -> ConstantMethodHandle(MethodHandleType.INVOKEVIRTUAL,
            MethodReference(handle.owner, handle.name, handle.desc).withLocation(location),
            handle.isInterface)
        H_INVOKESTATIC -> ConstantMethodHandle(MethodHandleType.INVOKESTATIC,
            MethodReference(handle.owner, handle.name, handle.desc).withLocation(location),
            handle.isInterface)
        H_INVOKESPECIAL -> ConstantMethodHandle(MethodHandleType.INVOKESPECIAL,
            MethodReference(handle.owner, handle.name, handle.desc).withLocation(location),
            handle.isInterface)
        H_NEWINVOKESPECIAL -> ConstantMethodHandle(MethodHandleType.NEWINVOKESPECIAL,
            MethodReference(handle.owner, handle.name, handle.desc).withLocation(location),
            handle.isInterface)
        H_INVOKEINTERFACE -> ConstantMethodHandle(MethodHandleType.INVOKEINTERFACE,
            MethodReference(handle.owner, handle.name, handle.desc).withLocation(location),
            handle.isInterface)
        else -> throw IllegalArgumentException("unsupported handle type: ${handle.tag}")
    }

    private fun newConstantDynamic(constant: org.objectweb.asm.ConstantDynamic, location: Location?): ConstantDynamic = ConstantDynamic(
        constant.name,
        MethodDescriptor(constant.descriptor),
        newConstantHandle(constant.bootstrapMethod, location),
        List(constant.bootstrapMethodArgumentCount) {
            newConstant(constant.getBootstrapMethodArgument(it), location)
        },
    )

    private fun newConstantDynamic(
        name: String,
        descriptor: String,
        bootstrapMethod: Handle,
        args: Array<out Any>,
        location: Location?,
    ): ConstantDynamic = ConstantDynamic(
        name,
        MethodDescriptor(descriptor),
        newConstantHandle(bootstrapMethod, location),
        args.map { newConstant(it, location) },
    )

    fun newTypePath(path: ASMTypePath): TypePath = TypePath.Builder(path.length).apply {
        for (it in 0 until path.length) {
            when (path.getStep(it)) {
                ASMTypePath.ARRAY_ELEMENT -> array()
                ASMTypePath.INNER_TYPE -> inner()
                ASMTypePath.WILDCARD_BOUND -> wildcard()
                ASMTypePath.TYPE_ARGUMENT -> argument(path.getStepArgument(it))
            }
        }
    }.build()
}
