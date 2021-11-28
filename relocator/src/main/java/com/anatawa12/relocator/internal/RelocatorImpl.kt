package com.anatawa12.relocator.internal

import com.anatawa12.relocator.ReferenceCollector
import com.anatawa12.relocator.ReferencesCollectContext
import com.anatawa12.relocator.Relocator
import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.diagnostic.*
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_CLASS
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_FIELD
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_METHOD
import com.anatawa12.relocator.reference.*
import com.anatawa12.relocator.reference.withLocation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class RelocatingEnvironment(val relocator: Relocator) {
    lateinit var refers: ReferencesClassPath
    lateinit var embeds: EmbeddableClassPath
    lateinit var roots: EmbeddableClassPath
    lateinit var classpath: CombinedClassPath
    val diagnosticHandler = InternalDiagnosticHandlerWrapper(relocator.diagnosticHandler)
    private val collectors = listOf<ReferenceCollector>(
        DefaultCollector,
    )

    suspend fun run(): Unit = coroutineScope {
        refers = ReferencesClassPath(relocator.referPath) {
            computeReferencesForLibrary()
        }
        embeds = EmbeddableClassPath(relocator.embedPath)
        roots = EmbeddableClassPath(relocator.rootPath)
        listOf(
            launch { refers.init() },
            launch { embeds.init() },
            launch { roots.init() },
        ).forEach { it.join() }

        checkNoErrors()

        classpath = CombinedClassPath(listOf(roots, embeds, refers))
        val computeReferenceEnv = ComputeReferenceEnvironment(
            relocator.keepRuntimeInvisibleAnnotation,
            classpath,
            diagnosticHandler,
        )

        // first step: computeReferences
        (embeds.classes + roots.classes).map {
            launch { it.computeReferences(computeReferenceEnv) }
        }.forEach { it.join() }

        checkNoErrors()

        // second step: collect references
        // collect all references for methods/classes.
        collectReferences()

        checkNoErrors()

        // forth step: make a jar.
        // make a jar with relocation

    }

    private fun checkNoErrors() {
        if (diagnosticHandler.errorCount != 0)
            throw ErrorFoundException()
    }

    private suspend fun collectReferences() = TaskQueue {
        val context = ReferencesCollectContextImpl(
            roots,
            classpath,
            this,
            diagnosticHandler,
        )
        for (collector in collectors) {
            start { collector.apply { context.collect() } }
        }
    }
}

private class ReferencesCollectContextImpl(
    override val roots: EmbeddableClassPath,
    override val classpath: CombinedClassPath,
    private val queue: TaskQueue,
    private val addDiagnostic: DiagnosticHandler,
) : ReferencesCollectContext() {
    private val references = Collections.newSetFromMap<Reference>(ConcurrentHashMap())

    override fun runChildThread(run: ReferencesCollectContext.() -> Unit) {
        queue.start { run() }
    }

    override fun collectReferencesOf(reference: Reference, location: Location?) {
        if (!references.add(reference)) return
        if (reference.location == null)
            reference.withLocation(location ?: Location.None)
        queue.start {
            when (reference) {
                is ClassReference -> collectReferencesOf(classpath.findClass(reference)
                    ?: return@start addDiagnostic(UNRESOLVABLE_CLASS(reference.name,
                        reference.location ?: Location.None)))
                is FieldReference -> {
                    val fields = classpath.findFields(reference)
                    if (fields.isEmpty())
                        return@start addDiagnostic(UNRESOLVABLE_FIELD(reference.owner,
                            reference.name,
                            reference.descriptor,
                            reference.location ?: Location.None))
                    fields.forEach { start { collectReferencesOf(it) } }
                }
                is MethodReference -> {
                    if (reference.owner[0] == '[' && isArrayMethod(reference))
                        return@start
                    collectReferencesOf(classpath.findMethod(reference)
                        ?: return@start addDiagnostic(UNRESOLVABLE_METHOD(reference.owner,
                            reference.name,
                            reference.descriptor,
                            reference.location ?: Location.None)))
                }
            }
        }
    }

    private suspend fun isArrayMethod(reference: MethodReference): Boolean {
        val objectClass = classpath.findClass("java/lang/Object") ?: return false
        return objectClass.findMethod(reference) != null
    }

    private fun collectReferencesOf(classFile: ClassFile) {
        classFile.included = true
        collectReferencesOf(classFile.allReferences, Location.Class(classFile.name))
    }

    private fun collectReferencesOf(field: ClassField) {
        field.included = true
        collectReferencesOf(field.allReferences, Location.Field(field.owner.name, field.main))
    }

    private fun collectReferencesOf(method: ClassMethod) {
        method.included = true
        collectReferencesOf(method.allReferences, Location.Method(method.owner.name, method.main))
    }
}

private object DefaultCollector : ReferenceCollector {
    override fun ReferencesCollectContext.collect() {
        for (classFile in roots.classes) {
            collectReferencesOf(ClassReference(classFile.name))
            for (method in classFile.methods)
                collectReferencesOf(MethodReference(classFile.name,
                    method.main.name,
                    method.main.desc))
            for (field in classFile.fields)
                collectReferencesOf(FieldReference(classFile.name, field.main.name, field.main.desc))
        }
    }
}