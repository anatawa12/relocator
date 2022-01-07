package com.anatawa12.relocator.plugin

import com.anatawa12.relocator.diagnostic.DiagnosticHandler
import com.anatawa12.relocator.diagnostic.SuppressionContainer
import com.anatawa12.relocator.reflect.ReflectionMappingContainer

/**
 * This interface is not intended to be implemented by external plugins.
 * This interface will be modified
 */
interface PreClassRelocatorPluginContext {
    val reflectionMap: ReflectionMappingContainer
    val suppression: SuppressionContainer
}

/**
 * This interface is not intended to be implemented by external plugins.
 * This interface will be modified
 */
interface ClassRelocatorPluginContext {
    val relocationMapping: RelocationMapping
    val diagnosticHandler: DiagnosticHandler
    fun addClassRelocator(step: ClassRelocatorStep, relocator: ClassRelocator)
}

interface ClassRelocatorPlugin {
    fun getName(): String
    fun getDependencies(): Array<String>? = null
    //fun initialize()
    fun preApply(context: PreClassRelocatorPluginContext) {}
    fun apply(context: ClassRelocatorPluginContext) {}
}
