package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.reference.ClassReference
import org.amshove.kluent.*
import org.junit.jupiter.api.Test

internal class ClassRefCollectingAnnotationVisitorTest {
    fun test(annotation: ClassAnnotation): Set<ClassReference> {
        val env = newComputeReferenceEnvironment()
        val collection = hashSetOf<ClassReference>()
        ClassRefCollectingAnnotationVisitor.acceptAnnotation(
            collection, env, annotation)
        return collection
    }

    private fun newClassAnnotation(internalName: String, vararg values: Pair<String, AnnotationValue>) =
        ClassAnnotation(ClassReference(internalName), values.map { (k, v) -> KeyValuePair(k, v) })

    @Test
    fun noValueAnnotation() {
        test(newClassAnnotation("java/lang/annotation/Documented"))
            .`should be equal to`(setOf(
                ClassReference("java/lang/annotation/Documented"),
            ))
    }

    @Test
    fun enumValueAnnotation() {
        test(newClassAnnotation("java/lang/annotation/Retention",
            "value" to AnnotationEnum(ClassReference("java/lang/annotation/RetentionPolicy"), "CLASS")))
            .`should be equal to`(setOf(
                ClassReference("java/lang/annotation/Retention"),
                ClassReference("java/lang/annotation/RetentionPolicy"),
            ))
        test(newClassAnnotation("java/lang/annotation/Target",
            "value" to AnnotationArray(
                AnnotationEnum(ClassReference("java/lang/annotation/ElementType"), "TYPE")
            )))
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
        test(newClassAnnotation("com/anatawa12/relocator/TestAnnotation",
            "value" to AnnotationClass("Ljava/lang/String;")))
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/TestAnnotation"),
                ClassReference("java/lang/String"),
            ))
        test(newClassAnnotation("com/anatawa12/relocator/TestAnnotation",
            "values" to AnnotationArray(
                AnnotationClass("Ljava/lang/String;"),
                AnnotationClass("[Ljava/lang/annotation/ElementType;"),
            )))
            .`should be equal to`(setOf(
                ClassReference("com/anatawa12/relocator/TestAnnotation"),
                ClassReference("java/lang/String"),
                ClassReference("java/lang/annotation/ElementType"),
            ))
    }
}
