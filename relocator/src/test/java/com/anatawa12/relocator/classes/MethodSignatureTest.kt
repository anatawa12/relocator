package com.anatawa12.relocator.classes

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

internal class MethodSignatureTest : DescribeSpec() {
    private inline fun testParse(
        signature: String,
        expect: MethodSignature,
        crossinline extraTests: suspend DescribeSpecContainerScope.(signature: String, parsed: MethodSignature, expect: MethodSignature) -> Unit
    ) {
        describe(signature) {
            val parsed = MethodSignature.parse(signature)
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

        testParse("<T:$objectSignature>()$objectSignature", MethodSignature.Builder()
            .addTypeParam(TypeParameter.of("T", objectSignature))
            .returns(objectSignature)
            .build()) { signature, parsed, _ ->
            it("type parameter of '$signature'") {
                parsed.typeParameters shouldBe listOf(TypeParameter.of("T", objectSignature))
            }
            it("type super class of '$signature'") {
                parsed.returns shouldBe objectSignature
            }
        }

        testParse("<T:$objectSignature>()$objectSignature^$listSignature", MethodSignature.Builder()
            .addTypeParam(TypeParameter.of("T", objectSignature))
            .returns(objectSignature)
            .addThrows(listSignature)
            .build()) { signature, parsed, _ ->
            it("type parameter of '$signature'") {
                parsed.typeParameters shouldBe listOf(TypeParameter.of("T", objectSignature))
            }
            it("super class of '$signature'") {
                parsed.returns shouldBe objectSignature
            }
            it("throws type of '$signature'") {
                parsed.throwsTypes shouldBe listOf(listSignature)
            }
        }

        testParse("<T::$listSignature>($objectSignature)$listSignature", MethodSignature.Builder()
            .addTypeParam(TypeParameter.Builder("T").addInterfaceBound(listSignature).build())
            .addValueParam(objectSignature)
            .returns(listSignature)
            .build()) { signature, parsed, _ ->
            it("type parameter of '$signature'") {
                parsed.typeParameters shouldBe listOf(TypeParameter.Builder("T")
                    .addInterfaceBound(listSignature)
                    .build())
            }
            it("value parameter of '$signature'") {
                parsed.valueParameters shouldBe listOf(objectSignature)
            }
            it("return type of '$signature'") {
                parsed.returns shouldBe listSignature
            }
        }

        describe("falling builders") {
            it ("building with missing return type") {
                shouldThrow<IllegalStateException> {
                    MethodSignature.Builder().build()
                }
            }
            it ("setting void as value parameter class") {
                shouldThrow<IllegalArgumentException> {
                    MethodSignature.Builder().addValueParam(TypeSignature.VOID)
                }
            }
            it ("setting primitive as throws class") {
                shouldThrow<IllegalArgumentException> {
                    MethodSignature.Builder().addThrows(TypeSignature.INT)
                }
            }
        }
    }
} 
