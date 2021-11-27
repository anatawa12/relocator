package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagostic.*
import kotlinx.atomicfu.atomic

/**
 * The DiagnosticHandler to
 * - count diagnostics by type.
 * - implement suppression TODO: implement
 */
internal class InternalDiagnosticHandlerWrapper(
    val handler: DiagnosticHandler,
) : DiagnosticHandler {
    private val errorCount = atomic(0)
    private val warningCount = atomic(0)

    override fun handle(diagnostic: Diagnostic) {
        when (diagnostic) {
            is Error -> errorCount.incrementAndGet()
            is Warning -> warningCount.incrementAndGet()
        }
        handler.handle(diagnostic)
    }
}
