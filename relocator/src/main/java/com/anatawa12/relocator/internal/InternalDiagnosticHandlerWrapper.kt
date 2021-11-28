package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagnostic.*
import kotlinx.atomicfu.atomic

/**
 * The DiagnosticHandler to
 * - count diagnostics by type.
 * - implement suppression TODO: implement
 */
internal class InternalDiagnosticHandlerWrapper(
    val handler: DiagnosticHandler,
) : DiagnosticHandler {
    private val _errorCount = atomic(0)
    val errorCount get()= _errorCount.value
    private val _warningCount = atomic(0)
    val warningCount get()= _warningCount.value

    override fun handle(diagnostic: Diagnostic) {
        when (diagnostic.type.kind) {
            DiagnosticKind.Error -> _errorCount.incrementAndGet()
            DiagnosticKind.Warning -> _warningCount.incrementAndGet()
        }
        handler.handle(diagnostic)
    }
}