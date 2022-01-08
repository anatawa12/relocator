package com.anatawa12.relocator.internal.plugins.smap

import com.anatawa12.relocator.plugin.ClassRelocatorPlugin
import com.anatawa12.relocator.plugin.ClassRelocatorPluginContext
import com.anatawa12.relocator.plugin.ClassRelocatorStep

class SMAPClassRelocatorPlugin : ClassRelocatorPlugin {
    override fun getName(): String = "smap"

    override fun apply(context: ClassRelocatorPluginContext) {
        context.addClassRelocator(ClassRelocatorStep.Finalizing, SMAPRelocator(context.relocationMapping))
    }
}
