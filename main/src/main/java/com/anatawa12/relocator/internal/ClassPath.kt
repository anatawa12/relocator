package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.ClassFile
import com.anatawa12.relocator.classes.ClassPath
import com.anatawa12.relocator.file.SingleFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry

internal abstract class ClassContainer(val file: File) {
    val files: Set<String> by lazy { getPathList() }

    suspend fun loadFile(path: String): SingleFile? = loadFiles(path).firstOrNull()
    abstract suspend fun loadFiles(path: String): List<SingleFile>
    protected abstract fun getPathList(): Set<String>

    companion object {
        fun create(file: File): ClassContainer =
            if (file.isDirectory) Directory(file) else Jar(file)
    }

    class Jar(file: File) : ClassContainer(file) {
        private val zipFile = JarFile(file)
        private val mutex = Mutex()
        private val multiRelease: Boolean
        internal val releases: IntArray

        init {
            multiRelease = zipFile.getEntry(JarFile.MANIFEST_NAME)?.let { manifestEntry ->
                val manifest = Manifest()
                zipFile.getInputStream(manifestEntry).use { manifest.read(it) }
                manifest.mainAttributes.getValue(MULTI_RELEASE).toBoolean()
            } ?: false
            releases = if (!multiRelease) emptyInts else {
                zipFile.entries()
                    .asSequence()
                    .mapNotNull { entry -> parseVersionedName(entry.name).takeIf { it != 0 } }
                    .toSet()
                    .toIntArray()
            }
        }

        override suspend fun loadFiles(path: String): List<SingleFile> = mutex.withLock {
            withContext(Dispatchers.IO) {
                if (path.startsWith("$META_INF/")) {
                    // always single release
                    listOfNotNull(getEntryOrNull(path, 0))
                } else {
                    // maybe multiple release
                    val files = mutableListOf<SingleFile>()
                    getEntryOrNull(path, 0)?.let(files::add)
                    for (release in releases) {
                        getEntryOrNull(path, release)?.let(files::add)
                    }
                    files
                }
            }
        }

        private fun getEntryOrNull(path: String, release: Int): SingleFile? = kotlin.runCatching {
            if (release == 0)
                zipFile.getEntry(path)
                    ?.let(zipFile::getInputStream)
                    ?.use { SingleFile(it.readBytes(), release) }
            else
                zipFile.getEntry("$META_INF_VERSIONS/$release/$path")
                    ?.let(zipFile::getInputStream)
                    ?.use { SingleFile(it.readBytes(), release) }
        }.getOrNull()

        override fun getPathList(): Set<String> {
            return zipFile.entries()
                .asSequence()
                .filter { !it.isDirectory }
                .map(ZipEntry::getName)
                .let { seq ->
                    if (!multiRelease) seq else seq.map { name ->
                        val version = parseVersionedName(name)
                        if (version == 0) name else {
                            name.substring(META_INF_VERSIONS.length + "/".length + version.toString().length + "/".length)
                        }
                    }
                }
                .toSet()
        }

        private fun parseVersionedName(name: String): Int {
            if (!name.startsWith("$META_INF_VERSIONS/")) return 0
            val versionPart = name.substring(META_INF_VERSIONS.length + "/".length).substringBefore('/', "")
            val version = versionPart.toIntOrNull() ?: 0
            if (version < 9) return 0
            return version
        }

        companion object {
            val MULTI_RELEASE = Attributes.Name("Multi-Release")
            const val META_INF = "META-INF"
            const val META_INF_VERSIONS = "$META_INF/versions"
            val emptyInts = intArrayOf()
        }
    }

    class Directory(file: File) : ClassContainer(file) {
        private val mutex = Mutex()

        override suspend fun loadFiles(path: String): List<SingleFile> = mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    file.resolve(path).inputStream().use { listOf(SingleFile(it.readBytes())) }
                } catch (ignored: FileNotFoundException) {
                    emptyList()
                }
            }
        }

        override fun getPathList(): Set<String> =
            file.walk()
                .filter { it.isFile }
                .map { it.toRelativeString(file) }
                .toSet()
    }
}

internal class EmbeddableClassPath(
    files: List<File>,
    val debug: Boolean,
): ClassPath(files) {
    override suspend fun init() {
        coroutineScope {
            files.asSequence()
                .filter { it.endsWith(".class") }
                .map { path ->
                    launch {
                        val name = path.replace('/', '.').removeSuffix(".class")
                        if (name == "module-info") return@launch // TODO: temporal until module support
                        classTree[name] = Reader.read(loadFile(path)!!, this@EmbeddableClassPath, debug)
                    }
                }
                .toList()
                .forEach { it.join() }
        }
    }

    override suspend fun loadClass(name: String): ClassFile? = null
}

internal class ReferencesClassPath(
    files: List<File>,
    val debug: Boolean,
    val initializer: ClassFile.() -> Unit,
): ClassPath(files) {
    override suspend fun loadClass(name: String): ClassFile? {
        val path = name.replace('.', '/')
        return loadFile("$path.class")?.let { Reader.read(it, this, debug, true) }?.apply(initializer)
    }
}
