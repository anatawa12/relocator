println("// this file is generated by 'DiagnosticContainer.generator.kts'")
println("package com.anatawa12.relocator.diagnostic")
println("")
println("@Suppress(\"UNCHECKED_CAST\")")
println("open class DiagnosticContainer {")
println("    private val diagnostics = hashMapOf<String, BasicDiagnosticType>()")
println("")
/*
// ABI for Kotlin
println("    protected operator fun <T : BasicDiagnosticType> DiagnosticBuilder<T>.provideDelegate(thisRef: DiagnosticContainer, prop: KProperty<*>) =")
println("        create(prop.name).apply(::add)")
println("")
println("    protected operator fun <T : BasicDiagnosticType> T.getValue(thisRef: DiagnosticContainer, prop: KProperty<*>) = this")
println("")
 */
println("    protected fun add(diagnosticType: BasicDiagnosticType) {")
println("        if (diagnostics.putIfAbsent(diagnosticType.id, diagnosticType) != null)")
println("            throw IllegalArgumentException(\"Diagnostic with id '\${diagnosticType.id}' already exists\")")
println("    }")
println("")

/*
// ABI for Kotlin
println("    fun warning(message: String) = DiagnosticBuilder(DiagnosticKind.Warning, message, ::DiagnosticType0)")
println("    fun error(message: String) = DiagnosticBuilder(DiagnosticKind.Error, message, ::DiagnosticType0)")
 */
println("    fun warning(id: String, message: String) = DiagnosticType0.new(id, DiagnosticKind.Warning, message).apply(::add)")
println("    fun error(id: String, message: String) = DiagnosticType0.new(id, DiagnosticKind.Error, message).apply(::add)")
println("")
println("    // begin loop generated code")

/*
// ABI for Kotlin. should be separated module
for (count in 1..4) {
    val range = 0 until count
    val types = range.joinToString(", ") { "T$it" }
    println("    fun <$types> warning(")
    for (i in range)
        println("        @Suppress(\"UNUSED_PARAMETER\") type$i: DiagnosticValueType<T$i>,")
    println("        message: ($types) -> String,")
    println("    ) = DiagnosticBuilder<DiagnosticType$count<$types>>(DiagnosticKind.Warning, message, ::DiagnosticType$count)")
    println("")
    println("    fun <$types> error(")
    for (i in range)
        println("        @Suppress(\"UNUSED_PARAMETER\") type$i: DiagnosticValueType<T$i>,")
    println("        message: ($types) -> String,")
    println("    ) = DiagnosticBuilder<DiagnosticType$count<$types>>(DiagnosticKind.Error, message, ::DiagnosticType$count)")
    println()
}
// */

for (count in 1..4) {
    val range = 0 until count
    val types = range.joinToString(", ") { "T$it" }
    println("    fun <$types> warning(")
    println("        id: String,")
    for (i in range)
        println("        @Suppress(\"UNUSED_PARAMETER\") type$i: DiagnosticValueType<T$i>,")
    println("        message: MessageBuilder$count<$types>,")
    println("    ) = DiagnosticType$count.new(id, DiagnosticKind.Warning, message).apply(::add)")
    println("")
    println("    fun <$types> error(")
    println("        id: String,")
    for (i in range)
        println("        @Suppress(\"UNUSED_PARAMETER\") type$i: DiagnosticValueType<T$i>,")
    println("        message: MessageBuilder$count<$types>,")
    println("    ) = DiagnosticType$count.new(id, DiagnosticKind.Error, message).apply(::add)")
    println()
}

println("    // end loop generated code")
println("    companion object {")
println("        @JvmStatic val String = DiagnosticValueType.String")
println("        @JvmStatic val Int = DiagnosticValueType.Int")
println("    }")
println("}")
println("")

for (count in 1..4) {
    val range = 0 until count
    val types = range.joinToString(", ") { "T$it" }
    println("fun interface MessageBuilder$count<$types>{")
    println("    fun build(")
    for (i in range)
        println("        type$i: T$i,")
    println("    ): String")
    println("}")
    println()
}