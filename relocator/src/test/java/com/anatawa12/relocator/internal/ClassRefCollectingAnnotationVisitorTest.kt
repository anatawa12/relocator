package com.anatawa12.relocator.internal

import com.anatawa12.relocator.reference.ClassReference
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode

internal class ClassRefCollectingAnnotationVisitorTest {
    fun test(annotation: AnnotationNode): Set<ClassReference> {
        val env = TestingComputeReferenceEnvironment()
        val collection = hashSetOf<ClassReference>()
        ClassRefCollectingAnnotationVisitor.acceptAnnotation(
            ClassRefCollectingAnnotationVisitor(collection, env), annotation)
        return collection
    }

    @Test
    fun noValueAnnotation() {
        test(AnnotationNode("Ljava/lang/annotation/Documented;"))
            .`should be equal to`(setOf(
                ClassReference("java/lang/annotation/Documented"),
            ))
    }

    @Test
    fun enumValueAnnotation() {
        test(AnnotationNode("Ljava/lang/annotation/Retention;").apply {
            visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "CLASS")
        })
            .`should be equal to`(setOf(
                ClassReference("java/lang/annotation/Retention"),
                ClassReference("java/lang/annotation/RetentionPolicy"),
            ))
        test(AnnotationNode("Ljava/lang/annotation/Target;").apply {
            visitArray("value").apply {
                visitEnum(null, "Ljava/lang/annotation/ElementType;", "TYPE")
            }
        })
            .`should be equal to`(setOf(
                ClassReference("java/lang/annotation/Target"),
                ClassReference("java/lang/annotation/ElementType"),
            ))
    }

    @Test
    fun classValueAnnotation() {
        /*
        package com.anatawa12.relocator;
        @interface TestAnnotation {
             Class<?> value() default Object.class;
             Class<?>[] values() default {};
        }
         */
        test(AnnotationNode("Lcom/anatawa12/relocator/TestAnnotation;").apply {
            visit("value", Type.getType("Ljava/lang/String;"))
        })
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/TestAnnotation"),
                ClassReference("java/lang/String"),
            ))
        test(AnnotationNode("Lcom/anatawa12/relocator/TestAnnotation;").apply {
            visitArray("values").apply {
                visit(null, Type.getType("Ljava/lang/String;"))
                visit(null, Type.getType("[Ljava/lang/annotation/ElementType;"))
            }
        })
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/TestAnnotation"),
                ClassReference("java/lang/String"),
                ClassReference("java/lang/annotation/ElementType"),
            ))
    }
}
