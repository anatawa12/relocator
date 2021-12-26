package com.anatawa12.relocator.internal

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

internal class TypeSignatureIndicesTest {
    private class Context(val signature: String) {
        val indices = DescriptorParser.parseTypeSignature(signature, TypeKind.Voidable)
        fun getTypeRange(): IntRange? = indices.getTypeRange(signature)
        fun TypeSignatureIndices.getTypeRange(): IntRange? = getTypeRange(signature)
    }
    private inline fun test(signature: String, block: Context.() -> Unit) =
        Context(signature).block()

    @Test
    fun getTypeRange() {
        test("I") {
            getTypeRange().shouldBeEqualTo(signature.indices)
        }
        test("V") {
            getTypeRange().shouldBeEqualTo(signature.indices)
        }
        test("TName;") {
            getTypeRange().shouldBeEqualTo(signature.indices)
        }
        test("L${"java/lang/String"};") {
            getTypeRange().shouldBeEqualTo(signature.indices)
        }
        test("L${"java/util/List"};") {
            getTypeRange().shouldBeEqualTo(signature.indices)
        }

        test("L${"java/util/List"}<TName;>;") {
            getTypeRange().shouldBeEqualTo(signature.indices)
            indices.value[0].args[0].getTypeRange().shouldBeEqualTo(16..21)
        }

        test("L${"java/util/List"}<L${"java/util/List"}<TName;>;>;") {
            getTypeRange().shouldBeEqualTo(signature.indices)
            indices.value[0].args[0].getTypeRange().shouldBeEqualTo(16..39)
            indices.value[0].args[0].value[0].args[0].getTypeRange().shouldBeEqualTo(32..37)
        }
    }
}
