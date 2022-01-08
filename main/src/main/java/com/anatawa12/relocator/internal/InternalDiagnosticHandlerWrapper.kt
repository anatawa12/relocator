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
    val suppressions: SuppressionContainer,
) : DiagnosticHandler {
    private val _errorCount = atomic(0)
    val errorCount get() = _errorCount.value
    private val _warningCount = atomic(0)
    val warningCount get() = _warningCount.value

    override fun handle(diagnostic: Diagnostic) {
        if (isSuppressed(diagnostic)) return
        when (diagnostic.type.kind) {
            DiagnosticKind.Error -> _errorCount.incrementAndGet()
            DiagnosticKind.Warning -> _warningCount.incrementAndGet()
        }
        handler.handle(diagnostic)
    }

    private fun isSuppressed(diagnostic: Diagnostic): Boolean =
        suppressions.getDiagnosticList(diagnostic.location, diagnostic.type.id).any { suppressingDiagnostic ->
            diagnostic.parameters.asSequence().zip(suppressingDiagnostic.values.asSequence(), ::match).all { it }
        }

    private fun match(a: Any?, b: SuppressingValue<*>): Boolean = when (b) {
        SuppressingValue.Any -> true
        is SuppressingValue.IntRange -> a is Int && a in b.min..b.max
        is SuppressingValue.IntValue -> a is Int && a == b.value
        is SuppressingValue.StringPattern -> a is String && b.pattern.toRegex().matches(a)
        is SuppressingValue.StringValue -> a is String && b.value == a
    }
}
