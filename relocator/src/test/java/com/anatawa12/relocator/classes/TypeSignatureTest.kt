package com.anatawa12.relocator.classes

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

internal class TypeSignatureTest : DescribeSpec() {
    private fun testParse(
        signature: String,
        kind: TypeSignature.Kind,
        expect: TypeSignature,
    ) = testParse(signature, kind, expect) { _, _, _ -> }
    private inline fun testParse(
        signature: String,
        kind: TypeSignature.Kind,
        expect: TypeSignature,
        crossinline extraTests: suspend DescribeSpecContainerScope.(
            signature: String, 
            parsed: TypeSignature, 
            expect: TypeSignature,
        ) -> Unit
    ) {
        describe(signature) {
            val parsed = TypeSignature.parse(signature)
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
            it("type kind check: '$signature'") {
                parsed.kind shouldBe kind
            }
            it("array type kind check: '$signature'.array(1)") {
                parsed.array(1).kind shouldBe TypeSignature.Kind.Array
            }
            extraTests(signature, parsed, expect)
        }
    }

    init {
        for (primitive in primitives) {
            testParse(primitive.signature, TypeSignature.Kind.Primitive, primitive) { signature, parsed, expect ->
                it("primitive instance check: '$signature'") {
                    parsed shouldBeSameInstanceAs expect
                }
            }
        }

        testParse("TT;", TypeSignature.Kind.TypeArgument, TypeSignature.argumentOf("T"))

        testParse("L${"java/lang/String"};", TypeSignature.Kind.Class,
            TypeSignature.classOf("java/lang/String"))

        testParse("L${"java/lang/List"}<L${"java/lang/String"};>;", TypeSignature.Kind.Class,
            TypeSignature.ClassBuilder("java/lang/List")
                .addTypeArgument(TypeSignature.classOf("java/lang/String"))
                .build())

        testParse("L${"test/Outer"}<L${"java/lang/String"};>.Inner<L${"java/lang/Integer"};>;", TypeSignature.Kind.Class,
            TypeSignature.ClassBuilder("test/Outer")
                .addTypeArgument(TypeSignature.classOf("java/lang/String"))
                .innerClassName("Inner")
                .addTypeArgument(TypeSignature.classOf("java/lang/Integer"))
                .build())
    }

    companion object {
        private val primitives = arrayOf(
            TypeSignature.VOID,
            TypeSignature.BYTE,
            TypeSignature.CHAR,
            TypeSignature.DOUBLE,
            TypeSignature.FLOAT,
            TypeSignature.INT,
            TypeSignature.LONG,
            TypeSignature.SHORT,
            TypeSignature.BOOLEAN,
        )
    }
}
