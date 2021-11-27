package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.CombinedClassPath

internal fun newComputeReferenceEnvironment(
    keepRuntimeInvisibleAnnotation: Boolean = true,
) = ComputeReferenceEnvironment(
    keepRuntimeInvisibleAnnotation,
    CombinedClassPath(emptyList()),
    ThrowingDiagnosticHandler,
)
