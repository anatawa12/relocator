package com.anatawa12.relocator

//*
import com.anatawa12.relocator.PublicABITest.Location.ReturnTypeOf
import com.anatawa12.relocator.internal.hasFlag
import io.kotest.assertions.fail
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.objectweb.asm.Opcodes
import java.lang.reflect.*
import kotlin.annotations.jvm.Mutable
import kotlin.annotations.jvm.ReadOnly

/**
 * The test to check all ABI doesn't use external classes
 */
class AllJavaHaveAnnotations : PublicABITest(false) {
    override fun shouldCheckClass(clazz: TClass): Boolean = !clazz.isAnnotationPresent(Metadata::class.java)

    override fun checkAnnotatedType(firstType: AnnotatedType, location: Location) {
        if (location is Location.SuperClassOf) return
        if (location is Location.SuperInterfaceOf) return
        super.checkAnnotatedType(firstType, location)
    }

    override fun checkMethod(method: TMethod) {
        if (kotlin.runCatching { Any::class.java.getMethod(method.name, *method.parameterTypes) }.getOrNull() != null)
            return
        super.checkMethod(method)
    }

    override fun checkAnnotatedTypeSimple(annotatedType: AnnotatedType, location: Location) {
        val ownerElement = when (location) {
            is Location.ParameterTypeOf -> location.parameter
            is ReturnTypeOf -> location.method
            is Location.TypeOf -> location.field
            else -> null
        }

        // skip return type of Enum.values() and Enum.valueOf
        if (ownerElement is TMethod && isEnumMethod(ownerElement))
            return
        if (ownerElement is TParameter && ownerElement.declaringExecutable is TMethod
            && isEnumMethod(ownerElement.declaringExecutable))
            return
        // skip type of enum constants
        if (ownerElement is TField && ownerElement.modifiers.hasFlag(Opcodes.ACC_ENUM))
            return

        val type = annotatedType.type
        // first check: no typed error
        if (type is Class<*> && type.typeParameters.isNotEmpty())
            fail("using raw type $type at $location")
        val rawType = toRawType(annotatedType.type) ?: return
        if (!rawType.isPrimitive) {
            if (!annotatedType.hasNullability() && ownerElement?.hasNullability() != true)
                fail("no nullability annotation for $type at $location")
        }
        if (rawType in collectionTypes) {
            if (!annotatedType.hasMutability() && ownerElement?.hasMutability() != true)
                fail("no mutability annotation for $type at $location")
        }
    }

    private fun isEnumMethod(ownerElement: TMethod): Boolean {
        if (!ownerElement.declaringClass.modifiers.hasFlag(Opcodes.ACC_ENUM)) return false
        if (ownerElement.name == "values" && ownerElement.parameterTypes.isEmpty()) return true
        if (ownerElement.name == "valueOf" && ownerElement.parameterTypes.contentEquals(arrayOf(String::class.java)))
            return true
        return false
    }

    private fun AnnotatedElement.hasNullability() =
        getAnnotation(NotNull::class.java) != null || getAnnotation(Nullable::class.java) != null
    private fun AnnotatedElement.hasMutability() =
        getAnnotation(Mutable::class.java) != null || getAnnotation(ReadOnly::class.java) != null

    private val collectionTypes = arrayOf(
        Iterator::class.java,
        Iterable::class.java,
        Collection::class.java,
        List::class.java,
        Set::class.java,
        Map::class.java,
        Map.Entry::class.java,
    )
}
