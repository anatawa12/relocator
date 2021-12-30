package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.Insns
import com.anatawa12.relocator.internal.OwnerBasedSet
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference
import kotlinx.atomicfu.atomic

class ClassCode(
    instructions: InsnList,
    tryCatchBlocks: List<TryCatchBlock>,
    var maxStack: Int,
    var maxLocals: Int,
    localVariables: List<LocalVariable>,
    visibleLocalVariableAnnotations: List<ClassLocalVariableAnnotation>,
    invisibleLocalVariableAnnotations: List<ClassLocalVariableAnnotation>,
) {
    val instructions: InsnList = ownerAccessorInsnList.preInit(this, instructions)

    val tryCatchBlocks = tryCatchBlocks.toMutableList()
    val visibleLocalVariableAnnotations = visibleLocalVariableAnnotations.toMutableList()
    val invisibleLocalVariableAnnotations = invisibleLocalVariableAnnotations.toMutableList()

    private val owner = atomic<ClassMethod?>(null)
    val localVariables: MutableList<LocalVariable> = OwnerBasedList(this, ::ownerAccessorLocalVariable)
        .apply { addAll(localVariables) }

    init {
        ownerAccessorClassCode = Accessor
    }

    private object Accessor : OwnerAccessor<ClassCode, ClassMethod>() {
        override fun trySet(element: ClassCode, target: ClassMethod): Boolean =
            element.owner.compareAndSet(null, target)
        override fun check(element: ClassCode, target: ClassMethod): Boolean =
            element.owner.value === target

        override fun clear(element: ClassCode) {
            element.owner.value = null
        }

        override fun get(element: ClassCode): ClassMethod =
            element.owner.value ?: error("owner of this code not found")
    }
}

class InsnList : MutableList<Insn> by ArrayList() {
    private val owner = atomic<ClassCode?>(null)

    init {
        ownerAccessorInsnList = Accessor
    }

    private object Accessor : OwnerAccessor<InsnList, ClassCode>() {
        override fun trySet(element: InsnList, target: ClassCode): Boolean =
            element.owner.compareAndSet(null, target)
        override fun check(element: InsnList, target: ClassCode): Boolean =
            element.owner.value === target

        override fun clear(element: InsnList) {
            element.owner.value = null
        }

        override fun get(element: InsnList): ClassCode =
            element.owner.value ?: error("owner of this code not found")
    }
}

sealed class Insn {
    init {
        StaticInit.init()
    }

    @Suppress("LeakingThis")
    val labelsToMe: MutableSet<CodeLabel> = OwnerBasedSet(this, ownerAccessorCodeLabel)
    val visibleAnnotations: MutableList<ClassTypeAnnotation> = ArrayList(0)
    val invisibleAnnotations: MutableList<ClassTypeAnnotation> = ArrayList(0)
    var frame: CodeFrame? = null
    var lineNumber: Int = -1
        set(value) {
            require(value == -1 || value in Insns.ushortRange) { "lineNumber out of range" }
            field = value
        }

    private object StaticInit {
        init {
            CodeLabel()
        }
        @JvmStatic
        fun init() {}
    }
}

enum class VMType {
    // basic types
    Int,
    Long,
    Float,
    Double,
    Reference,
    // extra types for arrays and casts
    Byte,
    Char,
    Short,
    // for array creation insn. for others, this will be Boolean
    Boolean,
    ;
}

class SimpleInsn(val insn: SimpleInsnType) : Insn()
enum class SimpleInsnType() {
    NOP, 
    ACONST_NULL, 
    // NO [ILFD]CONST_M?[0-5]: it's const insn
    // NO [ILFDABCS]A(LOAD|STORE): it's TypedInsn
    POP, 
    POP2, 
    DUP, 
    DUP_X1, 
    DUP_X2, 
    DUP2, 
    DUP2_X1, 
    DUP2_X2, 
    SWAP, 
    // NO [ILFD](ADD|SUB|MUL|DIV|REM|NEG|SH[LR]|AND|X?OR): it's TypedInsn
    // NO [ILFD]2[ILFDBCS]: it's CastInsn
    LCMP, 
    FCMPL, 
    FCMPG, 
    DCMPL, 
    DCMPG,
    // NO [ILFDA]RETURN: it's TypedInsn
    RETURN, 
    ARRAYLENGTH, 
    ATHROW, 
    MONITORENTER, 
    MONITOREXIT,
}

class TypedInsn(val insn: TypedInsnType, val type: VMType) : Insn() {
    init {
        require(type in Insns.typeMatches[insn]!!) { "$type is invalid for $insn" }
    }
}

enum class TypedInsnType {
    ALOAD,
    ASTORE,
    ADD,
    SUB,
    MUL,
    DIV,
    REM,
    NEG,
    SHL,
    SHR,
    USHR,
    AND,
    OR,
    XOR,
    RETURN,
    NEWARRAY,
}

class CastInsn(val from: VMType, val to: VMType): Insn() {
    init {
        require(from to to in Insns.casts) { "can't cast from $from to $to" }
    }
}

class VarInsn(val insn: VarInsnType, val type: VMType, var variable: Int) : Insn() {
    init {
        require(type in Insns.vmBasicTypes) { "$type is invalid for variable" }
        require(variable in Insns.ushortRange) { "variable id out of range: $variable" }
    }
}

enum class VarInsnType {
    LOAD,
    STORE,
}

class RetInsn(var variable: Int) : Insn() {
    init {
        require(variable in Insns.ushortRange) { "variable id out of range: $variable" }
    }
}

class TypeInsn(val insn: TypeInsnType, var type: ClassReference) : Insn() {
    init {
        if (insn == TypeInsnType.NEW)
            require(!type.isArray()) { "type for NEW insn must not a array." }
    }
}

enum class TypeInsnType {
    NEW,
    ANEWARRAY,
    CHECKCAST,
    INSTANCEOF,
}

class FieldInsn(val insn: FieldInsnType, var field: FieldReference) : Insn()

enum class FieldInsnType {
    GETSTATIC,
    PUTSTATIC,
    GETFIELD,
    PUTFIELD,
}

class MethodInsn(val insn: MethodInsnType, var method: MethodReference, var isInterface: Boolean) : Insn() {
    init {
        require(method.name != "<clinit>") { "we can't call <clinit>" }
        if (method.name == "<init>") {
            require(insn == MethodInsnType.INVOKESPECIAL) { "we can't call <init> via $insn" }
            require(!method.owner.isArray()) { "we can't call <init> of array" }
            require(!isInterface) { "we can't call <init> of interface" }
        }
        if (insn == MethodInsnType.INVOKEINTERFACE) {
            require(isInterface) { "we can't call non-interface method via $insn" }
        }
    }
}

enum class MethodInsnType {
    INVOKEVIRTUAL,
    INVOKESPECIAL,
    INVOKESTATIC,
    INVOKEINTERFACE,
}

class InvokeDynamicInsn(var target: ConstantDynamic) : Insn()

class JumpInsn(
    val insn: JumpInsnType,
    var target: CodeLabel,
) : Insn()

enum class JumpInsnType {
    IFEQ,
    IFNE,
    IFLT,
    IFGE,
    IFGT,
    IFLE,
    IF_ICMPEQ,
    IF_ICMPNE,
    IF_ICMPLT,
    IF_ICMPGE,
    IF_ICMPGT,
    IF_ICMPLE,
    IF_ACMPEQ,
    IF_ACMPNE,
    GOTO,
    JSR,
    IFNULL,
    IFNONNULL,
}

class LdcInsn(var value: Constant) : Insn()

class IIncInsn(var variable: Int, var value: Int) : Insn() {
    init {
        require(variable in Insns.ushortRange) { "variable id out of range: $variable" }
        require(variable in Insns.shortRange) { "iinc avlue out of range: $value" }
    }
}

class TableSwitchInsn(
    var min: Int,
    var default: CodeLabel,
    labels: List<CodeLabel>
) : Insn() {
    val labels = labels.toMutableList()
}

class LookupSwitchInsn(
    var default: CodeLabel,
    labels: Map<Int, CodeLabel>
) : Insn() {
    val labels = labels.toMutableMap()
}

class MultiANewArrayInsn(var type: ClassReference, var dimensions: Int) : Insn() {
    init {
        require(dimensions != 0 && dimensions in Insns.ubyteRange) { "dimensions out of range: $dimensions" }
        require(type.isArray()) { "type for MULTIANEWARRAY must be a array" }
        require(type.arrayDimensions >= dimensions) { "type for MULTIANEWARRAY is not deep enough" }
    }
}

class CodeLabel() {
    private val target = atomic<Insn?>(null)

    init {
        ownerAccessorCodeLabel = Accessor
    }

    override fun toString(): String =
        "CodeLabel@" + System.identityHashCode(this).toString(16).padStart(8, '0')

    private object Accessor : OwnerAccessor<CodeLabel, Insn>() {
        override fun trySet(element: CodeLabel, target: Insn): Boolean =
            element.target.compareAndSet(null, target)

        override fun check(element: CodeLabel, target: Insn): Boolean =
            element.target.value === target

        override fun clear(element: CodeLabel) {
            element.target.value = null
        }

        override fun get(element: CodeLabel): Insn = element.target.value ?: error("target of this label not found")
    }
}

sealed class CodeFrame

class FullFrame(val locals: List<FrameElement>, val stacks: List<FrameElement>) : CodeFrame()
object SameFrame : CodeFrame()
class Same1Frame(val stack: FrameElement) : CodeFrame()

class AppendFrame(val locals: List<FrameElement>) : CodeFrame() {
    init {
        require(locals.size in 1..3) { "size of locals out of range" }
    }
}

class ChopFrame(val locals: Int) : CodeFrame() {
    init {
        require(locals in 1..3) { "locals out of range" }
    }
}

sealed class FrameElement {
    object Top: FrameElement()
    object Integer: FrameElement()
    object Float: FrameElement()
    object Long: FrameElement()
    object Double: FrameElement()
    object Null: FrameElement()
    object UninitializedThis: FrameElement()
    class Reference(val type: ClassReference): FrameElement()
    class Uninitialized(val at: CodeLabel): FrameElement()
}
