package com.anatawa12.relocator

import io.kotest.assertions.fail

/**
 * The test to check all ABI doesn't use external classes
 */
class AllAPIDoesNotUseExternalClass : PublicABITest(true) {
    override fun checkClass(clazz: Class<*>, location: Location) {
        @Suppress("NAME_SHADOWING") var clazz = clazz
        while (clazz.isArray) clazz = clazz.componentType

        if (clazz.name.startsWith("com.anatawa12.relocator.internal."))
            fail("uses internal at $location")

        if (clazz.isPrimitive) return
        if (clazz == Metadata::class.java) return
        if (clazz.name == "kotlin.Deprecated") return
        if (clazz.name.startsWith("kotlin.jvm.internal.markers.")) return
        if (clazz.name.startsWith("java.")) return
        if (clazz.name.startsWith("com.anatawa12.relocator.")) return
        if (clazz.name.startsWith("org.jetbrains.annotations.")) return
        if (clazz.name.startsWith("kotlin.jvm.JvmName")) return
        if (clazz.name.startsWith("kotlin.annotations.jvm.ReadOnly")) return
        if (clazz.name.startsWith("kotlin.annotations.jvm.Mutable")) return

        fail("uses $clazz at $location")
    }
}
