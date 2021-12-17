package com.anatawa12.relocator.diagnostic

class Diagnostic internal constructor(
    val type: BasicDiagnosticType,
    val location: Location,
    val parameters: Array<Any?>,
) {
    override fun toString(): String {
        val inClass = location
        return "${message()} $inClass"
    }

    fun message(): String = type.render(parameters)
}

enum class DiagnosticKind {
    Warning,
    Error,
}

sealed class DiagnosticValueType<T> {
    object String : DiagnosticValueType<kotlin.String>()
    object Int : DiagnosticValueType<kotlin.Int>()

    fun optional(): Optional<T> = Optional(this)

    class Optional<T> internal constructor(val inner: DiagnosticValueType<T>) : DiagnosticValueType<T?>() {
        override fun equals(other: Any?): Boolean {
            if (other === null) return false
            if (this === other) return true
            if (other is Optional<*> && mostInner() == other.mostInner())
                return true
            return false
        }

        override fun hashCode(): kotlin.Int = mostInner().hashCode() + 1

        private fun mostInner(): DiagnosticValueType<*> {
            var cur = this as DiagnosticValueType<*>
            while (cur is Optional<*>) cur = cur.inner
            return cur
        }
    }
}

/*
// ABI for Kotlin. should be separated module
class DiagnosticBuilder<T> internal constructor(
    internal val kind: DiagnosticKind,
    internal val render: Any?,
    internal val factory: (String, DiagnosticBuilder<T>) -> T,
) {
    internal fun create(name: String) = factory(name, this)
}
 */
