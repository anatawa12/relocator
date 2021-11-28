package com.anatawa12.relocator

import com.anatawa12.relocator.internal.hasFlag
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.*
import java.io.File
import java.lang.reflect.*
import kotlin.collections.ArrayDeque
import kotlin.reflect.KVisibility
import kotlin.reflect.jvm.kotlinFunction

/**
 * The test to check all ABI doesn't use kotlin classes
 */
class AllAPIDoesNotUseKotlin {
    val bads = mutableListOf<String>()

    @Test
    fun check() {
        val rootDir = File(Relocator::class.java.protectionDomain.codeSource.location.toURI())
        if (!rootDir.isDirectory)
            throw Exception("Relocator is in non-directory classpath")
        for (file in rootDir.walkTopDown().filter { it.isFile && it.extension == "class" }) {
            val className = file.toRelativeString(rootDir).removeSuffix(".class").replace('/', '.')
            if (className.startsWith("com.anatawa12.relocator.internal.")) continue
            val classFile = Class.forName(className)
            checkClassFile(classFile)
        }
        if (bads.isNotEmpty())
            throw Exception("some class have kotlin ABI: \n" + bads.joinToString("\n"))
    }
    
    val anonymousClassPattern = """\$[0-9]""".toRegex()

    private fun checkClassFile(classFile: Class<*>) {
        if (!isApi(classFile.modifiers)) return
        if (classFile.modifiers.hasFlag(ACC_SYNTHETIC)) return
        if (classFile.enclosingMethod != null) return
        if (classFile.enclosingClass != null) return
        if (classFile.simpleName.isNullOrEmpty() || classFile.simpleName.contains(anonymousClassPattern)) return

        if (classFile.annotatedSuperclass != null)
            checkAnnotatedType(classFile.annotatedSuperclass, "superclass of ${classFile.name}")
        for (clazz in classFile.annotatedInterfaces)
            checkAnnotatedType(clazz, "a interfaces of ${classFile.name}")
        checkAnnotations(classFile, classFile.name)
        for (method in classFile.methods) {
            if (!isApi(method.modifiers)) continue
            if (method.modifiers.hasFlag(ACC_SYNTHETIC)) continue

            if (method.kotlinFunction?.visibility == KVisibility.INTERNAL) continue
            checkAnnotations(method, "method ${classFile.name}:${method.name}")
            for (parameter in method.parameters)
                checkAnnotatedType(parameter.annotatedType, "parameter type of method ${classFile.name}:${method.name}")
            checkAnnotatedType(method.annotatedReturnType, "return type of method ${classFile.name}:${method.name}")
        }

        for (constructor in classFile.constructors) {
            if (!isApi(constructor.modifiers)) continue
            if (constructor.modifiers.hasFlag(ACC_SYNTHETIC)) continue

            checkAnnotations(constructor, "constructor of ${classFile.name}")
            for (parameter in constructor.parameters)
                checkAnnotatedType(parameter.annotatedType, "parameter type of constructor of ${classFile.name}")
            checkAnnotatedType(constructor.annotatedReturnType, "return type of constructor of ${classFile.name}")
        }

        for (field in classFile.fields) {
            if (!isApi(field.modifiers)) continue
            if (field.modifiers.hasFlag(ACC_SYNTHETIC)) continue

            checkAnnotations(field, "field ${classFile.name}:${field.name}")
            checkAnnotatedType(field.annotatedType, "type of field ${classFile.name}:${field.name}")
        }
    }

    private fun isApi(modifiers: Int) = !modifiers.hasFlag(ACC_PRIVATE)

    private fun checkAnnotations(classFile: AnnotatedElement, of: String) {
        val location = "annotation type of $of"
        for (declaredAnnotation in classFile.declaredAnnotations) {
            // ignore kotlin.Metadata
            if (declaredAnnotation.annotationClass == Metadata::class) continue
            checkClass(declaredAnnotation.annotationClass.java, location)
        }
    }

    private fun checkAnnotatedType(firstType: AnnotatedType, location: String) {
        val types = ArrayDeque<AnnotatedType>()
        types.add(firstType)
        while (types.isNotEmpty()) {
            val type = types.removeFirst()
            checkAnnotations(type, "annotation type of $location")
            when (val genericType = type.type) {
                is Class<*> -> {
                    checkClass(genericType, location)
                    continue
                }
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
                else -> error("unknown type variable type: $type(${type.javaClass})")
            }
        }
    }

    private fun toAnnotated(type: Type): AnnotatedType {
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

    private fun checkClass(clazz: Class<*>, location: String) {
        @Suppress("NAME_SHADOWING") var clazz = clazz
        while (clazz.isArray) clazz = clazz.componentType
        if (clazz.name.startsWith("kotlin"))
            bads.add(location)
    }
}
