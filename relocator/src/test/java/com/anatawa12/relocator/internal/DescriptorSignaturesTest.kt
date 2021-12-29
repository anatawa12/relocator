package com.anatawa12.relocator.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.matchers.shouldBe

internal class DescriptorSignaturesTest : DescribeSpec() {
    suspend inline fun <T> DescribeSpecContainerScope.assertIAE(desc: String, crossinline test: (String) -> T) {
        it ("falling '$desc'") {
            shouldThrow<IllegalArgumentException> {
                test(desc)
            }
        }
    }

    suspend inline fun <T> DescribeSpecContainerScope.assertSuccessful(desc: String, crossinline test: (String) -> T) {
        it ("successful '$desc'") {
            test(desc)
        }
    }

    init {
        describe("parsing method descriptor") {
            it (" -> void") {
                DescriptorSignatures.parseMethodDesc("()V") shouldBe intArrayOf()
            }
            it ("string -> void") {
                DescriptorSignatures.parseMethodDesc("(L${"java/lang/String"};)V") shouldBe intArrayOf(19)
            }
            it ("strubgm int -> void") {
                DescriptorSignatures.parseMethodDesc("(L${"java/lang/String"};I)V") shouldBe intArrayOf(19, 20)
            }

            assertIAE("L${"java/lang/String"};", DescriptorSignatures::parseMethodDesc)
            assertIAE("(L${"java/lang/String"};", DescriptorSignatures::parseMethodDesc)
            assertIAE("(L${"java/lang/String"};)", DescriptorSignatures::parseMethodDesc)
            assertIAE("(L${"java"}", DescriptorSignatures::parseMethodDesc)
            assertIAE("(L${"java/lang/String"};)L", DescriptorSignatures::parseMethodDesc)
            assertIAE("(L${"java/lang/String"};)V traling", DescriptorSignatures::parseMethodDesc)
        }

        describe("parsing type descriptor") {
            assertSuccessful("V") { DescriptorSignatures.parseTypeDesc(it, TypeKind.Voidable) }
            assertSuccessful("I") { DescriptorSignatures.parseTypeDesc(it, TypeKind.Primitive) }
            assertSuccessful("L${"java/lang/String"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertSuccessful("[L${"java/lang/String"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }

            assertIAE("V") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"java/lang/String"}") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("[[") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${""};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"java/"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"/java/lang"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"java//lang"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"<"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${">"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"["};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"."};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${":"};") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
            assertIAE("L${"java/lang/String"}; traling") { DescriptorSignatures.parseTypeDesc(it, TypeKind.RefOnly) }
        }
    }

// TODO: rewrite test
/*
    @Test
    fun parseMethodSignature() {
        DescriptorSignatures.parseMethodSignature("()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), emptyList(),
                TypeSignatureIndices(2..2), emptyList()))
        DescriptorSignatures.parseMethodSignature("(I)V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), listOf(
                    TypeSignatureIndices(1..1),
                ),
                TypeSignatureIndices(3..3), emptyList()))
        DescriptorSignatures.parseMethodSignature("(L${"java/lang/String"};)V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), listOf(
                    TypeSignatureIndices(1..17),
                ),
                TypeSignatureIndices(20..20), emptyList()))
        DescriptorSignatures.parseMethodSignature("(L${"java/lang/String"};I)V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), listOf(
                    TypeSignatureIndices(1..17),
                    TypeSignatureIndices(19..19),
                ),
                TypeSignatureIndices(21..21), emptyList()))

        // signature
        DescriptorSignatures.parseMethodSignature("<Index:>()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                listOf(
                    TypeParameterIndices(1..5, 
                        TypeSignatureIndices(6..6)
                    ),
                ), emptyList(),
                TypeSignatureIndices(10..10), emptyList()))
        DescriptorSignatures.parseMethodSignature("<Index:Ljava/lang/Object;>()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                listOf(
                    TypeParameterIndices(1..5, 
                        TypeSignatureIndices(6..23),
                    ),
                ), emptyList(),
                TypeSignatureIndices(28..28), emptyList()))
        DescriptorSignatures.parseMethodSignature("<Index::Ljava/util/List<TIndex;>;>()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                listOf(
                    TypeParameterIndices(1..5,
                        TypeSignatureIndices(6..6),
                        TypeSignatureIndices(TypeSignatureIndicesElement(7..22,
                            TypeSignatureIndices(24..30)
                        )),
                    ),
                ), emptyList(),
                TypeSignatureIndices(36..36), emptyList()))

        fun assertIAE(desc: String) = assertThrows<IllegalArgumentException> {
            DescriptorSignatures.parseMethodSignature(desc)
        }

        assertIAE("<")
        assertIAE("<Name")
        assertIAE("<Name:")
        assertIAE("<:>")
        assertIAE("<Name:>")
        assertIAE("L${"java/lang/String"};")
        assertIAE("(L${"java/lang/String"};")
        assertIAE("(L${"java/lang/String"};)")
        assertIAE("(L${"java"}")
        assertIAE("(L${"java/lang/String"};)L")
        assertIAE("(L${"java/lang/String"};)V traling")
    }

    @Test
    fun parseTypeSignature() {
        fun testSignature(signature: String, type: TypeKind, expected: TypeSignatureIndices) {
            val actual = DescriptorSignatures.parseTypeSignature(signature, type)
            actual shouldBeEqualTo expected
        }

        // descriptors
        testSignature("V", TypeKind.Voidable,
            TypeSignatureIndices(0..0))
        testSignature("I", TypeKind.Primitive,
            TypeSignatureIndices(0..0))
        testSignature("L${"java/lang/String"};", TypeKind.RefOnly,
            TypeSignatureIndices(0..16))
        testSignature("[L${"java/lang/String"};", TypeKind.RefOnly,
            TypeSignatureIndices(0..17))
        // signatures
        testSignature("[L${"java/util/List<L${"java/lang/String"};>"};", TypeKind.RefOnly,
            TypeSignatureIndices(TypeSignatureIndicesElement(0..15, TypeSignatureIndices(17..33))))
        testSignature("[L${"java/util/List<T${"Hello"};>"};", TypeKind.RefOnly,
            TypeSignatureIndices(TypeSignatureIndicesElement(0..15, TypeSignatureIndices(17..23))))
        testSignature("[L${"java/util/List<T${"Hello"};>"}.Hello;", TypeKind.RefOnly,
            TypeSignatureIndices(
                TypeSignatureIndicesElement(0..15, TypeSignatureIndices(17..23)),
                TypeSignatureIndicesElement(26..30),
            ))

        testSignature("[L${"java/util/List<+L${"java/lang/String"};>"};", TypeKind.RefOnly,
            TypeSignatureIndices(TypeSignatureIndicesElement(0..15, TypeSignatureIndices(17..34))))

        fun assertIAE(desc: String) = assertThrows<IllegalArgumentException> {
            DescriptorSignatures.parseTypeSignature(desc, TypeKind.RefOnly)
        }

        // descriptors
        assertIAE("V")
        assertIAE("I")
        assertIAE("L${"java/lang/String"}")
        assertIAE("[[")
        assertIAE("L${""};")
        assertIAE("L${"java/"};")
        assertIAE("L${"/java/lang"};")
        assertIAE("L${"java//lang"};")
        assertIAE("L${"<"};")
        assertIAE("L${">"};")
        assertIAE("L${"["};")
        assertIAE("L${"."};")
        assertIAE("L${":"};")
        // signatures
        assertIAE("L${"java/lang/String"}<")
        assertIAE("L${"java/lang/String"}<<")
        assertIAE("L${"java/lang/String"}<>")
        assertIAE("L${"java/lang/String"}<>;")
        assertIAE("L${"java/lang/String"}<L")
        assertIAE("L${"java/lang/String"}<L${"java/lang/String"};")
        assertIAE("L${"java/lang/String"}<L${"java/lang/String"};>")
        assertIAE("L${"java/lang/String"}<L${"java/lang/String"};>a")
        assertIAE("L${"java/lang/String"}<L${"java/lang/String"};>.")

        assertIAE("T${"Hello"}<L${"java/lang/String"};>;")
        assertIAE("L${"java/lang/String"}; traling")
    }
*/
}
