package com.anatawa12.relocator.classes

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

internal class ClassSignatureTest : DescribeSpec() {
    private inline fun testParse(
        signature: String,
        expect: ClassSignature,
        crossinline extraTests: suspend DescribeSpecContainerScope.(signature: String, parsed: ClassSignature, expect: ClassSignature) -> Unit
    ) {
        describe(signature) {
            val parsed = ClassSignature.parse(signature)
            it("signature of parsed signature should be same instance as parameter: '$signature'") {
                parsed.signature shouldBeSameInstanceAs signature
            }
            it("toString of parsed signature should be same instance as parameter: '$signature'") {
                parsed.toString() shouldBeSameInstanceAs signature
            }
            it("check the signature of built instance: '$signature'") {
                expect.signature shouldBe signature
            }
            it("check toString of built instance: '$signature'") {
                expect.toString() shouldBe signature
            }
            it("equality of signature: '$signature'") {
                expect.signature shouldBe signature
            }
            extraTests(signature, parsed, expect)
        }
    }

    init {
        val objectSignature = TypeSignature.parse("L${"java/lang/Object"};")
        val listSignature = TypeSignature.parse("L${"java/util/List"};")

        testParse("<T:$objectSignature>$objectSignature", ClassSignature.Builder()
            .addTypeParam(TypeParameter.of("T", objectSignature))
            .superClass(objectSignature)
            .build()) { signature, parsed, _ ->
            it("type parameter of '$signature'") {
                parsed.typeParameters shouldBe listOf(TypeParameter.of("T", objectSignature))
            }
            it("type super class of '$signature'") {
                parsed.superClass shouldBe objectSignature
            }
        }

        testParse("<T:$objectSignature>$objectSignature$listSignature", ClassSignature.Builder()
            .addTypeParam(TypeParameter.of("T", objectSignature))
            .superClass(objectSignature)
            .addInterface(listSignature)
            .build()) { signature, parsed, _ ->
            it("type parameter of '$signature'") {
                parsed.typeParameters shouldBe listOf(TypeParameter.of("T", objectSignature))
            }
            it("type super class of '$signature'") {
                parsed.superClass shouldBe objectSignature
            }
            it("type super interface of '$signature'") {
                parsed.superInterfaces shouldBe listOf(listSignature)
            }
        }

        testParse("<T::$listSignature>$objectSignature$listSignature", ClassSignature.Builder()
            .addTypeParam(TypeParameter.Builder("T").addInterfaceBound(listSignature).build())
            .superClass(objectSignature)
            .addInterface(listSignature)
            .build()) { signature, parsed, _ ->
            it("type parameter of '$signature'") {
                parsed.typeParameters shouldBe listOf(TypeParameter.Builder("T")
                    .addInterfaceBound(listSignature)
                    .build())
            }
            it("type super class of '$signature'") {
                parsed.superClass shouldBe objectSignature
            }
            it("type super interface of '$signature'") {
                parsed.superInterfaces shouldBe listOf(listSignature)
            }
        }

        describe("falling builders") {
            it ("building with missing super class name") {
                shouldThrow<IllegalStateException> {
                    ClassSignature.Builder().build()
                }
            }
            it ("setting primitive as super class") {
                shouldThrow<IllegalArgumentException> {
                    ClassSignature.Builder().superClass(TypeSignature.INT)
                }
            }
            it ("setting double super class") {
                shouldThrow<IllegalStateException> {
                    ClassSignature.Builder().superClass(objectSignature).superClass(objectSignature)
                }
            }
            it ("adding primitive as super interface") {
                shouldThrow<IllegalArgumentException> {
                    ClassSignature.Builder().addInterface(TypeSignature.INT)
                }
            }
        }
    }
}
