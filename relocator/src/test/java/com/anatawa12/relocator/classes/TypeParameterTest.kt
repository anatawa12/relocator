package com.anatawa12.relocator.classes

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import java.lang.IllegalStateException

internal class TypeParameterTest : DescribeSpec() {
    init {
        val string = TypeSignature.parse("L${"java/lang/String"};")
        val list = TypeSignature.parse("L${"java/util/List"};")
        describe("instantiate") {
            TypeParameter.of("T", string)
            TypeParameter.Builder("T").classBound(string).build()
            TypeParameter.Builder("T").addInterfaceBound(list).build()
        }
        describe("bad instantiate") {
            it("of: empty name") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.of("", string)
                }
            }
            it("of: invalid char on name") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.of("/", string)
                }
            }
            it("of: primitive on class bound") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.of("T", TypeSignature.INT)
                }
            }

            it("builder: empty name") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.Builder("").classBound(string).build()
                } 
            }
            it("builder: invalid char on name") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.Builder("/").classBound(string).build()
                }
            }
            it("builder: primitive on class bound") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.Builder("T").classBound(TypeSignature.INT).build()
                }
            }
            it("builder: primitive on interface bound") {
                shouldThrow<IllegalArgumentException> {
                    TypeParameter.Builder("T").addInterfaceBound(TypeSignature.INT).build()
                }
            }
            it("builder: no bounds") {
                shouldThrow<IllegalStateException> {
                    TypeParameter.Builder("T").build()
                }
            }
            it("builder: modify on built") {
                shouldThrow<IllegalStateException> {
                    TypeParameter.Builder("T")
                        .classBound(string)
                        .apply { build() }
                        .addInterfaceBound(list)
                }
            }
        }
    }
} 
