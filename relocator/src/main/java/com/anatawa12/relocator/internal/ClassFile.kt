package com.anatawa12.relocator.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

internal abstract class ComputeReferenceEnvironment(
    val keepRuntimeInvisibleAnnotation: Boolean,
) {
    abstract fun addDiagnostic(diagnostic: Diagnostic)
}

internal class ClassFile internal constructor(
    val main: ClassNode,
) {
    val innerClasses = InnerClassContainer(main.name, main.innerClasses)
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

    companion object Reader {
        fun read(bytes: ByteArray): ClassFile {
            val reader = ClassReader(bytes)
            val node = ClassNode()
            reader.accept(node, 0)
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
