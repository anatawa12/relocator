package com.anatawa12.relocator

import org.objectweb.asm.TypePath
import org.objectweb.asm.TypeReference
import org.objectweb.asm.TypeReference.*
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.TypeAnnotationNode
import sun.reflect.annotation.AnnotatedTypeFactory
import sun.reflect.annotation.AnnotationType
import sun.reflect.annotation.TypeAnnotation
import sun.reflect.annotation.TypeAnnotation.TypeAnnotationTarget
import sun.reflect.annotation.TypeAnnotation.TypeAnnotationTargetInfo
import sun.reflect.generics.factory.CoreReflectionFactory
import sun.reflect.generics.parser.SignatureParser
import sun.reflect.generics.scope.ClassScope
import sun.reflect.generics.visitor.Reifier
import java.lang.annotation.AnnotationFormatError
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.AnnotatedType
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.nio.ByteBuffer
import sun.reflect.annotation.AnnotationParser as SunAnnotationParser

object AnnotationParser {
    private val EMPTY_ANNOTATED_TYPE: AnnotatedType = AnnotatedTypeFactory.buildAnnotatedType(null, null, null, null, null)
    private val EMPTY_TYPE_ANNOTATION_ARRAY = emptyArray<TypeAnnotation>()
    
    private fun convertTypeAnnotationNode(node: TypeAnnotationNode, baseDecl: AnnotatedElement, container: TClass): TypeAnnotation {
        val targetInfo = convertTypeReference(node.typeRef)
        val loc = node.typePath?.let(::convertTypePath) ?: TypeAnnotation.LocationInfo.BASE_LOCATION
        val annotation = convertAnnotation(node, container, false)
        return TypeAnnotation(targetInfo, loc, annotation, baseDecl)
    }

    private fun convertTypeReference(typeRef: Int): TypeAnnotationTargetInfo {
        @Suppress("NAME_SHADOWING")
        val typeRef = TypeReference(typeRef)
        return when (val posCode = typeRef.sort) {
            CLASS_TYPE_PARAMETER, METHOD_TYPE_PARAMETER -> when (posCode) {
                CLASS_TYPE_PARAMETER -> TypeAnnotationTargetInfo(TypeAnnotationTarget.CLASS_TYPE_PARAMETER, typeRef.typeParameterIndex)
                else -> TypeAnnotationTargetInfo(TypeAnnotationTarget.METHOD_TYPE_PARAMETER, typeRef.typeParameterIndex)
            }
            CLASS_EXTENDS -> {
                val index = typeRef.superTypeIndex //needs to be signed
                if (index == -1) {
                    TypeAnnotationTargetInfo(TypeAnnotationTarget.CLASS_EXTENDS)
                } else if (index >= 0) {
                    TypeAnnotationTargetInfo(TypeAnnotationTarget.CLASS_IMPLEMENTS, index)
                } else {
                    throw AnnotationFormatError("Could not convert bytes for type annotations")
                }
            }
            CLASS_TYPE_PARAMETER_BOUND -> TypeAnnotationTargetInfo(TypeAnnotationTarget.CLASS_TYPE_PARAMETER_BOUND, typeRef.typeParameterIndex, typeRef.typeParameterBoundIndex)
            METHOD_TYPE_PARAMETER_BOUND -> TypeAnnotationTargetInfo(TypeAnnotationTarget.METHOD_TYPE_PARAMETER_BOUND, typeRef.typeParameterIndex, typeRef.typeParameterBoundIndex)
            FIELD -> TypeAnnotationTargetInfo(TypeAnnotationTarget.FIELD)
            METHOD_RETURN -> TypeAnnotationTargetInfo(TypeAnnotationTarget.METHOD_RETURN)
            METHOD_RECEIVER -> TypeAnnotationTargetInfo(TypeAnnotationTarget.METHOD_RECEIVER)
            METHOD_FORMAL_PARAMETER -> TypeAnnotationTargetInfo(TypeAnnotationTarget.METHOD_FORMAL_PARAMETER, typeRef.formalParameterIndex)
            THROWS -> TypeAnnotationTargetInfo(TypeAnnotationTarget.METHOD_FORMAL_PARAMETER, typeRef.exceptionIndex)
            else -> throw AnnotationFormatError("Could not convert bytes for type annotations")
        }
    }

    private val reflectionTypePathContainer = TypePath::class.java.getDeclaredField("typePathContainer").apply { isAccessible = true }
    private val reflectionTypePathOffset = TypePath::class.java.getDeclaredField("typePathOffset").apply { isAccessible = true }
    private val TypePath.typePathContainer get() = reflectionTypePathContainer.get(this) as ByteArray
    private val TypePath.typePathOffset get() = reflectionTypePathOffset.getInt(this)

    private fun convertTypePath(typePath: TypePath): TypeAnnotation.LocationInfo? {
        val typePathContainer = typePath.typePathContainer
        val typePathOffset = typePath.typePathOffset
        val length = typePathContainer[typePathOffset] * 2 + 1
        return TypeAnnotation.LocationInfo
            .parseLocationInfo(ByteBuffer.wrap(typePathContainer.copyOfRange(typePathOffset, typePathOffset + length)))
    }

    fun convertAnnotatedType(
        annotations: List<TypeAnnotationNode>,
        decl: AnnotatedElement,
        container: TClass,
        type: Type?,
        filter: TypeAnnotationTarget,
    ): AnnotatedType {
        val typeAnnotations = convertTypeAnnotations(annotations, decl, container)
            .filter { it.targetInfo.target == filter }.toTypedArray()
        return AnnotatedTypeFactory.buildAnnotatedType(type, TypeAnnotation.LocationInfo.BASE_LOCATION, 
            typeAnnotations, typeAnnotations, decl)
    }

    fun convertAnnotatedTypes(
        annotations: List<TypeAnnotationNode>,
        decl: AnnotatedElement,
        container: TClass,
        types: Array<Type>,
        filter: TypeAnnotationTarget,
    ): Array<AnnotatedType> {
        val size = types.size
        val l = arrayOfNulls<ArrayList<TypeAnnotation>?>(size)
        convertTypeAnnotations(annotations, decl, container).asSequence()
            .filter { it.targetInfo.target == filter }
            .forEach { l.getOrSet(it.targetInfo.count, ::ArrayList).add(it) }
        return Array(size) { i ->
            val typeAnnotations = l[i]?.toTypedArray() ?: EMPTY_TYPE_ANNOTATION_ARRAY
            AnnotatedTypeFactory.buildAnnotatedType(types[i], TypeAnnotation.LocationInfo.BASE_LOCATION, 
                typeAnnotations, typeAnnotations, decl)
        }
    }

    fun convertAnnotatedSuperclass(
        annotations: List<TypeAnnotationNode>,
        decl: TClass,
    ): AnnotatedType {
        val supertype = decl.genericSuperclass ?: return EMPTY_ANNOTATED_TYPE
        return convertAnnotatedType(annotations, decl, decl, supertype, TypeAnnotationTarget.CLASS_EXTENDS)
    }

    fun convertAnnotatedInterfaces(
        annotations: List<TypeAnnotationNode>,
        decl: TClass,
    ): Array<AnnotatedType> =
        convertAnnotatedTypes(annotations, decl, decl, decl.genericInterfaces, TypeAnnotationTarget.CLASS_IMPLEMENTS)

    private fun convertTypeAnnotations(
        annotations: List<TypeAnnotationNode>?,
        baseDecl: AnnotatedElement,
        container: TClass,
    ): Array<TypeAnnotation> =
        annotations?.map { convertTypeAnnotationNode(it, baseDecl, container) }?.toTypedArray() ?: EMPTY_TYPE_ANNOTATION_ARRAY

    private val AnnotationNode.pairs: List<Pair<String, Any>>
        get() = values.orEmpty().chunked(2).map { (k, v) -> k as String to v }

    fun convertAnnotation(
        annotationNode: AnnotationNode,
        container: TClass,
        exceptionOnMissingAnnotationClass: Boolean,
    ): Annotation? {
        @Suppress("UNCHECKED_CAST")
        val annotationClass: Class<out Annotation> = try {
            parseType(annotationNode.desc, container) as Class<out Annotation>
        } catch (e: NoClassDefFoundError) {
            if (exceptionOnMissingAnnotationClass) throw TypeNotPresentException(annotationNode.desc, e)
            return null
        } catch (e: TypeNotPresentException) {
            if (exceptionOnMissingAnnotationClass) throw e
            return null
        }
        val type: AnnotationType = try {
            AnnotationType.getInstance(annotationClass)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val memberTypes = type.memberTypes()
        val memberValues: MutableMap<String, Any?> = LinkedHashMap(type.memberDefaults())
        for ((memberName, memberValue) in annotationNode.pairs) {
            val memberType = memberTypes[memberName] ?: continue
            memberValues[memberName] = convertMemberValue(memberType, memberValue, container)
        }
        return SunAnnotationParser.annotationForMap(annotationClass, memberValues)
    }

    private fun convertAnnotationThrowing(annotationNode: AnnotationNode, container: TClass) =
        convertAnnotation(annotationNode, container, true)!!

    fun convertParameterAnnotations(annotationsList: List<List<AnnotationNode>>, container: TClass): Array<Array<Annotation>> =
        convertParameterAnnotations2(annotationsList, container)

    private fun convertParameterAnnotations2(annotationsList: List<List<AnnotationNode>>, container: TClass): Array<Array<Annotation>> = Array(annotationsList.size) {
        annotationsList[it].mapNotNull { annotation ->
            convertAnnotation(annotation, container, false)
        }.toTypedArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertMemberValue(memberType: Class<*>, value: Any, container: TClass): Any = when (value) {
        is Array<*> -> convertEnumValue(memberType as Class<out Enum<*>>, value as Array<String>, container)
        is AnnotationNode -> convertAnnotationThrowing(value, container)
        is org.objectweb.asm.Type -> parseType(value.descriptor, container)
        is List<*> -> convertArray(memberType, value, container)
        else -> value
    }.let { memberType.cast(it) }

    private fun toClass(o: Type?): Class<*> {
        return if (o is GenericArrayType) java.lang.reflect.Array.newInstance(toClass(o.genericComponentType), 0).javaClass
        else o as Class<*>
    }

    private fun convertEnumValue(enumType: Class<out Enum<*>>, value: Array<String>, container: TClass): Any =
        parseType(value[0], container).cast(java.lang.Enum.valueOf(enumType, value[1]))!!

    private fun parseType(sig: String, container: TClass): Class<*> {
        if (sig == "V") return Void.TYPE
        val reify = Reifier.make(CoreReflectionFactory.make(container.clazz, ClassScope.make(container.clazz)))
        SignatureParser.make().parseTypeSig(sig).accept(reify)
        return toClass(reify.result)
    }

    private fun convertArray(arrayType: Class<*>, value: List<*>, container: TClass): Any = when (val type = arrayType.componentType) {
        Byte::class.javaPrimitiveType -> value.map { it as Byte }.toByteArray()
        Char::class.javaPrimitiveType -> value.map { it as Char }.toCharArray()
        Double::class.javaPrimitiveType -> value.map { it as Double }.toDoubleArray()
        Float::class.javaPrimitiveType -> value.map { it as Float }.toFloatArray()
        Int::class.javaPrimitiveType -> value.map { it as Int }.toIntArray()
        Long::class.javaPrimitiveType -> value.map { it as Long }.toLongArray()
        Short::class.javaPrimitiveType -> value.map { it as Short }.toShortArray()
        Boolean::class.javaPrimitiveType -> value.map { it as Boolean }.toBooleanArray()
        String::class.java -> value.map { it as String }.toTypedArray()
        Class::class.java -> value.map { it as Class<*> }.toTypedArray()
        else -> if (type.isEnum) {
            convertArray(type, value) {
                @Suppress("UNCHECKED_CAST")
                convertEnumValue(type as Class<out Enum<*>>, it as Array<String>, container)
            }
        } else {
            assert(type.isAnnotation)
            convertArray(type, value) {
                convertAnnotationThrowing(value as AnnotationNode, container)
            }
        }
    }

    private inline fun convertArray(type: Class<*>, values: List<*>, convert: (Any?) -> Any): Any {
        @Suppress("UNCHECKED_CAST")
        val result = java.lang.reflect.Array.newInstance(type, values.size) as Array<Any>
        for ((i, value) in values.withIndex()) {
            @Suppress("UNCHECKED_CAST")
            result[i] = convert(value)
        }
        return result
    }

    fun convertAnnotations(annotationNodes: List<AnnotationNode>, declaringClass: TClass) =
        annotationNodes.map { convertAnnotationThrowing(it, declaringClass) }.associateBy { it.annotationClass.java }
}

private fun <T: Any> Array<T?>.getOrSet(index: Int, init: () -> T): T {
    var value = this[index]
    if (value == null) {
        value = init()
        this[index] = value
    }
    return value
}
