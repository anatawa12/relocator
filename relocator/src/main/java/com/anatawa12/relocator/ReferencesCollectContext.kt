package com.anatawa12.relocator

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.CombinedClassPath
import com.anatawa12.relocator.internal.EmbeddableClassPath
import com.anatawa12.relocator.internal.Reference

// TODO: make public
internal abstract class ReferencesCollectContext {
    abstract val roots: EmbeddableClassPath
    abstract val classpath: CombinedClassPath

    abstract fun runChildThread(run: ReferencesCollectContext.() -> Unit)
    fun collectReferencesOf(reference: Reference) = collectReferencesOf(reference, null)
    abstract fun collectReferencesOf(reference: Reference, location: Location?)
    fun collectReferencesOf(refs: Iterable<Reference>) = collectReferencesOf(refs, null)
    fun collectReferencesOf(refs: Iterable<Reference>, location: Location?) {
        for (ref in refs) {
            collectReferencesOf(ref, location)
        }
    }
}
