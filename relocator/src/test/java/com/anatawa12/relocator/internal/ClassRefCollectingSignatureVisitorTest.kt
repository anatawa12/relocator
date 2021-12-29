package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.ClassInnerClass
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.reference.ClassReference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

internal class ClassRefCollectingSignatureVisitorTest : DescribeSpec() {
    fun test(signature: String?, vararg innerClasses: ClassInnerClass): Set<ClassReference> {
        val container = InnerClassContainer(innerClasses.toList())
        val env = newComputeReferenceEnvironment()
        val collection = hashSetOf<ClassReference>()
        ClassRefCollectingSignatureVisitor.acceptSignature(collection, env, container, signature, Location.Class(""))
        return collection
    }

    init {
        it("null signature") {
            test(null) shouldBe setOf()
        }
        describe ("simple signature") {
            val values = listOf(
                // type parameters
                "<A:>()V" to setOf(),
                "<A:Ljava/lang/Object;>()V" to setOf(ClassReference("java/lang/Object")),
                "Ljava/util/Map\$Entry<Ljava/lang/String;Ljava/lang/String;>;" to setOf(
                    ClassReference("java/util/Map\$Entry"),
                    ClassReference("java/lang/String"),
                )
            )
            for ((sig, expect) in values) {
                it (sig) {
                    test(sig) shouldBe expect
                }
            }
        }

        describe("generic inner class signature") {
            /*
            package com.anatawa12.relocator;
            class Test<T> {
                class Inner {
                    class Inner2 {}
                }
                static class Child<T> {
                    class Inner {}
                }
            }
            */
            val inner = ClassInnerClass(0x0,
                ClassReference("com/anatawa12/relocator/Test\$Inner"),
                ClassReference("com/anatawa12/relocator/Test"),"Inner")
            //val child = InnerClassNode("com/anatawa12/relocator/Test\$Child",
            //    "com/anatawa12/relocator/Test","Child", 0x8)
            val childInner = ClassInnerClass(0x0,
                ClassReference("com/anatawa12/relocator/Test\$Child\$Inner"),
                ClassReference("com/anatawa12/relocator/Test\$Child"),"Inner")
            val innnerInner2 = ClassInnerClass(0x0,
                ClassReference("com/anatawa12/relocator/Test\$Inner\$Inner2"),
                ClassReference("com/anatawa12/relocator/Test\$Inner"),"Inner2")

            it ("inner of generic") {
                test("Lcom/anatawa12/relocator/Test<Ljava/lang/String;>.Inner;", inner)
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/Test\$Inner"),
                        ClassReference("java/lang/String"),
                    ))
            }
            it ("inner of nested generic of non-generic") {
                test("Lcom/anatawa12/relocator/Test\$Child<Ljava/lang/String;>.Inner;", childInner)
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/Test\$Child\$Inner"),
                        ClassReference("java/lang/String"),
                    ))
            }
            it ("inner of inner of generic") {
                test("Lcom/anatawa12/relocator/Test<Ljava/lang/String;>.Inner.Inner2;", inner, innnerInner2)
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/Test\$Inner\$Inner2"),
                        ClassReference("java/lang/String"),
                    ))
            }
        }
    }
}
