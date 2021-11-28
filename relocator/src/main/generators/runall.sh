#!/bin/sh

run_script() {
  kotlinc -script "$1" > "../java/com/anatawa12/relocator/$2"
}

run_script "DiagnosticContainer.generator.kts" "diagostic/DiagnosticContainer.kt"
run_script "DiagnosticType.generator.kts" "diagostic/DiagnosticType.kt"
