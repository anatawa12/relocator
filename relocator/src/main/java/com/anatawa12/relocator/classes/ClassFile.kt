package com.anatawa12.relocator.classes

import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.BasicDiagnostics.UNSUPPORTED_ATTRIBUTE
import com.anatawa12.relocator.internal.ComputeReferenceEnvironment
import com.anatawa12.relocator.internal.InnerClassContainer
import com.anatawa12.relocator.internal.computeReferencesOfClass
import com.anatawa12.relocator.internal.computeReferencesOfField
import com.anatawa12.relocator.internal.computeReferencesOfRecordField
import com.anatawa12.relocator.internal.computeReferencesOfMethod
import com.anatawa12.relocator.reference.*
import kotlinx.atomicfu.atomic
import org.objectweb.asm.ClassReader
import java.lang.Exception

// TODO: add builder and make constructor parameter builder
// TODO: module support

class ClassFile constructor(
    val version: Int,
    val access: Int,
    val name: String,
    val signature: String?,
    val superName: ClassReference?,
    val interfaces: List<ClassReference>,
    val sourceFile: String?,
    val sourceDebug: String?,
    val outerClass: ClassReference?,
    val outerMethod: String?,
    val outerMethodDesc: String?,
    val visibleAnnotations: List<ClassAnnotation>,
    val invisibleAnnotations: List<ClassAnnotation>,
    val visibleTypeAnnotations: List<ClassTypeAnnotation>,
    val invisibleTypeAnnotations: List<ClassTypeAnnotation>,
    val innerClasses: List<ClassInnerClass>,
    val nestHostClass: ClassReference?,
    val nestMembers: List<ClassReference>,
    val permittedSubclasses: List<ClassReference>,
    methods: List<ClassMethod>,
    fields: List<ClassField>,
    recordFields: List<ClassRecordField>,
) {
    var included: Boolean = false
    internal val innerClassesContainer by lazy { InnerClassContainer(innerClasses) }
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences

    val methods: MutableList<ClassMethod> = OwnerBasedList(this, ownerAccessorClassMethod)
        .apply { addAll(methods) }
    val fields: MutableList<ClassField> = OwnerBasedList(this, ownerAccessorClassField)
        .apply { addAll(fields) }
    val recordFields: MutableList<ClassRecordField> = OwnerBasedList(this, ownerAccessorClassRecordField)
        .apply { addAll(recordFields) }

    private var attrNames = emptyList<String>()

    init {
        unknownAttrsSetterClassFile = { attrNames = it }
    }

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfClass(env, this)
        methods.forEach { it.computeReferences(env) }
        fields.forEach { it.computeReferences(env) }
        recordFields.forEach { it.computeReferences(env) }
        for (attrName in attrNames)
            env.addDiagnostic(UNSUPPORTED_ATTRIBUTE(attrName, Location.Class(this)))
    }

    fun computeReferencesForLibrary() {
        references = buildSet {
            for (method in methods) {
                method.references = setOf(ClassReference(name))
                add(MethodReference(name, method.name, method.descriptor))
            }
            for (field in fields) {
                field.references = setOf(ClassReference(name))
                add(FieldReference(name, field.name, field.descriptor))
            }
        }
    }

    companion object Reader {
        fun read(bytes: ByteArray, @Suppress("UNUSED_PARAMETER") loader: ClassPath, noCode: Boolean = false): ClassFile {
            val reader = ClassReader(bytes)
            val builder = Builders.ClassBuilder()
            try {
                reader.accept(builder, if (noCode) ClassReader.SKIP_CODE else 0)
            } catch (e: Exception) {
                throw IllegalArgumentException("reading ${reader.className}", e);
            }
            return builder.classFile!!
        }
    }
}

fun ClassFile.findMethod(ref: MethodReference): ClassMethod? =
    findMethod(ref.name, ref.descriptor)
fun ClassFile.findMethods(ref: PartialMethodReference): List<ClassMethod> =
    this@findMethods.findMethods(ref.name, ref.descriptor)
fun ClassFile.findMethods(ref: TypelessMethodReference): List<ClassMethod> =
    this@findMethods.findMethods(ref.name)
fun ClassFile.findMethod(name: String, desc: String): ClassMethod? =
    methods.firstOrNull { it.name == name && it.descriptor == desc }
fun ClassFile.findMethods(name: String, desc: String): List<ClassMethod> =
    methods.filter { it.name == name && it.descriptor.startsWith(desc) }
fun ClassFile.findMethods(name: String): List<ClassMethod> =
    methods.filter { it.name == name }
fun ClassFile.findField(ref: FieldReference): ClassField? =
    findField(ref.name, ref.descriptor)
fun ClassFile.findField(name: String, desc: String): ClassField? =
    fields.firstOrNull { it.name == name && it.descriptor == desc }
fun ClassFile.findFields(ref: PartialFieldReference): List<ClassField> =
    fields.filter { it.name == ref.name }
fun ClassFile.findRecordField(name: String, desc: String): ClassRecordField? =
    recordFields.firstOrNull { it.name == name && it.descriptor == desc }

class ClassInnerClass(
    var access: Int,
    var name: ClassReference,
    var outerName: ClassReference?,
    var innerName: String?,
)

class ClassMethod constructor(
    val access: Int,
    val name: String,
    val descriptor: String,
    val signature: String?,
    val exceptions: List<ClassReference>,
    val parameters: List<ClassParameter>,
    val visibleAnnotations: List<ClassAnnotation>,
    val invisibleAnnotations: List<ClassAnnotation>,
    val visibleTypeAnnotations: List<ClassTypeAnnotation>,
    val invisibleTypeAnnotations: List<ClassTypeAnnotation>,
    val annotationDefault: AnnotationValue?,
    val visibleParameterAnnotations: Array<List<ClassAnnotation>?>,
    val invisibleParameterAnnotations: Array<List<ClassAnnotation>?>,
    classCode: ClassCode?,
) {
    var included: Boolean = false
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences
    private val owner = atomic<ClassFile?>(null)

    var classCode: ClassCode? = ownerAccessorClassCode.preInit(this, classCode)
       set(value) = ownerAccessorClassCode.doSet(this, field, value) { field = it }
    private var attrNames = emptyList<String>()
    
    init {
        ownerAccessorClassMethod = Accessor
        unknownAttrsSetterClassMethod = { attrNames = it }
    }

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfMethod(env, this)
        for (attrName in attrNames)
            env.addDiagnostic(UNSUPPORTED_ATTRIBUTE(attrName, Location.Method(this)))
    }

    private object Accessor : OwnerAccessor<ClassMethod, ClassFile>() {
        override fun trySet(element: ClassMethod, target: ClassFile): Boolean =
            element.owner.compareAndSet(null, target)

        override fun check(element: ClassMethod, target: ClassFile): Boolean =
            element.owner.value === target

        override fun clear(element: ClassMethod) {
            element.owner.value = null
        }

        override fun get(element: ClassMethod): ClassFile = element.owner.value ?: error("owner of this method not found")
    }
}

class ClassField constructor(
    val access: Int,
    val name: String,
    val descriptor: String,
    val signature: String?,
    val value: Constant?,
    val visibleAnnotations: List<ClassAnnotation>,
    val invisibleAnnotations: List<ClassAnnotation>,
    val visibleTypeAnnotations: List<ClassTypeAnnotation>,
    val invisibleTypeAnnotations: List<ClassTypeAnnotation>,
) {
    var included: Boolean = false
    lateinit var references: Set<ClassReference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences
    private val owner = atomic<ClassFile?>(null)
    private var attrNames = emptyList<String>()

    init {
        ownerAccessorClassField = Accessor
        unknownAttrsSetterClassField = { attrNames = it }
    }

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfField(env, this)
        for (attrName in attrNames)
            env.addDiagnostic(UNSUPPORTED_ATTRIBUTE(attrName, Location.Field(this)))
    }

    private object Accessor : OwnerAccessor<ClassField, ClassFile>() {
        override fun trySet(element: ClassField, target: ClassFile): Boolean =
            element.owner.compareAndSet(null, target)

        override fun check(element: ClassField, target: ClassFile): Boolean =
            element.owner.value === target

        override fun clear(element: ClassField) {
            element.owner.value = null
        }

        override fun get(element: ClassField): ClassFile = element.owner.value ?: error("owner of this field not found")
    }
}

class ClassRecordField(
    val name: String,
    val descriptor: String,
    val signature: String?,
    val visibleAnnotations: List<ClassAnnotation>,
    val invisibleAnnotations: List<ClassAnnotation>,
    val visibleTypeAnnotations: List<ClassTypeAnnotation>,
    val invisibleTypeAnnotations: List<ClassTypeAnnotation>,
) {
    var included: Boolean = false
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences
    private var attrNames = emptyList<String>()
    private val owner = atomic<ClassFile?>(null)

    init {
        ownerAccessorClassRecordField = Accessor
        unknownAttrsSetterClassRecordField = { attrNames = it }
    }

    internal suspend fun computeReferences(env: ComputeReferenceEnvironment) {
        references = computeReferencesOfRecordField(env, this)
        for (attrName in attrNames)
            env.addDiagnostic(UNSUPPORTED_ATTRIBUTE(attrName, Location.RecordField(this)))
    }

    private object Accessor : OwnerAccessor<ClassRecordField, ClassFile>() {
        override fun trySet(element: ClassRecordField, target: ClassFile): Boolean =
            element.owner.compareAndSet(null, target)

        override fun check(element: ClassRecordField, target: ClassFile): Boolean =
            element.owner.value === target

        override fun clear(element: ClassRecordField) {
            element.owner.value = null
        }

        override fun get(element: ClassRecordField): ClassFile = element.owner.value ?: error("owner of this field not found")
    }
}
