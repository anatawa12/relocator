package com.anatawa12.relocator.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

internal class ClassFile internal constructor(
    val main: ClassNode,
) {
    val innerClasses = InnerClassContainer(this)
    lateinit var references: Set<Reference>
    val methods: List<ClassMethod>
    val fields: List<ClassField>

    init {
        methods = main.methods.map { ClassMethod(it, innerClasses) }
        fields = main.fields.map { ClassField(it, innerClasses) }
    }

    fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfClass(env, main, innerClasses)
        methods.forEach { it.computeReferences(env) }
        fields.forEach { it.computeReferences(env) }
    }

    fun computeReferencesForLibrary() {
        references = buildSet {
            for (method in methods) {
                method.references = setOf(ClassReference(main.name))
                add(MethodReference(main.name, method.main.name, method.main.desc))
            }
            for (field in fields) {
                field.references = setOf(ClassReference(main.name))
                add(FieldReference(main.name, field.main.name, field.main.desc))
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

internal class ClassMethod internal constructor(val main: MethodNode, val innerClasses: InnerClassContainer) {
    lateinit var references: Set<Reference>

    fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfMethod(env, main, innerClasses)
    }
}

internal class ClassField internal constructor(val main: FieldNode, val innerClasses: InnerClassContainer) {
    lateinit var references: Set<ClassReference>

    fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfField(env, main, innerClasses)
    }
}
