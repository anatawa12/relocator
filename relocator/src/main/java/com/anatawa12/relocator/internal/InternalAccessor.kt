package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*

internal lateinit var ownerAccessorCodeLabel: OwnerAccessor<CodeLabel, Insn>
internal val CodeLabel.target get() = ownerAccessorCodeLabel.get(this)

internal lateinit var ownerAccessorLocalVariable: OwnerAccessor<LocalVariable, ClassCode>
internal val LocalVariable.owner get() = ownerAccessorLocalVariable.get(this)

internal lateinit var ownerAccessorClassCode: OwnerAccessor<ClassCode, ClassMethod>
internal val ClassCode.owner get() = ownerAccessorClassCode.get(this)

internal lateinit var ownerAccessorInsnList: OwnerAccessor<InsnList, ClassCode>
internal val InsnList.owner get() = ownerAccessorInsnList.get(this)

internal lateinit var ownerAccessorClassMethod: OwnerAccessor<ClassMethod, ClassFile>
internal val ClassMethod.owner get() = ownerAccessorClassMethod.get(this)

internal lateinit var ownerAccessorClassField: OwnerAccessor<ClassField, ClassFile>
internal val ClassField.owner get() = ownerAccessorClassField.get(this)

internal lateinit var ownerAccessorClassRecordField: OwnerAccessor<ClassRecordField, ClassFile>
internal val ClassRecordField.owner get() = ownerAccessorClassRecordField.get(this)

internal lateinit var unknownAttrsSetterClassFile: ClassFile.(List<String>) -> Unit
internal fun ClassFile.withUnknownAttrs(attrs: List<String>) = apply { unknownAttrsSetterClassFile(attrs) }

internal lateinit var unknownAttrsSetterClassMethod: ClassMethod.(List<String>) -> Unit
internal fun ClassMethod.withUnknownAttrs(attrs: List<String>) = apply { unknownAttrsSetterClassMethod(attrs) }

internal lateinit var unknownAttrsSetterClassField: ClassField.(List<String>) -> Unit
internal fun ClassField.withUnknownAttrs(attrs: List<String>) = apply { unknownAttrsSetterClassField(attrs) }

internal lateinit var unknownAttrsSetterClassRecordField: ClassRecordField.(List<String>) -> Unit
internal fun ClassRecordField.withUnknownAttrs(attrs: List<String>) = apply { unknownAttrsSetterClassRecordField(attrs) }

@Suppress("ObjectPropertyName", "unused")
private val _init = run<Unit> {
    ClassFile(
        0,
        0,
        "",
        null,
        null,
        emptyList(),
        null,
        null,
        null,
        null,
        null,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        null,
        emptyList(),
        emptyList(),
        listOf(
            ClassMethod(
                0,
                "",
                "",
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                null,
                emptyArray(),
                emptyArray(),
                ClassCode(
                    InsnList(),
                    emptyList(),
                    0,
                    0,
                    listOf(LocalVariable("", "", "", CodeLabel(), CodeLabel(), 0)),
                    emptyList(),
                    emptyList(),
                ),
            )
        ),
        listOf(
            ClassField(
                0,
                "",
                "",
                null,
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        ),
        listOf(
            ClassRecordField(
                "",
                "",
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        ),
    )

    ownerAccessorCodeLabel
    ownerAccessorLocalVariable
    ownerAccessorClassCode
    ownerAccessorClassMethod
    ownerAccessorClassField
    ownerAccessorClassRecordField
    unknownAttrsSetterClassFile
    unknownAttrsSetterClassMethod
    unknownAttrsSetterClassField
    unknownAttrsSetterClassRecordField
}
