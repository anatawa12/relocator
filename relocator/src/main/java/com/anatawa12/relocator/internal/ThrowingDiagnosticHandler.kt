package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagostic.Diagnostic
import com.anatawa12.relocator.diagostic.DiagnosticException
import com.anatawa12.relocator.diagostic.DiagnosticHandler

internal object ThrowingDiagnosticHandler : DiagnosticHandler {
    override fun handle(diagnostic: Diagnostic) {
        throw DiagnosticException(diagnostic)
    }
}
