package com.anatawa12.relocator.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

internal class ClassFile internal constructor(
    val main: ClassNode,
) {
    val innerClasses = InnerClassContainer(main.innerClasses)
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val methods: List<ClassMethod>
    val fields: List<ClassField>
    val name: String get() = main.name

    init {
        methods = main.methods.map { ClassMethod(it, this) }
        fields = main.fields.map { ClassField(it, this) }
    }

    fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfClass(env, this)
        methods.forEach { it.computeReferences(env) }
        fields.forEach { it.computeReferences(env) }
    }

    fun computeReferencesForLibrary() {
        references = buildSet {
            for (method in methods) {
                method.references = setOf(ClassReference(name))
                add(MethodReference(name, method.main.name, method.main.desc))
            }
            for (field in fields) {
                field.references = setOf(ClassReference(name))
                add(FieldReference(name, field.main.name, field.main.desc))
            }
        }
    }

    companion object Reader {
        fun read(bytes: ByteArray, noCode: Boolean = false): ClassFile {
            val reader = ClassReader(bytes)
            val node = ClassNode()
            reader.accept(node, if (noCode) ClassReader.SKIP_CODE else 0)
            return ClassFile(node)
        }
    }
}

internal class ClassMethod internal constructor(val main: MethodNode, val owner: ClassFile) {
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()

    fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfMethod(env, main, owner)
    }
}

internal class ClassField internal constructor(val main: FieldNode, val owner: ClassFile) {
    lateinit var references: Set<ClassReference>
    val externalReferences = mutableSetOf<ClassReference>()

    fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfField(env, main, owner)
    }
}
