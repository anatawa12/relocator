package com.anatawa12.relocator.diagnostic

fun interface DiagnosticHandler {
    fun handle(diagnostic: Diagnostic)
}

class DiagnosticException(val diagnostic: Diagnostic) : RuntimeException(diagnostic.toString())

class ErrorFoundException() : RuntimeException("Error during relocation. see log for more details")
