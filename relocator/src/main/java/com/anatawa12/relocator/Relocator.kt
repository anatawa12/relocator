package com.anatawa12.relocator

import com.anatawa12.relocator.diagnostic.DiagnosticException
import com.anatawa12.relocator.diagnostic.DiagnosticHandler
import com.anatawa12.relocator.diagnostic.SuppressionContainer
import com.anatawa12.relocator.internal.RelocatingEnvironment
import com.anatawa12.relocator.internal.ThrowingDiagnosticHandler
import com.anatawa12.relocator.internal.plugins.exclude.ExcludeClassRelocatorPlugin
import com.anatawa12.relocator.plugin.ClassRelocatorPlugin
import com.anatawa12.relocator.reflect.ReflectionMappingContainer
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.function.Function
import kotlin.collections.ArrayDeque
import kotlin.collections.LinkedHashMap
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

    private val _plugins = LinkedHashMap<String, ClassRelocatorPlugin>()

    /**
     * The list of [ClassRelocatorPlugin].
     */
    val plugins: Map<String, ClassRelocatorPlugin> = Collections.unmodifiableMap(_plugins)

    init {
        addPlugin(ExcludeClassRelocatorPlugin())
    }

    /**
     * Install a [ClassRelocatorPlugin].
     * @param plugin The [ClassRelocatorPlugin] to be installed
     */
    fun addPlugin(plugin: ClassRelocatorPlugin) {
        if (_plugins.putIfAbsent(plugin.getName(), plugin) != null)
            error("a plugin named '${plugin.getName()}' already installed")
    }

    private val serviceLoader by lazy {
        ServiceLoader.load(ClassRelocatorPlugin::class.java, classLoader)
    }

    /**
     * Install a [ClassRelocatorPlugin] via service loader with name.
     * This will also install dependency plugins.
     * This can be used to install 
     * @param name The name of [ClassRelocatorPlugin] to be installed.
     */
    fun addPlugin(name: String) {
        if (name in _plugins) error("a plugin named '$name' already installed")
        val requests = ArrayDeque<Pair<String?, String>>()
        val plugins = mutableListOf<ClassRelocatorPlugin>()
        requests.addLast(null to name)
        while (requests.isNotEmpty()) {
            val (source, finding) = requests.removeFirst()
            if (finding in _plugins) continue
            val found = serviceLoader.firstOrNull { it.getName() == finding }
                ?: if (source != null) error("ClassRelocatorPlugin named '$name' not found; requested by '$source'")
                else error("ClassRelocatorPlugin named '$name' not found")
            plugins.add(found)
        }

        for (plugin in plugins) _plugins[plugin.getName()] = plugin
    }

    /**
     * The [ClassLoader] for service loader.
     * It's required to set this ClassLoader before you call apis uses [ServiceLoader].
     * Defaults the [ClassLoader] of [Relocator].
     */
    var classLoader: ClassLoader = Relocator::class.java.classLoader

    val reflectionMap = ReflectionMappingContainer()

    val suppression = SuppressionContainer()

    /**
     * @implSpec the map is ordered by length of package
     */
    private val _relocateMapping: SortedMap<String, String?> = TreeMap(
        Comparator.comparingInt { obj: String -> obj.length }.reversed()
            .thenComparing(Function.identity()))

    val relocateMapping: Map<String, String?> = Collections.unmodifiableMap(_relocateMapping)

    fun addRelocateMapping(relocateFrom: String, relocateTo: String) {
        val normalizedFrom = normalizeMapValue(relocateFrom)
        val normalizedTo = normalizeMapValue(relocateTo)
        check(_relocateMapping.putIfAbsent(normalizedFrom, normalizedTo) == null) {
            "relocation for $relocateFrom already exists."
        }
    }

    private fun normalizeMapValue(input: String): String {
        var normalized = input
        normalized = normalized.replace('.', '/')
        if (normalized.endsWith('/')) normalized = normalized.substring(0, normalized.length - 1)
        if (normalized.startsWith('/')) normalized = normalized.substring(1)
        return normalized
    }

    /**
     * False if you want to omit runtime invisible, 
     * in other words, the annotations that their [java.lang.annotation.Retention] 
     * are [java.lang.annotation.RetentionPolicy.CLASS].
     */
    var keepRuntimeInvisibleAnnotation: Boolean = true

    /**
     * The function to handle diagnostics.
     * By default, the DiagnosticHandler which throws [DiagnosticException] is set.
     */
    var diagnosticHandler: DiagnosticHandler = ThrowingDiagnosticHandler

    /**
     * If true, the features for debugging Relocator and plugin will be enabled.
     * Currently. this will print some features to STDOUT.
     */
    // TODO: add parameter to configure debug output
    var debugMode: Boolean = false

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
            (RelocatingEnvironment(this)::run as (suspend () -> Unit))
                .startCoroutine(continuation)
        } catch (t: Throwable) {
            continuation.resumeWith(Result.failure(t))
        }
    }
}
