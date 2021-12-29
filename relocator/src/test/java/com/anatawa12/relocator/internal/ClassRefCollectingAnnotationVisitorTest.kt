package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.reference.ClassReference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

internal class ClassRefCollectingAnnotationVisitorTest : DescribeSpec() {
    private fun test(annotation: ClassAnnotation): Set<ClassReference> {
        val env = newComputeReferenceEnvironment()
        val collection = hashSetOf<ClassReference>()
        ClassRefCollectingAnnotationVisitor.acceptAnnotation(
            collection, env, annotation)
        return collection
    }

    private fun newClassAnnotation(internalName: String, vararg values: Pair<String, AnnotationValue>) =
        ClassAnnotation(ClassReference(internalName), values.map { (k, v) -> KeyValuePair(k, v) })

    init {
        it ("marker annotation") {
            test(newClassAnnotation("java/lang/annotation/Documented"))
                .shouldBe(setOf(
                    ClassReference("java/lang/annotation/Documented"),
                ))
        }

        describe ("annotation with enum value") {
            it ("single value") {
                test(newClassAnnotation("java/lang/annotation/Retention",
                    "value" to AnnotationEnum(ClassReference("java/lang/annotation/RetentionPolicy"), "CLASS")))
                    .shouldBe(setOf(
                        ClassReference("java/lang/annotation/Retention"),
                        ClassReference("java/lang/annotation/RetentionPolicy"),
                    ))
            }
            it ("in array") {
                test(newClassAnnotation("java/lang/annotation/Target",
                    "value" to AnnotationArray(
                        AnnotationEnum(ClassReference("java/lang/annotation/ElementType"), "TYPE")
                    )))
                    .shouldBe(setOf(
                        ClassReference("java/lang/annotation/Target"),
                        ClassReference("java/lang/annotation/ElementType"),
                    ))
            }
        }

        describe("annotation with ckass value") {
            /*
            package com.anatawa12.relocator;
            @interface TestAnnotation {
                 Class<?> value() default Object.class;
                 Class<?>[] values() default {};
            }
             */
            it ("single value") {
                test(newClassAnnotation("com/anatawa12/relocator/TestAnnotation",
                    "value" to AnnotationClass("Ljava/lang/String;")))
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/TestAnnotation"),
                        ClassReference("java/lang/String"),
                    ))
            }
            it ("in array") {
                test(newClassAnnotation("com/anatawa12/relocator/TestAnnotation",
                    "values" to AnnotationArray(
                        AnnotationClass("Ljava/lang/String;"),
                        AnnotationClass("[Ljava/lang/annotation/ElementType;"),
                    )))
                    .shouldBe(setOf(
                        ClassReference("com/anatawa12/relocator/TestAnnotation"),
                        ClassReference("java/lang/String"),
                        ClassReference("java/lang/annotation/ElementType"),
                    ))
            }
        }
    }
}
