
package com.anatawa12.relocator

import com.anatawa12.relocator.PublicABITest.Location.*
import com.anatawa12.relocator.internal.JavaClassMarker
import com.anatawa12.relocator.internal.hasFlag
import io.kotest.core.spec.style.DescribeSpec
import org.objectweb.asm.Opcodes.*
import java.io.File
import java.lang.reflect.*

/**
 * The test to check all API
 */
@Suppress("LeakingThis")
abstract class PublicABITest(
    val checkInternalMarker: Boolean,
) : DescribeSpec() {
    init {
        val rootDirs = listOf(
            File(Relocator::class.java.protectionDomain.codeSource.location.toURI()),
            File(JavaClassMarker::class.java.protectionDomain.codeSource.location.toURI()),
        )
        val loader = TClassLoader(PublicABITest::class.java.classLoader, rootDirs)
        if (!rootDirs.any { it.isDirectory })
            throw Exception("Relocator is in non-directory classpath")
        val classFiles = rootDirs.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "class" }.map { dir to it }
        }
        for ((rootDir, file) in classFiles) {
            val className = file.toRelativeString(rootDir).removeSuffix(".class").replace('/', '.')
            if (className.startsWith("com.anatawa12.relocator.internal.")) continue
            val classFile = loader.loadClass(className)
            if (classFile.enclosingMethod != null) continue
            if (classFile.enclosingClass != null) continue
            if (!shouldCheckClass(classFile)) continue
            it(className) {
                checkClassFile(classFile)
            }
        }
    }

    private val anonymousClassPattern = """\$[0-9]""".toRegex()

    private fun checkClassFile(classFile: TClass) {
        if (!isApi(classFile.modifiers)) return
        if (classFile.simpleName.isNullOrEmpty() || classFile.simpleName.contains(anonymousClassPattern)) return
        checkAnnotatedType(classFile.annotatedSuperclass, SuperClassOf(classFile))
        for (clazz in classFile.annotatedInterfaces)
            checkAnnotatedType(clazz, SuperInterfaceOf(classFile))
        checkAnnotations(classFile)

        for (method in classFile.declaredMethods) {
            if (!isApi(method.modifiers)) continue
            if (checkInternalMarker && method.name.endsWith("\$main")) continue
            if (checkInternalMarker && method.name in javaKeywords) continue

            checkMethod(method)
        }

        for (constructor in classFile.declaredConstructors) {
            if (!isApi(constructor.modifiers)) continue

            checkConstructor(constructor)
        }

        for (field in classFile.declaredFields) {
            if (!isApi(field.modifiers)) continue

            checkField(field)
        }

        for (declaredClass in classFile.declaredClasses) {
            checkClassFile(declaredClass)
        }
    }

    protected open fun shouldCheckClass(clazz: TClass): Boolean = true

    protected open fun checkAnnotations(element: AnnotatedElement) {
        val location = AnnotationTypeOf(element)
        for (declaredAnnotation in element.declaredAnnotations) {
            // ignore kotlin.Metadata
            checkClass(declaredAnnotation.annotationClass.java, location)
        }
    }

    protected open fun checkMethod(method: TMethod) {
        checkExecutable(method)
        checkAnnotatedType(method.annotatedReturnType, ReturnTypeOf(method))
    }

    protected open fun checkConstructor(constructor: TConstructor) {
        checkExecutable(constructor)
    }

    protected open fun checkExecutable(executable: TExecutable) {
        checkAnnotations(executable)
        for (parameter in executable.parameters)
            checkAnnotatedType(parameter.annotatedType, ParameterTypeOf(parameter))
    }

    protected open fun checkField(field: TField) {
        checkAnnotations(field)
        checkAnnotatedType(field.annotatedType, TypeOf(field))
    }

    protected open fun checkAnnotatedType(firstType: AnnotatedType, location: Location) {
        val types = ArrayDeque<AnnotatedType>()
        types.add(firstType)
        while (types.isNotEmpty()) {
            val type = types.removeFirst()
            checkAnnotatedTypeSimple(type, location)
            checkAnnotations(type)
            when (val genericType = type.type) {
                is Class<*> -> {
                    checkClass(genericType, location)
                    continue
                }
                null -> continue
            }
            when (type) {
                is AnnotatedArrayType -> types.add(type.annotatedGenericComponentType)
                is AnnotatedParameterizedType -> {
                    val owner = (type.type as ParameterizedType).ownerType
                    if (owner != null) types.add(toAnnotated(owner))
                    types.addAll(type.annotatedActualTypeArguments)
                }
                is AnnotatedWildcardType -> {
                    types.addAll(type.annotatedLowerBounds)
                    types.addAll(type.annotatedUpperBounds)
                }
                is AnnotatedTypeVariable -> {}
                else -> error("unknown type variable type: $type(${type.javaClass}) at $location")
            }
        }
    }

    protected open fun checkAnnotatedTypeSimple(annotatedType: AnnotatedType, location: Location) {
    }

    protected open fun checkClass(clazz: Class<*>, location: Location) {
    }

    // utilities
    protected fun toAnnotated(type: Type): AnnotatedType {
        abstract class NoAnnotationElement() : AnnotatedElement {
            override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>): Nothing? = null
            override fun getAnnotations(): Array<Annotation> = emptyArray()
            override fun getDeclaredAnnotations(): Array<Annotation> = emptyArray()
        }
        return when (type) {
            is Class<*> -> object : NoAnnotationElement(), AnnotatedType {
                override fun getType(): Type = type
            }
            is GenericArrayType -> object : NoAnnotationElement(), AnnotatedArrayType {
                override fun getType(): Type = type
                override fun getAnnotatedGenericComponentType(): AnnotatedType = toAnnotated(type.genericComponentType)
            }
            is ParameterizedType -> object : NoAnnotationElement(), AnnotatedParameterizedType {
                override fun getType(): Type = type
                override fun getAnnotatedActualTypeArguments(): Array<AnnotatedType> =
                    type.actualTypeArguments.map(::toAnnotated).toTypedArray()
            }
            is WildcardType -> object : NoAnnotationElement(), AnnotatedWildcardType {
                override fun getType(): Type = type
                override fun getAnnotatedLowerBounds(): Array<AnnotatedType> =
                    type.lowerBounds.map(::toAnnotated).toTypedArray()

                override fun getAnnotatedUpperBounds(): Array<AnnotatedType> =
                    type.upperBounds.map(::toAnnotated).toTypedArray()
            }
            is TypeVariable<*> -> object : NoAnnotationElement(), AnnotatedTypeVariable {
                override fun getType(): Type = type
                override fun getAnnotatedBounds(): Array<AnnotatedType> =
                    type.annotatedBounds
            }
            else -> error("unknown type variable type: $type(${type.javaClass})")
        }
    }

    protected fun toRawType(type: Type): Class<*>? {
        return when (type) {
            is Class<*> -> type
            is GenericArrayType -> java.lang.reflect.Array.newInstance(toRawType(type.genericComponentType), 0).javaClass
            is ParameterizedType -> toRawType(type.rawType)
            is WildcardType -> null
            is TypeVariable<*> -> null
            else -> error("unknown type variable type: $type(${type.javaClass})")
        }
    }

    private fun isApi(modifiers: Int) = !modifiers.hasFlag(ACC_PRIVATE) && !modifiers.hasFlag(ACC_SYNTHETIC)

    private val javaKeywords = setOf(
        "abstract", "continue", "for", "new", "switch",
        "assert", "default", "goto", "package", "synchronized",
        "boolean", "do", "if", "private", "this",
        "break", "double", "implements", "protected", "throw",
        "byte", "else", "import", "public", "throws",
        "case", "enum", "instanceof", "return", "transient",
        "catch", "extends", "int", "short", "try",
        "char", "final", "interface", "static", "void",
        "class", "finally", "long", "strictfp", "volatile",
        "const", "float", "native", "super", "while",
    )

    sealed class Location {
        class SuperClassOf(val clazz: TClass): Location() {
            override fun toString(): String = "super class of $clazz"
        }

        class SuperInterfaceOf(val clazz: TClass): Location() {
            override fun toString(): String = "super interface of $clazz"
        }

        class AnnotationTypeOf(val element: AnnotatedElement): Location() {
            override fun toString(): String = "annotation type of $element"
        }

        class TypeOf(val field: TField): Location() {
            override fun toString(): String = "type of $field"
        }

        class ReturnTypeOf(val method: TExecutable): Location() {
            override fun toString(): String = "return type of $method"
        }

        class ParameterTypeOf(val parameter: TParameter): Location() {
            override fun toString(): String = "type of $parameter"
        }

        abstract override fun toString(): String
    }
}
