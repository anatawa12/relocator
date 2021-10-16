package com.anatawa12.relocator.internal

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal abstract class ClassPath(files: List<File>) {
    private val containers = files.map(ClassContainer::create)
    val files = containers.flatMap { it.files }.toSet()

    open suspend fun init() {
    }

    suspend fun loadFile(path: String): ByteArray? =
        containers.firstNotNullOfOrNull { it.loadFile(path) }

    protected val classTree = mutableMapOf<String, ClassFile>()
    suspend fun findClass(name: String): ClassFile? {
        val dottedName = name.replace('/', '.')
        return classTree[dottedName]
            ?: loadClass(dottedName)
                ?.also { classTree[dottedName] = it }
    }

    protected abstract suspend fun loadClass(name: String): ClassFile?
}

internal abstract class ClassContainer(val file: File) {
    val files: Set<String> by lazy { getPathList() }

    abstract suspend fun loadFile(path: String): ByteArray?
    protected abstract fun getPathList(): Set<String>

    companion object {
        fun create(file: File): ClassContainer =
            if (file.isDirectory) Directory(file) else Jar(file)
    }

    class Jar(file: File) : ClassContainer(file) {
        private val zipFile = ZipFile(file)
        private val mutex = Mutex()

        override suspend fun loadFile(path: String): ByteArray? = mutex.withLock {
            withContext(Dispatchers.IO) {
                zipFile.getEntry(path)
                    ?.let(zipFile::getInputStream)
                    ?.use { it.readBytes() }
            }
        }

        override fun getPathList(): Set<String> =
            zipFile.entries()
                .asSequence()
                .filter { !it.isDirectory }
                .map(ZipEntry::getName)
                .toSet()
    }

    class Directory(file: File) : ClassContainer(file) {
        private val mutex = Mutex()

        override suspend fun loadFile(path: String): ByteArray? = mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    file.resolve(path).inputStream().use { it.readBytes() }
                } catch (ignored: FileNotFoundException) {
                    null
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

internal class EmbeddableClassPath(files: List<File>): ClassPath(files) {
    val classes: Collection<ClassFile> = Collections.unmodifiableCollection(classTree.values)

    override suspend fun init() {
        coroutineScope {
            files.asSequence()
                .filter { it.endsWith(".class") }
                .map { name ->
                    launch {
                        val path = name.replace('/', '.')
                        classTree[name] = ClassFile.read(loadFile("$path.class")!!)
                    }
                }
                .forEach { it.join() }
        }
    }

    override suspend fun loadClass(name: String): ClassFile? = null
}

internal class ReferencesClassPath(files: List<File>): ClassPath(files) {
    override suspend fun loadClass(name: String): ClassFile? {
        val path = name.replace('/', '.')
        return loadFile("$path.class")?.let { ClassFile.read(it, true) }
    }
}
