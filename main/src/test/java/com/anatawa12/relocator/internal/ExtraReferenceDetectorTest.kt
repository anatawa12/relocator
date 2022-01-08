package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.reference.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.*
import io.kotest.matchers.*

internal class ExtraReferenceDetectorTest : DescribeSpec() {
    private val env = newComputeReferenceEnvironment()
    private val location = Location.None
    val voidMethod = MethodDescriptor("()V")

    private fun detectExtraReference(list: List<Insn>): Set<Reference> {
        val references = mutableSetOf<Reference>()
        ExtraReferenceDetector(true, voidMethod, 5,
            emptyMap(), env, location, list, references, emptySet())
            .collectExtraReferences()
        return references
    }

    private fun checkOnStackValue(list: List<Insn>): Any {
        val references = mutableSetOf<Reference>()
        val detector = ExtraReferenceDetector(true, voidMethod, 5,
            emptyMap(), env, location, list, references, emptySet())
        detector.collectExtraReferences()
        return detector.pop()
    }

    init {
        describe("method reference") {
            it("static string.format") {
                detectExtraReference(stringFormat) shouldContain PartialMethodReference(
                    "java/lang/String",
                    "format",
                    "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)")
            }
            it("virtual string.indexOf") {
                detectExtraReference(stringIndexOf) shouldContain PartialMethodReference(
                    "java/lang/String",
                    "indexOf",
                    "(I)")
            }
        }

        it("field reference") {
            detectExtraReference(stringCaseInsensitiveOrder) shouldContain PartialFieldReference(
                "java/lang/String",
                "CASE_INSENSITIVE_ORDER")
        }

        describe("on stack class value") {
            it("reflection class with static Class.forName(String)") {
                checkOnStackValue(simpleReflectionString) shouldBe ConstantClass("L${"java/lang/String"};")
            }
            it("reflection class with static Class.forName(String, boolean, ClassLoader)") {
                checkOnStackValue(withClassLoaderReflectionString) shouldBe ConstantClass("L${"java/lang/String"};")
            }
            it("reflection class with static ClassLoader.loadClass(String)") {
                checkOnStackValue(loadClassFunctionReflectionString) shouldBe ConstantClass("L${"java/lang/String"};")
            }
            it("primitive static TYPE field reference") {
                checkOnStackValue(listOf(
                    FieldInsn(FieldInsnType.GETSTATIC, FieldReference("java/lang/Integer", "TYPE", "Ljava/lang/Class;")),
                )) shouldBe ConstantClass("I")
            }
        }

        describe("on stack array of class value") {
            it ("with ldc class constants") {
                checkOnStackValue(localeStringObjectClassList) shouldBe
                        listOf(
                            ConstantClass("Ljava/util/Locale;"),
                            ConstantClass("Ljava/lang/String;"),
                            ConstantClass("[Ljava/lang/Object;"),
                        )
            }
            it("with external class reference") {
                checkOnStackValue(intClassList) shouldBe
                        listOf(ConstantClass("I"))
            }
        }
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
                "(L${"java/lang/String"};ZL${"java/lang/ClassLoader"};)L${"java/lang/Class"};"),
            false))
    }

    private val loadClassFunctionReflectionString = buildList {
        add(LdcInsn(ConstantClass("Lcom/anatawa12/Test;")))
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
