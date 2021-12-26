package com.anatawa12.relocator.internal

import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DescriptorParserTest {
    @Test
    fun parseMethodDesc() {
        DescriptorParser.parseMethodDesc("()V")
            .shouldBeEqualTo(intArrayOf())
        DescriptorParser.parseMethodDesc("(I)V")
            .shouldBeEqualTo(intArrayOf(2))
        DescriptorParser.parseMethodDesc("(L${"java/lang/String"};)V")
            .shouldBeEqualTo(intArrayOf(19))
        DescriptorParser.parseMethodDesc("(L${"java/lang/String"};I)V")
            .shouldBeEqualTo(intArrayOf(19, 20))

        fun assertIAE(desc: String) = assertThrows<IllegalArgumentException> {
            DescriptorParser.parseMethodDesc(desc)
        }

        assertIAE("L${"java/lang/String"};")
        assertIAE("(L${"java/lang/String"};")
        assertIAE("(L${"java/lang/String"};)")
        assertIAE("(L${"java"}")
        assertIAE("(L${"java/lang/String"};)L")
        assertIAE("(L${"java/lang/String"};)V traling")
    }

    @Test
    fun parseTypeDesc() {
        DescriptorParser.parseTypeDesc("V", TypeKind.Voidable)
        DescriptorParser.parseTypeDesc("I", TypeKind.Primitive)
        DescriptorParser.parseTypeDesc("L${"java/lang/String"};", TypeKind.RefOnly)
        DescriptorParser.parseTypeDesc("[L${"java/lang/String"};", TypeKind.RefOnly)

        fun assertIAE(desc: String) = assertThrows<IllegalArgumentException> {
            DescriptorParser.parseTypeDesc(desc, TypeKind.RefOnly)
        }

        assertIAE("V")
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
        assertIAE("L${"java/lang/String"}; traling")
    }

    @Test
    fun parseMethodSignature() {
        DescriptorParser.parseMethodSignature("()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), emptyList(),
                TypeSignatureIndices(2..2), emptyList()))
        DescriptorParser.parseMethodSignature("(I)V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), listOf(
                    TypeSignatureIndices(1..1),
                ),
                TypeSignatureIndices(3..3), emptyList()))
        DescriptorParser.parseMethodSignature("(L${"java/lang/String"};)V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), listOf(
                    TypeSignatureIndices(1..17),
                ),
                TypeSignatureIndices(20..20), emptyList()))
        DescriptorParser.parseMethodSignature("(L${"java/lang/String"};I)V")
            .shouldBeEqualTo(MethodSignatureIndices(
                emptyList(), listOf(
                    TypeSignatureIndices(1..17),
                    TypeSignatureIndices(19..19),
                ),
                TypeSignatureIndices(21..21), emptyList()))

        // signature
        DescriptorParser.parseMethodSignature("<Index:>()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                listOf(
                    TypeParameterIndices(1..5, 
                        TypeSignatureIndices(6..6)
                    ),
                ), emptyList(),
                TypeSignatureIndices(10..10), emptyList()))
        DescriptorParser.parseMethodSignature("<Index:Ljava/lang/Object;>()V")
            .shouldBeEqualTo(MethodSignatureIndices(
                listOf(
                    TypeParameterIndices(1..5, 
                        TypeSignatureIndices(6..23),
                    ),
                ), emptyList(),
                TypeSignatureIndices(28..28), emptyList()))
        DescriptorParser.parseMethodSignature("<Index::Ljava/util/List<TIndex;>;>()V")
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
            DescriptorParser.parseMethodSignature(desc)
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
            val actual = DescriptorParser.parseTypeSignature(signature, type)
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
            DescriptorParser.parseTypeSignature(desc, TypeKind.RefOnly)
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
        // */
    }
}
