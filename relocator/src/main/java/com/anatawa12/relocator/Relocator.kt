package com.anatawa12.relocator

import com.anatawa12.relocator.internal.*
import com.anatawa12.relocator.internal.ClassFile
import com.anatawa12.relocator.internal.ComputeReferenceEnvironment
import com.anatawa12.relocator.internal.EmbeddableClassPath
import com.anatawa12.relocator.internal.ReferencesClassPath
import kotlinx.coroutines.*
import kotlinx.coroutines.intrinsics.startCoroutineCancellable
import java.io.File
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.function.Function
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine

class Relocator {
    private val _referPath: MutableList<File> = ArrayList()
    /**
     * The list of jar files/directories which will be added 
     * to runtime classpath.
     * JDK bootclasspath should be added to this path.
     * If `java.lang.Object` does not exists on this path,
     * all files in `$JAVA_HOME/lib`, `$JAVA_HOME/lib/ext`,
     * `$JAVA_HOME/jre/lib`, and `$JAVA_HOME/jre/lib/ext` will be added.
     * (`$JAVA_HOME` will be the value of `java.home` system property)
     */
    val referPath: List<File> = Collections.unmodifiableList(_referPath)

    /**
     * Add a file or directory to [Relocator.referPath].
     * @param referPath The path to file or directory to [Relocator.referPath].
     */
    fun addReferPath(referPath: File) {
        this._referPath.add(referPath)
    }

    private val _embedPath: MutableList<File> = ArrayList()
    /**
     * The list of jar files/directories which has classes that will be embedded if needed.
     */
    val embedPath: List<File> = Collections.unmodifiableList(_embedPath)

    /**
     * Add a file or directory to [Relocator.embedPath].
     * @param embedPath The path to file or directory to [Relocator.embedPath].
     */
    fun addEmbedPath(embedPath: File) {
        this._embedPath.add(embedPath)
    }

    private val _rootPath: MutableList<File> = ArrayList()

    /**
     * The list of jar files/directories which has classes that always be embedded.
     */
    val rootPath: List<File> = Collections.unmodifiableList(_rootPath)

    /**
     * Add a file or directory to [Relocator.rootPath].
     * @param rootPath The path to file or directory to [Relocator.rootPath].
     */
    fun addRootPath(rootPath: File) {
        _rootPath.add(rootPath)
    }


    /**
     * @implSpec the map is ordered by length of package
     */
    private val relocateMapping: SortedMap<String, String?> = TreeMap(
        Comparator.comparingInt { obj: String -> obj.length }.reversed()
            .thenComparing(Function.identity()))

    fun addRelocateMapping(relocateFrom: String, relocateTo: String) {
        check(relocateMapping.putIfAbsent(relocateFrom,
            relocateTo) == null) { "relocation for $relocateFrom already exists." }
    }

    /**
     * False if you want to omit runtime invisible, 
     * in other words, the annotations that their [java.lang.annotation.Retention] 
     * are [java.lang.annotation.RetentionPolicy.CLASS].
     */
    var keepRuntimeInvisibleAnnotation: Boolean = true

    fun <A> run(attachment: A, callback: CompletionHandler<Void?, A>) {
        class ContinuationImpl : Continuation<Unit> {
            override val context: CoroutineContext
                get() = Dispatchers.Default

            override fun resumeWith(result: Result<Unit>) {
                if (result.isFailure) {
                    callback.failed(result.exceptionOrNull()!!, attachment)
                } else {
                    callback.completed(null, attachment)
                }
            }
        }
        val continuation = ContinuationImpl()
        try {
            (RelocatingEnvironment()::run as (suspend () -> Unit))
                .startCoroutine(continuation)
        } catch (t: Throwable) {
            continuation.resumeWith(Result.failure(t))
        }
    }

    private inner class RelocatingEnvironment {
        lateinit var refers: ReferencesClassPath
        lateinit var embeds: EmbeddableClassPath
        lateinit var roots: EmbeddableClassPath

        suspend fun run(): Unit = coroutineScope {
            refers = ReferencesClassPath(referPath, ClassFile::computeReferencesForLibrary)
            embeds = EmbeddableClassPath(embedPath)
            roots = EmbeddableClassPath(rootPath)
            listOf (
                launch { refers.init() },
                launch { embeds.init() },
                launch { roots.init() },
            ).forEach { it.join() }

            // first step: computeReferences
            (embeds.classes.asSequence() + roots.classes).map {
                launch { it.computeReferences(ComputeReferenceEnvironmentImpl(it.main.name)) }
            }.forEach { it.join() }

            // second step: connect to parent classes(including interfaces)
            // find parent class/interfaces add reference from 
            // parent class's same signature method
            // if parent method is in refers, add the method to references of
            // the class

            // third step: collect references
            // collect all references for methods/classes.

            // forth step: make a jar.
            // make a jar with relocation
            
        }

        inner class ComputeReferenceEnvironmentImpl(
            val ofClass: String,
        ) : ComputeReferenceEnvironment(
            keepRuntimeInvisibleAnnotation
        ) {
            override fun addDiagnostic(diagnostic: Diagnostic) {
                diagnostic.inClass = ofClass
                throw RuntimeException("$diagnostic")
            }
        }
    }
}
