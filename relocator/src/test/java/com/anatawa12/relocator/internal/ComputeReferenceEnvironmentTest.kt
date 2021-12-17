package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.CombinedClassPath
import com.anatawa12.relocator.reflect.ReflectionMappingContainer

internal fun newComputeReferenceEnvironment(
    keepRuntimeInvisibleAnnotation: Boolean = true,
) = ComputeReferenceEnvironment(
    keepRuntimeInvisibleAnnotation,
    ReflectionMappingContainer(),
    CombinedClassPath(emptyList()),
    ThrowingDiagnosticHandler,
)
