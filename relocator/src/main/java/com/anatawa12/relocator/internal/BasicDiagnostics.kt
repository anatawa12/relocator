package com.anatawa12.relocator.internal

import com.anatawa12.relocator.diagnostic.DiagnosticContainer

object BasicDiagnostics : DiagnosticContainer() {
    val UNRESOLVABLE_INNER_CLASS = warning("UNRESOLVABLE_INNER_CLASS", String, String) { outer, inner ->
        "the internal name of '$outer.$inner' not found."
    }
    val UNRESOLVABLE_REFLECTION_CLASS = warning("UNRESOLVABLE_REFLECTION_CLASS", "Unresolvable reflection call for class found.")
    val UNRESOLVABLE_REFLECTION_FIELD = warning("UNRESOLVABLE_REFLECTION_FIELD", "Unresolvable reflection call for field found.")
    val UNRESOLVABLE_REFLECTION_METHOD = warning("UNRESOLVABLE_REFLECTION_METHOD", "Unresolvable reflection call for method found.")

    val UNRESOLVABLE_CLASS = error("UNRESOLVABLE_CLASS", String) { name -> "the class '$name' not found" }
    val UNRESOLVABLE_FIELD = error("UNRESOLVABLE_FIELD", String, String, String.optional()) { owner, name, desc ->
        "the field '$owner.$name${if (desc == null) "" else ":$desc"}' not found"
    }
    val UNRESOLVABLE_METHOD = error("UNRESOLVABLE_METHOD", String, String, String.optional()) { owner, name, desc ->
        "the method '$owner.$name${if (desc == null) "" else ":$desc"}' not found"
    }
}
