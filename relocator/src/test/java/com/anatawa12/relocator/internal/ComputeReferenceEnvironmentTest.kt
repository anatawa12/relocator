package com.anatawa12.relocator.internal

import java.lang.RuntimeException

internal class TestingComputeReferenceEnvironment(
    keepRuntimeInvisibleAnnotation: Boolean = true,
) : ComputeReferenceEnvironment(
    keepRuntimeInvisibleAnnotation,
    CombinedClassPath(emptyList()),
) {
    override fun addDiagnostic(diagnostic: Diagnostic) {
        throw DiagnosticException(diagnostic)
    }
}

class DiagnosticException(diagnostic: Diagnostic) : RuntimeException(
    "diagnostic: $diagnostic"
)
