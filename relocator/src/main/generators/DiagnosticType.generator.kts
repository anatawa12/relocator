println("// this file is generated by 'DiagnosticType.generator.kts'")
println("package com.anatawa12.relocator.diagnostic")
println("")
println("sealed class BasicDiagnosticType constructor(val id: String, builder: DiagnosticBuilder<*>) {")
println("    val kind: DiagnosticKind = builder.kind")
println("    protected val render: Any? = builder.render")
println("")
println("    abstract fun render(values: Array<Any?>): String")
println("}")
println("")
println("class DiagnosticType0(")
println("    name: String,")
println("    builder: DiagnosticBuilder<*>,")
println("): BasicDiagnosticType(name, builder) {")
println("    @JvmName(\"create\")")
println("    operator fun invoke(location: Location) = Diagnostic(this, location, arrayOf())")
println("    override fun render(values: Array<Any?>): String = render as String")
println("}")
println("")
println("// begin loop generated code")

fun generate(count: Int) {
    val range = 0 until count
    val types = range.joinToString(", ") { "T$it" }

    println("class DiagnosticType$count<$types>(")
    println("    name: String,")
    println("    builder: DiagnosticBuilder<DiagnosticType$count<$types>>,")
    println("): BasicDiagnosticType(name, builder) {")
    println("    @JvmName(\"create\")")
    println("    operator fun invoke(")
    for (i in range)
        println("        value$i: T$i,")
    println("        location: Location,")
    println("    ) = Diagnostic(this, location, arrayOf(")
    for (i in range)
        println("        value$i,")
    println("    ))")
    println("")
    println("    @Suppress(\"UNCHECKED_CAST\")")
    println("    override fun render(values: Array<Any?>): String = (render as ($types) -> String)(")
    for (i in range)
        println("        values[$i] as T$i,")
    println("    )")
    println("}")
    println("")
}

for (i in 1..4) {
    generate(i)
}

println("// end loop generated code")
println("")
