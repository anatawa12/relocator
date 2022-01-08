package com.anatawa12.relocator.classes

import com.anatawa12.relocator.builder.BuildBuilder
import com.anatawa12.relocator.builder.StaticBuilderArg
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

// TODO: module support

@BuildBuilder
class ClassFile private constructor(
    @StaticBuilderArg var version: Int,
    @StaticBuilderArg var access: Int,
    @StaticBuilderArg var name: String,
    var signature: ClassSignature?,
    var superName: ClassReference?,
    interfaces: List<ClassReference>,
    var sourceFile: String?,
    var sourceDebug: String?,
    var outerClass: ClassReference?,
    var outerMethod: String?,
    var outerMethodDesc: MethodDescriptor?,
    visibleAnnotations: List<ClassAnnotation>,
    invisibleAnnotations: List<ClassAnnotation>,
    visibleTypeAnnotations: List<ClassTypeAnnotation>,
    invisibleTypeAnnotations: List<ClassTypeAnnotation>,
    innerClasses: List<ClassInnerClass>,
    var nestHostClass: ClassReference?,
    nestMembers: List<ClassReference>,
    permittedSubclasses: List<ClassReference>,
    methods: List<ClassMethod>,
    fields: List<ClassField>,
    recordFields: List<ClassRecordField>,
) {
    val interfaces = interfaces.toMutableList()
    val visibleAnnotations = visibleAnnotations.toMutableList()
    val invisibleAnnotations = invisibleAnnotations.toMutableList()
    val visibleTypeAnnotations = visibleTypeAnnotations.toMutableList()
    val invisibleTypeAnnotations = invisibleTypeAnnotations.toMutableList()
    val innerClasses = innerClasses.toMutableList()
    val nestMembers = nestMembers.toMutableList()
    val permittedSubclasses = permittedSubclasses.toMutableList()

    var included: Boolean = false
    internal val innerClassesContainer by lazy { InnerClassContainer(innerClasses) }
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences

    val methods: MutableList<ClassMethod> = OwnerBasedList(this, ::ownerAccessorClassMethod)
        .apply { addAll(methods) }
    val fields: MutableList<ClassField> = OwnerBasedList(this, ::ownerAccessorClassField)
        .apply { addAll(fields) }
    val recordFields: MutableList<ClassRecordField> = OwnerBasedList(this, ::ownerAccessorClassRecordField)
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

    // TODO: make ClassFileBuilder internal
    class Builder(version: Int, access: Int, name: String) : ClassFileBuilder(version, access, name) {
        override fun buildInternal(
            version: Int,
            access: Int,
            name: String,
            signature: ClassSignature?,
            superName: ClassReference?,
            interfaces: List<ClassReference>,
            sourceFile: String?,
            sourceDebug: String?,
            outerClass: ClassReference?,
            outerMethod: String?,
            outerMethodDesc: MethodDescriptor?,
            visibleAnnotations: List<ClassAnnotation>,
            invisibleAnnotations: List<ClassAnnotation>,
            visibleTypeAnnotations: List<ClassTypeAnnotation>,
            invisibleTypeAnnotations: List<ClassTypeAnnotation>,
            innerClasses: List<ClassInnerClass>,
            nestHostClass: ClassReference?,
            nestMembers: List<ClassReference>,
            permittedSubclasses: List<ClassReference>,
            methods: List<ClassMethod>,
            fields: List<ClassField>,
            recordFields: List<ClassRecordField>
        ): ClassFile = ClassFile(version,
            access,
            name,
            signature,
            superName,
            interfaces,
            sourceFile,
            sourceDebug,
            outerClass,
            outerMethod,
            outerMethodDesc,
            visibleAnnotations,
            invisibleAnnotations,
            visibleTypeAnnotations,
            invisibleTypeAnnotations,
            innerClasses,
            nestHostClass,
            nestMembers,
            permittedSubclasses,
            methods,
            fields,
            recordFields)
    }
}

fun ClassFile.findMethod(ref: MethodReference): ClassMethod? =
    findMethod(ref.name, ref.descriptor)
fun ClassFile.findMethods(ref: PartialMethodReference): List<ClassMethod> =
    this@findMethods.findMethods(ref.name, ref.descriptor)
fun ClassFile.findMethods(ref: TypelessMethodReference): List<ClassMethod> =
    this@findMethods.findMethods(ref.name)
fun ClassFile.findMethod(name: String, desc: MethodDescriptor): ClassMethod? =
    methods.firstOrNull { it.name == name && it.descriptor == desc }
fun ClassFile.findMethods(name: String, desc: PartialMethodDescriptor): List<ClassMethod> =
    methods.filter { it.name == name && it.descriptor.matches(desc) }

fun ClassFile.findMethods(name: String): List<ClassMethod> =
    methods.filter { it.name == name }
fun ClassFile.findField(ref: FieldReference): ClassField? =
    findField(ref.name, ref.descriptor)
fun ClassFile.findField(name: String, desc: TypeDescriptor): ClassField? =
    fields.firstOrNull { it.name == name && it.descriptor == desc }
fun ClassFile.findFields(ref: PartialFieldReference): List<ClassField> =
    fields.filter { it.name == ref.name }
fun ClassFile.findRecordField(name: String, desc: TypeDescriptor): ClassRecordField? =
    recordFields.firstOrNull { it.name == name && it.descriptor == desc }

fun MethodDescriptor.matches(desc: PartialMethodDescriptor): Boolean = descriptor.startsWith(desc.descriptor)

class ClassInnerClass(
    var access: Int,
    var name: ClassReference,
    var outerName: ClassReference?,
    var innerName: String?,
) {
    override fun toString(): String =
        "inner class ${modifiers(access, 0)} $name outer: $outerName inner: $innerName"
}

@BuildBuilder
class ClassMethod private constructor(
    @StaticBuilderArg var access: Int,
    @StaticBuilderArg var name: String,
    @StaticBuilderArg var descriptor: MethodDescriptor,
    var signature: MethodSignature?,
    exceptions: List<ClassReference>,
    parameters: List<ClassParameter>,
    visibleAnnotations: List<ClassAnnotation>,
    invisibleAnnotations: List<ClassAnnotation>,
    visibleTypeAnnotations: List<ClassTypeAnnotation>,
    invisibleTypeAnnotations: List<ClassTypeAnnotation>,
    var annotationDefault: AnnotationValue?,
    visibleParameterAnnotations: Array<List<ClassAnnotation>?>,
    invisibleParameterAnnotations: Array<List<ClassAnnotation>?>,
    classCode: ClassCode?,
) {
    val exceptions = exceptions.toMutableList()
    val parameters = parameters.toMutableList()
    val visibleAnnotations = visibleAnnotations.toMutableList()
    val invisibleAnnotations = invisibleAnnotations.toMutableList()
    val visibleTypeAnnotations = visibleTypeAnnotations.toMutableList()
    val invisibleTypeAnnotations = invisibleTypeAnnotations.toMutableList()
    val visibleParameterAnnotations = visibleParameterAnnotations.mapToArray { it?.toMutableList() }
    val invisibleParameterAnnotations = invisibleParameterAnnotations.mapToArray { it?.toMutableList() }

    var included: Boolean = false
    lateinit var references: Set<Reference>
    val externalReferences = mutableSetOf<Reference>()
    val allReferences get() = references + externalReferences
    private val owner = atomic<ClassFile?>(null)

    var classCode: ClassCode? = classCode?.let { ownerAccessorClassCode.preInit(this, it) }
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

    class Builder(access: Int, name: String, descriptor: MethodDescriptor) : ClassMethodBuilder(access, name, descriptor) {
        override fun buildInternal(
            access: Int,
            name: String,
            descriptor: MethodDescriptor,
            signature: MethodSignature?,
            exceptions: List<ClassReference>,
            parameters: List<ClassParameter>,
            visibleAnnotations: List<ClassAnnotation>,
            invisibleAnnotations: List<ClassAnnotation>,
            visibleTypeAnnotations: List<ClassTypeAnnotation>,
            invisibleTypeAnnotations: List<ClassTypeAnnotation>,
            annotationDefault: AnnotationValue?,
            visibleParameterAnnotations: Array<List<ClassAnnotation>?>,
            invisibleParameterAnnotations: Array<List<ClassAnnotation>?>,
            classCode: ClassCode?,
        ): ClassMethod = ClassMethod(access,
            name,
            descriptor,
            signature,
            exceptions,
            parameters,
            visibleAnnotations,
            invisibleAnnotations,
            visibleTypeAnnotations,
            invisibleTypeAnnotations,
            annotationDefault,
            visibleParameterAnnotations,
            invisibleParameterAnnotations,
            classCode)
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

@BuildBuilder
class ClassField private constructor(
    @StaticBuilderArg var access: Int,
    @StaticBuilderArg var name: String,
    @StaticBuilderArg var descriptor: TypeDescriptor,
    var signature: TypeSignature?,
    var value: Constant?,
    visibleAnnotations: List<ClassAnnotation>,
    invisibleAnnotations: List<ClassAnnotation>,
    visibleTypeAnnotations: List<ClassTypeAnnotation>,
    invisibleTypeAnnotations: List<ClassTypeAnnotation>,
) {
    val visibleAnnotations = visibleAnnotations.toMutableList()
    val invisibleAnnotations = invisibleAnnotations.toMutableList()
    val visibleTypeAnnotations = visibleTypeAnnotations.toMutableList()
    val invisibleTypeAnnotations = invisibleTypeAnnotations.toMutableList()

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

    class Builder(access: Int, name: String, descriptor: TypeDescriptor) : ClassFieldBuilder(access, name, descriptor) {
        // todo: deny non-primitive signature
        override fun buildInternal(
            access: Int,
            name: String,
            descriptor: TypeDescriptor,
            signature: TypeSignature?,
            value: Constant?,
            visibleAnnotations: List<ClassAnnotation>,
            invisibleAnnotations: List<ClassAnnotation>,
            visibleTypeAnnotations: List<ClassTypeAnnotation>,
            invisibleTypeAnnotations: List<ClassTypeAnnotation>,
        ): ClassField = ClassField(access,
            name,
            descriptor,
            signature,
            value,
            visibleAnnotations,
            invisibleAnnotations,
            visibleTypeAnnotations,
            invisibleTypeAnnotations)
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

@BuildBuilder
class ClassRecordField(
    @StaticBuilderArg var name: String,
    @StaticBuilderArg var descriptor: TypeDescriptor,
    var signature: TypeSignature?,
    visibleAnnotations: List<ClassAnnotation>,
    invisibleAnnotations: List<ClassAnnotation>,
    visibleTypeAnnotations: List<ClassTypeAnnotation>,
    invisibleTypeAnnotations: List<ClassTypeAnnotation>,
) {
    val visibleAnnotations = visibleAnnotations.toMutableList()
    val invisibleAnnotations = invisibleAnnotations.toMutableList()
    val visibleTypeAnnotations = visibleTypeAnnotations.toMutableList()
    val invisibleTypeAnnotations = invisibleTypeAnnotations.toMutableList()

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

    class Builder(name: String, descriptor: TypeDescriptor) : ClassRecordFieldBuilder(name, descriptor) {
        // todo: deny non-primitive signature
        override fun buildInternal(
            name: String,
            descriptor: TypeDescriptor,
            signature: TypeSignature?,
            visibleAnnotations: List<ClassAnnotation>,
            invisibleAnnotations: List<ClassAnnotation>,
            visibleTypeAnnotations: List<ClassTypeAnnotation>,
            invisibleTypeAnnotations: List<ClassTypeAnnotation>,
        ): ClassRecordField = ClassRecordField(name,
            descriptor,
            signature,
            visibleAnnotations,
            invisibleAnnotations,
            visibleTypeAnnotations,
            invisibleTypeAnnotations)
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
