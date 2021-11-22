package com.anatawa12.relocator

import com.anatawa12.relocator.internal.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
        lateinit var classpath: CombinedClassPath

        suspend fun run(): Unit = coroutineScope {
            refers = ReferencesClassPath(referPath) {
                computeReferencesForLibrary()
            }
            embeds = EmbeddableClassPath(embedPath)
            roots = EmbeddableClassPath(rootPath)
            listOf(
                launch { refers.init() },
                launch { embeds.init() },
                launch { roots.init() },
            ).forEach { it.join() }
            classpath = CombinedClassPath(listOf(roots, embeds, refers))
            val computeReferenceEnv = ComputeReferenceEnvironmentImpl(classpath)

            // first step: computeReferences
            (embeds.classes + roots.classes).map {
                launch { it.computeReferences(computeReferenceEnv) }
            }.forEach { it.join() }

            // second step: collect references
            // collect all references for methods/classes.
            collectReferences()

            // forth step: make a jar.
            // make a jar with relocation
            
        }

        private val references = Collections.newSetFromMap<Reference>(ConcurrentHashMap())

        private suspend fun collectReferences() = coroutineScope {
            roots.classes.map {
                async {
                    val deferredList = ArrayList<Deferred<Unit>>(1 + it.methods.size + it.fields.size)
                    deferredList.add(async { collectReferencesOf(ClassReference(it.name)) })
                    for (method in it.methods)
                        deferredList.add(async { collectReferencesOf(MethodReference(it.name, method.main.name, method.main.desc)) })
                    for (field in it.fields)
                        deferredList.add(async { collectReferencesOf(FieldReference(it.name, field.main.name, field.main.desc)) })
                    deferredList.forEach { it.await() }
                }
            }.forEach { it.await() }
        }

        private suspend fun collectReferencesOf(refs: Iterable<Reference>, location: Location) = coroutineScope {
            val deferredList = mutableListOf<Deferred<Unit>>()
            for (ref in refs) {
                if (references.add(ref)) {
                    ref.withLocation(location)
                    deferredList += async { collectReferencesOf(ref) }
                }
            }
            for (deferred in deferredList) deferred.await()
        }

        private suspend fun collectReferencesOf(reference: Reference) {
            when (reference) {
                is ClassReference -> collectReferencesOf(classpath.findClass(reference)
                    ?: return addDiagnostic(UnresolvableClassError(reference, reference.location ?: Location.None)))
                is FieldReference -> {
                    val fields = classpath.findFields(reference)
                    if (fields.isEmpty())
                        return addDiagnostic(UnresolvableFieldError(reference, reference.location ?: Location.None))
                    coroutineScope { fields.map { async { collectReferencesOf(it) } } }
                        .forEach { it.await() }
                }
                is MethodReference -> {
                    if (reference.owner[0] == '[' && isArrayMethod(reference)) return
                    collectReferencesOf(classpath.findMethod(reference)
                        ?: return addDiagnostic(UnresolvableMethodError(reference, reference.location ?: Location.None)))
                }
            }
        }

        private suspend fun isArrayMethod(reference: MethodReference): Boolean {
            val objectClass = classpath.findClass("java/lang/Object") ?: return false
            return objectClass.findMethod(reference) != null
        }

        private suspend fun collectReferencesOf(classFile: ClassFile) = coroutineScope {
            classFile.included = true
            collectReferencesOf(classFile.allReferences, Location.Class(classFile.name))
        }

        private suspend fun collectReferencesOf(field: ClassField) = coroutineScope {
            field.included = true
            collectReferencesOf(field.allReferences, Location.Field(field.owner.name, field.main))
        }

        private suspend fun collectReferencesOf(method: ClassMethod) = coroutineScope {
            method.included = true
            collectReferencesOf(method.allReferences, Location.Method(method.owner.name, method.main))
        }

        fun addDiagnostic(diagnostic: Diagnostic) {
            // TODO
            println("diagnostic: $diagnostic")
        }

        inner class ComputeReferenceEnvironmentImpl(
            classpath: CombinedClassPath,
        ) : ComputeReferenceEnvironment(
            keepRuntimeInvisibleAnnotation,
            classpath,
        ) {
            override fun addDiagnostic(diagnostic: Diagnostic) {
                this@RelocatingEnvironment.addDiagnostic(diagnostic)
            }
        }
    }
}
