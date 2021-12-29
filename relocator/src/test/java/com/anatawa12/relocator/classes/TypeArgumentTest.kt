package com.anatawa12.relocator.classes

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec

internal class TypeArgumentTest : DescribeSpec() {
    init {
        val string = TypeSignature.classOf("java/lang/String")

        it("instantiate") {
            TypeArgument.of(string, TypeVariant.Covariant)
            TypeArgument.of(TypeSignature.parse("TT;"), TypeVariant.Covariant)
        }
        describe("falling builders") {
            it("primitive type") {
                shouldThrow<IllegalArgumentException> {
                    TypeArgument.of(TypeSignature.INT, TypeVariant.Covariant)
                }
            }
            it("void type") {
                shouldThrow<IllegalArgumentException> {
                    TypeArgument.of(TypeSignature.VOID, TypeVariant.Covariant)
                }
            }
        }
    }
} 
