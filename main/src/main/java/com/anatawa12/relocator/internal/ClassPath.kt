package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.ClassFile
import com.anatawa12.relocator.classes.ClassPath
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
                        if (name.endsWith("module-info")) return@launch // TODO: temporal until module support
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
