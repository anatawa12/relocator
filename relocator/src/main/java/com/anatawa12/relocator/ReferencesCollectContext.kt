package com.anatawa12.relocator

import com.anatawa12.relocator.classes.ClassPath
import com.anatawa12.relocator.diagostic.Location
import com.anatawa12.relocator.classes.CombinedClassPath
import com.anatawa12.relocator.reference.Reference

// TODO: make public
abstract class ReferencesCollectContext {
    abstract val roots: ClassPath
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
