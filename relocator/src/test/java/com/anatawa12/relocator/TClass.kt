package com.anatawa12.relocator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.*
import sun.reflect.annotation.AnnotationSupport
import sun.reflect.annotation.TypeAnnotation
import java.io.File
import java.lang.reflect.*
import org.objectweb.asm.Type as ASMType

class TClassLoader(private val classLoader: ClassLoader, private val nodePath: List<File>) {
    private val cache = mutableMapOf<String, TClass>()
    fun loadClass(name: String): TClass {
        cache[name]?.let { return it }
        val clazz = classLoader.loadClass(name)
        val path = name.replace('.', '/') + ".class"
        val file = nodePath.asSequence().map { it.resolve(path) }.first { it.isFile }
        val node = ClassNode().apply { ClassReader(file.readBytes()).accept(this, ClassReader.SKIP_CODE) }
        return TClass(this, clazz, node).also { cache[name] = it }
    }

    fun map(clazz: Class<*>): TClass = loadClass(clazz.name).also { check(it.clazz === clazz) }
    fun map(type: ASMType): Class<*> = when (type.sort) {
        ASMType.VOID -> Void::class.java
        ASMType.BOOLEAN -> Boolean::class.java
        ASMType.CHAR -> Char::class.java
        ASMType.BYTE -> Byte::class.java
        ASMType.SHORT -> Short::class.java
        ASMType.INT -> Int::class.java
        ASMType.FLOAT -> Float::class.java
        ASMType.LONG -> Long::class.java
        ASMType.DOUBLE -> Double::class.java
        ASMType.OBJECT -> Class.forName(type.className)
        ASMType.ARRAY -> {
            var t = map(type.elementType)
            repeat(type.dimensions) {
                t = java.lang.reflect.Array.newInstance(t, 0).javaClass
            }
            t
        }
        else -> error("")
    }
    fun paramTypes(desc: String?) = ASMType.getArgumentTypes(desc).map(::map).toTypedArray()
}

class TClass(private val classLoader: TClassLoader, val clazz: Class<*>, private val node: ClassNode) : GenericDeclaration, Type, AnnotatedElement {
    val name: String get() = clazz.name
    val genericSuperclass: Type? get() = clazz.genericSuperclass
    val genericInterfaces: Array<Type> get() = clazz.genericInterfaces
    val modifiers: Int get() = clazz.modifiers
    val enclosingMethod: Method? get() = clazz.enclosingMethod
    val enclosingClass: Class<*>? get() = clazz.enclosingClass
    val simpleName: String? = clazz.simpleName
    val declaredClasses: List<TClass> get() = clazz.declaredClasses.map { classLoader.map(it) }
    val declaredFields = node.fields
        .map { TField(it, clazz.getDeclaredField(it.name), this) }
    val declaredMethods = node.methods.filter { '<' !in it.name }
        .map { TMethod(it, clazz.getDeclaredMethod(it.name, *classLoader.paramTypes(it.desc)), this) }
    val declaredConstructors = node.methods.filter { it.name == "<init>" }
        .map { TConstructor(it, clazz.getDeclaredConstructor(*classLoader.paramTypes(it.desc)), this) }
    override fun getTypeParameters(): Array<TypeVariable<TClass>> = error("not yet implemented")
    override fun getTypeName(): String = clazz.typeName
    @Suppress("UNCHECKED_CAST")
    override fun <A : Annotation?> getAnnotation(annotationClass: Class<A>): A? = annotationClass.cast(declaredAnnotations[annotationClass])
    override fun getAnnotations(): Array<Annotation> = declaredAnnotations.values.toTypedArray()
    override fun getDeclaredAnnotations(): Array<Annotation> = declaredAnnotations.values.toTypedArray()
    val annotatedSuperclass: AnnotatedType get() = AnnotationParser.convertAnnotatedSuperclass(node.typeAnnotations, this)
    val annotatedInterfaces: Array<AnnotatedType> get() = AnnotationParser.convertAnnotatedInterfaces(node.typeAnnotations, this)

    @get:JvmName("declaredAnnotations0")
    private val declaredAnnotations by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AnnotationParser.convertAnnotations(node.annotations, this)
    }

    override fun toString(): String = clazz.toString()
}

class TMethod(node: MethodNode, executable: Method, declaringClass: TClass) : TExecutable(node, executable, declaringClass)

class TConstructor constructor(node: MethodNode, executable: Constructor<*>, declaringClass: TClass) : TExecutable(node, executable, declaringClass)

abstract class TExecutable constructor(
    protected val node: MethodNode,
    private val executable: Executable,
    val declaringClass: TClass,
) : AnnotatedElement, GenericDeclaration {
    private val typeAnnotationBytes0 = node.typeAnnotations

    val name: String get() = executable.name
    val modifiers: Int get() = executable.modifiers
    override fun getTypeParameters(): Array<TypeVariable<*>> = error("not yet implemented")
    val parameterTypes: Array<Class<*>> get() = executable.parameterTypes
    val genericParameterTypes: Array<Type> get() = executable.genericParameterTypes
    val parameters: List<TParameter> = executable.parameters
        .mapIndexed { i, ref -> TParameter(ref, this, i) }
    override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>): T =
        annotationClass.cast(declaredAnnotations[annotationClass])
    override fun <T : Annotation?> getAnnotationsByType(annotationClass: Class<T>): Array<T> =
        AnnotationSupport.getDirectlyAndIndirectlyPresent(declaredAnnotations, annotationClass)
    override fun getAnnotations(): Array<Annotation> = getDeclaredAnnotations()
    override fun getDeclaredAnnotations(): Array<Annotation> = declaredAnnotations.values.toTypedArray()
    val annotatedReturnType: AnnotatedType
        get() = AnnotationParser.convertAnnotatedType(typeAnnotationBytes0, this, declaringClass,
            executable.annotatedReturnType.type, TypeAnnotation.TypeAnnotationTarget.METHOD_RETURN)
    val annotatedParameterTypes: Array<AnnotatedType>
        get() = AnnotationParser.convertAnnotatedTypes(typeAnnotationBytes0, this, declaringClass,
            allGenericParameterTypes, TypeAnnotation.TypeAnnotationTarget.METHOD_FORMAL_PARAMETER)

    override fun toString(): String = executable.toString()

    @get:JvmName("declaredAnnotations0")
    private val declaredAnnotations by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AnnotationParser.convertAnnotations(node.annotations, declaringClass)
    }

    private val allGenericParameterTypes: Array<Type> get() {
        var genericIndex = 0
        return Array(parameterTypes.size) { i ->
            val param = parameters[i]
            if (param.isSynthetic || param.isImplicit) parameterTypes[i]
            else genericParameterTypes[genericIndex++]
        }
    }

    val parameterAnnotations: Array<Array<Annotation>>
        get() = AnnotationParser.convertParameterAnnotations(node.parameterAnnotations, declaringClass)
}

class TField(
    protected val node: FieldNode,
    private val jField: Field,
    val declaringClass: TClass,
) : AnnotatedElement {
    val name: String get() = jField.name
    val modifiers: Int get() = jField.modifiers
    val type: Class<*> get() = jField.type
    val genericType: Type get() = jField.genericType
    override fun toString(): String = jField.toString()
    override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>): T =
        annotationClass.cast(declaredAnnotations[annotationClass])
    override fun getAnnotations(): Array<Annotation> = getDeclaredAnnotations()
    override fun getDeclaredAnnotations(): Array<Annotation> = declaredAnnotations.values.toTypedArray()
    val annotatedType: AnnotatedType
        get() = AnnotationParser.convertAnnotatedType(node.typeAnnotations,
            this,
            declaringClass,
            genericType,
            TypeAnnotation.TypeAnnotationTarget.FIELD)

    @get:JvmName("declaredAnnotations")
    private val declaredAnnotations: Map<Class<out Annotation>, Annotation> by lazy {
        AnnotationParser.convertAnnotations(node.annotations, declaringClass)
    }
}

val ClassNode.annotations get() = visibleAnnotations.orEmpty() + invisibleAnnotations.orEmpty()
val ClassNode.typeAnnotations get() = visibleTypeAnnotations.orEmpty() + invisibleTypeAnnotations.orEmpty()
val FieldNode.annotations get() = visibleAnnotations.orEmpty() + invisibleAnnotations.orEmpty()
val FieldNode.typeAnnotations get() = visibleTypeAnnotations.orEmpty() + invisibleTypeAnnotations.orEmpty()
val MethodNode.annotations get() = visibleAnnotations.orEmpty() + invisibleAnnotations.orEmpty()
val MethodNode.typeAnnotations get() = visibleTypeAnnotations.orEmpty() + invisibleTypeAnnotations.orEmpty()
val MethodNode.parameterAnnotations: List<List<AnnotationNode>> get() {
    val visibleParameterAnnotations = visibleParameterAnnotations?.asList()?.subList(0, visibleAnnotableParameterCount)
    val invisibleParameterAnnotations = invisibleParameterAnnotations?.asList()?.subList(0, invisibleAnnotableParameterCount)
    return if (visibleParameterAnnotations == null) {
        if (invisibleParameterAnnotations == null) {
            List(ASMType.getArgumentTypes(desc).size) { emptyList() }
        } else {
            invisibleParameterAnnotations.map { it.orEmpty() }
        }
    } else {
        if (invisibleParameterAnnotations == null) {
            visibleParameterAnnotations.map { it.orEmpty() }
        } else {
            visibleParameterAnnotations.zip(invisibleParameterAnnotations) { a, b -> a.orEmpty() + b.orEmpty() }
        }
    }
}

class TParameter internal constructor(
    private val parameter: Parameter,
    val declaringExecutable: TExecutable,
    private val index: Int,
) : AnnotatedElement {
    override fun toString(): String = "$parameter of $declaringExecutable"
    val name get() = parameter.name
    val type: Class<*> get() = parameter.type
    val annotatedType: AnnotatedType get() = declaringExecutable.annotatedParameterTypes[index]
    val isImplicit: Boolean get() = parameter.isImplicit
    val isSynthetic: Boolean get() = parameter.isSynthetic
    override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>): T =
        annotationClass.cast(declaredAnnotations[annotationClass])
    override fun getDeclaredAnnotations(): Array<Annotation> = declaringExecutable.parameterAnnotations[index]
    override fun <T : Annotation?> getDeclaredAnnotation(annotationClass: Class<T>): T = getAnnotation(annotationClass)
    override fun <T : Annotation?> getDeclaredAnnotationsByType(annotationClass: Class<T>): Array<T> = getAnnotationsByType(annotationClass)
    override fun getAnnotations(): Array<Annotation> = getDeclaredAnnotations()

    @get:JvmName("declaredAnnotations")
    private val declaredAnnotations: Map<Class<out Annotation>, Annotation> by lazy {
        getDeclaredAnnotations().associateBy { it.annotationClass.java }
    }
}
