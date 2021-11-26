package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.ComputeReferenceEnvironment
import com.anatawa12.relocator.internal.computeReferencesOfClass
import com.anatawa12.relocator.internal.computeReferencesOfField
import com.anatawa12.relocator.internal.computeReferencesOfMethod
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.internal.InnerClassContainer
import com.anatawa12.relocator.reference.MethodReference
import com.anatawa12.relocator.reference.Reference
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class ClassFile internal constructor(
    val main: ClassNode,
    val loader: ClassPath,
) {
    var included: Boolean = false
    internal val innerClasses = InnerClassContainer(main.innerClasses)
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences
    val methods: List<ClassMethod>
    val fields: List<ClassField>
    val name: String get() = main.name

    init {
        methods = main.methods.map { ClassMethod(it, this) }
        fields = main.fields.map { ClassField(it, this) }
    }

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
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
        fun read(bytes: ByteArray, loader: ClassPath, noCode: Boolean = false): ClassFile {
            val reader = ClassReader(bytes)
            val node = ClassNode()
            reader.accept(node, if (noCode) ClassReader.SKIP_CODE else 0)
            return ClassFile(node, loader)
        }
    }
}

fun ClassFile.findMethod(ref: MethodReference): ClassMethod? =
    findMethod(ref.name, ref.descriptor)
fun ClassFile.findMethod(name: String, desc: String): ClassMethod? =
    methods.firstOrNull { it.main.name == name && it.main.desc == desc }
fun ClassFile.findFields(ref: FieldReference): List<ClassField> =
    fields.filter { it.main.name == ref.name && (ref.descriptor == null || it.main.desc == ref.descriptor) }
fun ClassFile.findField(name: String, desc: String): ClassField? =
    fields.firstOrNull { it.main.name == name && it.main.desc == desc }

class ClassMethod internal constructor(val main: MethodNode, val owner: ClassFile) {
    var included: Boolean = false
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfMethod(env, main, owner)
    }
}

class ClassField internal constructor(val main: FieldNode, val owner: ClassFile) {
    var included: Boolean = false
    lateinit var references: Set<ClassReference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfField(env, main, owner)
    }
}
