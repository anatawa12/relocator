package com.anatawa12.relocator.internal

sealed class Diagnostic {
    var inClass: String? = null
    override fun toString(): String {
        val inClass = inClass
        return if (inClass != null) {
            "${message()}: in $inClass"
        } else {
            "${message()}: in unknown class"
        }
    }
    abstract fun message(): String
}

abstract class Warning : Diagnostic()
