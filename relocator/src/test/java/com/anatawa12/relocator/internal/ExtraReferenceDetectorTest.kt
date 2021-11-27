package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagostic.Location
import com.anatawa12.relocator.internal.ExtraReferenceDetector.InsnContainer
import com.anatawa12.relocator.internal.ExtraReferenceDetector.detectExtraReference
import com.anatawa12.relocator.internal.ExtraReferenceDetector.resolveOnStackClass
import com.anatawa12.relocator.internal.ExtraReferenceDetector.resolveOnStackClassArray
import com.anatawa12.relocator.reference.FieldReference
import com.anatawa12.relocator.reference.MethodReference

import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

internal class ExtraReferenceDetectorTest {
    val env = newComputeReferenceEnvironment()
    val location = Location.None

    @Test
    fun detectExtraMethodReference() {
        detectExtraReference(stringFormat.last, env, location) `should be equal to` MethodReference(
            "java/lang/String",
            "format",
            "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)")
        detectExtraReference(stringIndexOf.last, env, location) `should be equal to` MethodReference(
            "java/lang/String",
            "indexOf",
            "(I)")
    }
    @Test
    fun detectExtraFieldReference() {
        detectExtraReference(stringCaseInsensitiveOrder.last, env, location) `should be equal to` FieldReference(
            "java/lang/String",
            "CASE_INSENSITIVE_ORDER",
            null)
    }

    @Test
    fun resolveOnStackClass() {
        resolveOnStackClass(InsnContainer.get(simpleReflectionString.last), env, location) `should be equal to`
                "L${"java/lang/String"};"
        resolveOnStackClass(InsnContainer.get(withClassLoaderReflectionString.last), env, location) `should be equal to`
                "L${"java/lang/String"};"
        resolveOnStackClass(InsnContainer.get(loadClassFunctionReflectionString.last), env, location) `should be equal to`
                "L${"java/lang/String"};"
        resolveOnStackClass(InsnContainer.get(FieldInsnNode(GETSTATIC, 
            "java/lang/Integer", 
            "TYPE", 
            "Ljava/lang/Class;")), env, location) `should be equal to` "I"
    }

    @Test
    fun resolveOnStackClassArray() {
        resolveOnStackClassArray(InsnContainer.get(localeStringObjectClassList.last), env, location) `should be equal to`
                listOf("Ljava/util/Locale;", "Ljava/lang/String;", "[Ljava/lang/Object;")
        resolveOnStackClassArray(InsnContainer.get(intClassList.last), env, location) `should be equal to`
                listOf("I")
    }

    val simpleReflectionString = InsnList().apply {
        add(LdcInsnNode("java.lang.String"))
        add(MethodInsnNode(INVOKESTATIC,
            "java/lang/Class",
            "forName",
            "(L${"java/lang/String"};)L${"java/lang/Class"};"))
    }

    val withClassLoaderReflectionString = InsnList().apply {
        add(LdcInsnNode("java.lang.String"))
        add(InsnNode(ICONST_1))
        add(LdcInsnNode(Type.getObjectType("com/anatawa12/Test")))
        add(MethodInsnNode(INVOKEVIRTUAL,
            "java/lang/Class",
            "getClassLoader",
            "()L${"java/lang/ClassLoader"};"))
        add(MethodInsnNode(INVOKESTATIC,
            "java/lang/Class",
            "forName",
            "(L${"java/lang/String"};BL${"java/lang/ClassLoader"};)L${"java/lang/Class"};"))
    }

    val loadClassFunctionReflectionString = InsnList().apply {
        add(LdcInsnNode(Type.getObjectType("com/anatawa12/Test")))
        add(MethodInsnNode(INVOKEVIRTUAL,
            "java/lang/Class",
            "getClassLoader",
            "()L${"java/lang/ClassLoader"};"))
        add(LdcInsnNode("java.lang.String"))
        add(MethodInsnNode(INVOKEVIRTUAL,
            "java/lang/ClassLoader",
            "loadClass",
            "(L${"java/lang/String"};)L${"java/lang/Class"};"))
    }

    val localeStringObjectClassList = InsnList().apply {
        add(InsnNode(ICONST_3))
        add(TypeInsnNode(ANEWARRAY, "java/lang/Class"))

        add(InsnNode(DUP))
        add(InsnNode(ICONST_0))
        add(LdcInsnNode(Type.getType("Ljava/util/Locale;")))
        add(InsnNode(AASTORE))

        add(InsnNode(DUP))
        add(InsnNode(ICONST_1))
        add(LdcInsnNode(Type.getType("Ljava/lang/String;")))
        add(InsnNode(AASTORE))

        add(InsnNode(DUP))
        add(InsnNode(ICONST_2))
        add(LdcInsnNode(Type.getType("[Ljava/lang/Object;")))
        add(InsnNode(AASTORE))
    }

    val intClassList = InsnList().apply {
        add(InsnNode(ICONST_1))
        add(TypeInsnNode(ANEWARRAY, "java/lang/Class"))

        add(InsnNode(DUP))
        add(InsnNode(ICONST_0))
        add(FieldInsnNode(GETSTATIC,
            "java/lang/Integer",
            "TYPE",
            "Ljava/lang/Class;"))
        add(InsnNode(AASTORE))
    }

    val stringFormat = InsnList().apply {
        add(LdcInsnNode(Type.getType("Ljava/lang/String;")))
        add(LdcInsnNode("format"))

        add(localeStringObjectClassList.clone())

        add(MethodInsnNode(INVOKEVIRTUAL,
            "java/lang/Class",
            "getMethod",
            "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"))
    }

    val stringIndexOf = InsnList().apply {
        add(LdcInsnNode(Type.getType("Ljava/lang/String;")))
        add(LdcInsnNode("indexOf"))

        add(intClassList.clone())

        add(MethodInsnNode(INVOKEVIRTUAL,
            "java/lang/Class",
            "getMethod",
            "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"))
    }

    val stringCaseInsensitiveOrder = InsnList().apply {
        add(LdcInsnNode(Type.getType("Ljava/lang/String;")))
        add(LdcInsnNode("CASE_INSENSITIVE_ORDER"))

        add(MethodInsnNode(INVOKEVIRTUAL,
            "java/lang/Class",
            "getField",
            "(Ljava/lang/String;)Ljava/lang/reflect/Field;"))
    }

    private fun InsnList.clone(): InsnList = InsnList().also { newList ->
        val labelMap = mutableMapOf<LabelNode, LabelNode>()
        for (node in this) {
            newList.add(node.clone(labelMap))
        }
    }
}
