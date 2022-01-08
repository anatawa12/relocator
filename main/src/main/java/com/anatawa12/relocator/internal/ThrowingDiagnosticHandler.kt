package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagnostic.Diagnostic
import com.anatawa12.relocator.diagnostic.DiagnosticException
import com.anatawa12.relocator.diagnostic.DiagnosticHandler

internal object ThrowingDiagnosticHandler : DiagnosticHandler {
    override fun handle(diagnostic: Diagnostic) {
        throw DiagnosticException(diagnostic)
    }
}
