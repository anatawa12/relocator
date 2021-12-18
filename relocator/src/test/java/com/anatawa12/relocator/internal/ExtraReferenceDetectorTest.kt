package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.reference.*

import org.amshove.kluent.*
import org.junit.jupiter.api.Test

internal class ExtraReferenceDetectorTest {
    private val env = newComputeReferenceEnvironment()
    private val location = Location.None

    private fun detectExtraReference(list: List<Insn>): Set<Reference> {
        val references = mutableSetOf<Reference>()
        ExtraReferenceDetector(true, "()V", 5,
            env, location, list, references, emptySet())
            .collectExtraReferences()
        return references
    }

    private fun checkOnStackValue(list: List<Insn>): Any {
        val references = mutableSetOf<Reference>()
        val detector = ExtraReferenceDetector(true, "()V", 5,
            env, location, list, references, emptySet())
        detector.collectExtraReferences()
        return detector.pop()
    }

    @Test
    fun detectExtraMethodReference() {
        detectExtraReference(stringFormat) `should contain` PartialMethodReference(
            "java/lang/String",
            "format",
            "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)")
        detectExtraReference(stringIndexOf) `should contain` PartialMethodReference(
            "java/lang/String",
            "indexOf",
            "(I)")
    }
    @Test
    fun detectExtraFieldReference() {
        detectExtraReference(stringCaseInsensitiveOrder) `should contain` PartialFieldReference(
            "java/lang/String",
            "CASE_INSENSITIVE_ORDER")
    }

    @Test
    fun resolveOnStackClass() {
        checkOnStackValue(simpleReflectionString) `should be equal to`
                ConstantClass("L${"java/lang/String"};")
        checkOnStackValue(withClassLoaderReflectionString) `should be equal to`
                ConstantClass("L${"java/lang/String"};")
        checkOnStackValue(loadClassFunctionReflectionString) `should be equal to`
                ConstantClass("L${"java/lang/String"};")
        checkOnStackValue(listOf(
            FieldInsn(FieldInsnType.GETSTATIC, FieldReference("java/lang/Integer", "TYPE", "Ljava/lang/Class;"))
        )) `should be equal to` ConstantClass("I")
    }

    @Test
    fun resolveOnStackClassArray() {
        checkOnStackValue(localeStringObjectClassList) `should be equal to`
                listOf(ConstantClass("Ljava/util/Locale;"), ConstantClass("Ljava/lang/String;"), ConstantClass("[Ljava/lang/Object;"))
        checkOnStackValue(intClassList) `should be equal to`
                listOf(ConstantClass("I"))
    }

    private val simpleReflectionString = buildList {
        add(LdcInsn(ConstantString("java.lang.String")))
        add(MethodInsn(MethodInsnType.INVOKESTATIC,
            MethodReference(
                "java/lang/Class",
                "forName",
                "(L${"java/lang/String"};)L${"java/lang/Class"};"),
            false))
    }

    private val withClassLoaderReflectionString = buildList {
        add(LdcInsn(ConstantString("java.lang.String")))
        add(LdcInsn(ConstantInt(1)))
        add(LdcInsn(ConstantClass("L${"com/anatawa12/Test"};")))
        add(MethodInsn(MethodInsnType.INVOKEVIRTUAL,
            MethodReference(
                "java/lang/Class",
                "getClassLoader",
                "()L${"java/lang/ClassLoader"};"),
            false))
        add(MethodInsn(MethodInsnType.INVOKESTATIC,
            MethodReference(
                "java/lang/Class",
                "forName",
                "(L${"java/lang/String"};BL${"java/lang/ClassLoader"};)L${"java/lang/Class"};"),
            false))
    }

    private val loadClassFunctionReflectionString = buildList {
        add(LdcInsn(ConstantClass("com/anatawa12/Test")))
        add(MethodInsn(MethodInsnType.INVOKEVIRTUAL,
            MethodReference(
                "java/lang/Class",
                "getClassLoader",
                "()L${"java/lang/ClassLoader"};"),
            false))
        add(LdcInsn(ConstantString("java.lang.String")))
        add(MethodInsn(MethodInsnType.INVOKEVIRTUAL,
            MethodReference(
                "java/lang/ClassLoader",
                "loadClass",
                "(L${"java/lang/String"};)L${"java/lang/Class"};"),
            false))
    }

    private val localeStringObjectClassList = buildList {
        add(LdcInsn(ConstantInt(3)))
        add(TypeInsn(TypeInsnType.ANEWARRAY, ClassReference("java/lang/Class")))

        add(SimpleInsn(SimpleInsnType.DUP))
        add(LdcInsn(ConstantInt(0)))
        add(LdcInsn(ConstantClass("Ljava/util/Locale;")))
        add(TypedInsn(TypedInsnType.ASTORE, VMType.Reference))

        add(SimpleInsn(SimpleInsnType.DUP))
        add(LdcInsn(ConstantInt(1)))
        add(LdcInsn(ConstantClass("Ljava/lang/String;")))
        add(TypedInsn(TypedInsnType.ASTORE, VMType.Reference))

        add(SimpleInsn(SimpleInsnType.DUP))
        add(LdcInsn(ConstantInt(2)))
        add(LdcInsn(ConstantClass("[Ljava/lang/Object;")))
        add(TypedInsn(TypedInsnType.ASTORE, VMType.Reference))
    }

    private val intClassList = buildList {
        add(LdcInsn(ConstantInt(1)))
        add(TypeInsn(TypeInsnType.ANEWARRAY, ClassReference("java/lang/Class")))

        add(SimpleInsn(SimpleInsnType.DUP))
        add(LdcInsn(ConstantInt(0)))
        add(FieldInsn(FieldInsnType.GETSTATIC,
            FieldReference("java/lang/Integer",
                "TYPE",
                "Ljava/lang/Class;")))
        add(TypedInsn(TypedInsnType.ASTORE, VMType.Reference))
    }

    private val stringFormat = buildList {
        add(LdcInsn(ConstantClass("Ljava/lang/String;")))
        add(LdcInsn(ConstantString("format")))

        addAll(localeStringObjectClassList)

        add(MethodInsn(MethodInsnType.INVOKEVIRTUAL,
            MethodReference("java/lang/Class",
                "getMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"), 
            false))
    }

    private val stringIndexOf = buildList {
        add(LdcInsn(ConstantClass("Ljava/lang/String;")))
        add(LdcInsn(ConstantString("indexOf")))

        addAll(intClassList)

        add(MethodInsn(MethodInsnType.INVOKEVIRTUAL,
            MethodReference("java/lang/Class",
                "getMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"), 
            false))
    }

    private val stringCaseInsensitiveOrder = buildList {
        add(LdcInsn(ConstantClass("Ljava/lang/String;")))
        add(LdcInsn(ConstantString("CASE_INSENSITIVE_ORDER")))

        add(MethodInsn(MethodInsnType.INVOKEVIRTUAL,
            MethodReference("java/lang/Class",
                "getField",
                "(Ljava/lang/String;)Ljava/lang/reflect/Field;"), 
            false))
    }
}
