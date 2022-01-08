package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.ClassInnerClass
import com.anatawa12.relocator.classes.ClassSignature
import com.anatawa12.relocator.classes.MethodSignature
import com.anatawa12.relocator.classes.TypeSignature
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.internal.SignatureClassRefCollector.Utils.processClassSignature
import com.anatawa12.relocator.internal.SignatureClassRefCollector.Utils.processMethodSignature
import com.anatawa12.relocator.internal.SignatureClassRefCollector.Utils.processTypeSignature
import com.anatawa12.relocator.reference.ClassReference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

internal class SignatureClassRefCollectorTest : DescribeSpec() {
    fun <S : Any> test(
        signature: S?,
        processor: (
            references: MutableCollection<in ClassReference>,
            env: ComputeReferenceEnvironment,
            innerClasses: InnerClassContainer,
            signature: S?,
            location: Location,
        ) -> Unit,
        vararg innerClasses: ClassInnerClass,
    ): Set<ClassReference> {
        val container = InnerClassContainer(innerClasses.toList())
        val env = newComputeReferenceEnvironment()
        val collection = hashSetOf<ClassReference>()
        processor(collection, env, container, signature, Location.Class(""))
        return collection
    }

    init {
        it("null signature") {
            test(null, ::processClassSignature) shouldBe setOf()
            test(null, ::processMethodSignature) shouldBe setOf()
            test(null, ::processTypeSignature) shouldBe setOf()
        }
        describe("simple signature") {
            it("<A:Ljava/lang/Object;>Ljava/io/InputStream;") {
                test(ClassSignature.parse(testCase.name.testName), ::processClassSignature) shouldBe setOf(
                    ClassReference("java/lang/Object"),
                    ClassReference("java/io/InputStream"),
                )
            }
            it("<A:Ljava/lang/Object;>()V") {
                test(MethodSignature.parse(testCase.name.testName), ::processMethodSignature) shouldBe setOf(
                    ClassReference("java/lang/Object"),
                )
            }
            it("Ljava/util/Map\$Entry<Ljava/lang/String;Ljava/lang/String;>;") {
                test(TypeSignature.parse(testCase.name.testName), ::processTypeSignature) shouldBe setOf(
                    ClassReference("java/util/Map\$Entry"),
                    ClassReference("java/lang/String"),
                )
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
                ClassReference("com/anatawa12/relocator/Test"), "Inner")
            //val child = InnerClassNode("com/anatawa12/relocator/Test\$Child",
            //    "com/anatawa12/relocator/Test","Child", 0x8)
            val childInner = ClassInnerClass(0x0,
                ClassReference("com/anatawa12/relocator/Test\$Child\$Inner"),
                ClassReference("com/anatawa12/relocator/Test\$Child"), "Inner")
            val innnerInner2 = ClassInnerClass(0x0,
                ClassReference("com/anatawa12/relocator/Test\$Inner\$Inner2"),
                ClassReference("com/anatawa12/relocator/Test\$Inner"), "Inner2")

            it("inner of generic") {
                test(TypeSignature.parse("Lcom/anatawa12/relocator/Test<Ljava/lang/String;>.Inner;"),
                    ::processTypeSignature, inner)
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/Test\$Inner"),
                        ClassReference("java/lang/String"),
                    ))
            }
            it("inner of nested generic of non-generic") {
                test(TypeSignature.parse("Lcom/anatawa12/relocator/Test\$Child<Ljava/lang/String;>.Inner;"),
                    ::processTypeSignature, childInner)
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/Test\$Child\$Inner"),
                        ClassReference("java/lang/String"),
                    ))
            }
            it("inner of inner of generic") {
                test(TypeSignature.parse("Lcom/anatawa12/relocator/Test<Ljava/lang/String;>.Inner.Inner2;"),
                    ::processTypeSignature, inner, innnerInner2)
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/Test\$Inner\$Inner2"),
                        ClassReference("java/lang/String"),
                    ))
            }
        }
    }
}
