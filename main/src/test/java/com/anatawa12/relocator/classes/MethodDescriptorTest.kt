package com.anatawa12.relocator.classes

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.*

internal class MethodDescriptorTest : DescribeSpec() {
    init {
        describe("constructor") {
            it ("returns void") {
                MethodDescriptor(void).descriptor shouldBe "()V"
            }
            it ("returns string") {
                MethodDescriptor(string).descriptor shouldBe "()L${"java/lang/String"};"
            }
            it ("int, int -> void") {
                MethodDescriptor(void, int, int).descriptor shouldBe "(II)V"
            }
            it ("string, int -> void") {
                MethodDescriptor(void, string, int).descriptor shouldBe "(L${"java/lang/String"};I)V"
            }
            it ("string[], int -> void") {
                MethodDescriptor(void, stringArray, int).descriptor shouldBe "([L${"java/lang/String"};I)V"
            }
            it ("string[], int[] -> void") {
                MethodDescriptor(void, string, intArray).descriptor shouldBe "(L${"java/lang/String"};[I)V"
            }
            it ("string, int[] -> int[]") {
                MethodDescriptor(intArray, string, intArray).descriptor shouldBe "(L${"java/lang/String"};[I)[I"
            }
        }

        describe("arguments and returns") {
            it ("empty args") {
                MethodDescriptor("()V").arguments shouldBe listOf()
            }
            it ("int, int args") {
                MethodDescriptor("(II)V").arguments shouldBe listOf(int, int)
            }
            it ("string, int args") {
                MethodDescriptor("(L${"java/lang/String"};I)V").arguments shouldBe listOf(string, int)
            }

            it ("returns void") {
                MethodDescriptor("()V").returns shouldBe void
            }
            it ("returns int") {
                MethodDescriptor("()I").returns shouldBe int
            }
            it ("returns string") {
                MethodDescriptor("()L${"java/lang/String"};").returns shouldBe string
            }
        }
    }

    val void = TypeDescriptor("V")
    val int = TypeDescriptor("I")
    val intArray = TypeDescriptor("[I")
    val string = TypeDescriptor("L${"java/lang/String"};")
    val stringArray = TypeDescriptor("[L${"java/lang/String"};")
}
