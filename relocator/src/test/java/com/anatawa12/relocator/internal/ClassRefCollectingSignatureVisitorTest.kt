package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagostic.Location
import com.anatawa12.relocator.reference.ClassReference
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.tree.InnerClassNode

internal class ClassRefCollectingSignatureVisitorTest {
    fun test(signature: String?, vararg innerClasses: InnerClassNode): Set<ClassReference> {
        val container = InnerClassContainer(innerClasses.toList())
        val env = TestingComputeReferenceEnvironment()
        val collection = hashSetOf<ClassReference>()
        ClassRefCollectingSignatureVisitor.acceptSignature(collection, env, container, signature, Location.Class(""))
        return collection
    }

    @Test
    fun nullSignature() {
        test(null) `should be equal to` setOf()
    }

    // signatures without generic inner classes
    @Test
    fun simpleSignature() {
        // type parameters
        test("<A:>()V") `should be equal to` setOf()
        test("<A:Ljava/lang/Object;>()V")
            .`should be equal to`(setOf(
                ClassReference("java/lang/Object"),
            ))
        // type arguments
        test("Ljava/util/Map\$Entry<Ljava/lang/String;Ljava/lang/String;>;")
            .`should be equal to`(setOf(
                ClassReference("java/util/Map\$Entry"),
                ClassReference("java/lang/String"),
            ))
    }

    // signatures without generic inner classes
    @Test
    fun genericInnerSignature() {
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
        val inner = InnerClassNode("com/anatawa12/relocator/Test\$Inner",
            "com/anatawa12/relocator/Test","Inner", 0x0)
        //val child = InnerClassNode("com/anatawa12/relocator/Test\$Child",
        //    "com/anatawa12/relocator/Test","Child", 0x8)
        val childInner = InnerClassNode("com/anatawa12/relocator/Test\$Child\$Inner", 
            "com/anatawa12/relocator/Test\$Child","Inner", 0x0)
        val innnerInner2 = InnerClassNode("com/anatawa12/relocator/Test\$Inner\$Inner2", 
            "com/anatawa12/relocator/Test\$Inner","Inner2", 0x0)

        test("Lcom/anatawa12/relocator/Test<Ljava/lang/String;>.Inner;",
            inner)
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/Test\$Inner"),
                ClassReference("java/lang/String"),
            ))
        test("Lcom/anatawa12/relocator/Test\$Child<Ljava/lang/String;>.Inner;",
            childInner)
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/Test\$Child\$Inner"),
                ClassReference("java/lang/String"),
            ))
        test("Lcom/anatawa12/relocator/Test<Ljava/lang/String;>.Inner.Inner2;",
            inner, innnerInner2)
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/Test\$Inner\$Inner2"),
                ClassReference("java/lang/String"),
            ))
    }
}
