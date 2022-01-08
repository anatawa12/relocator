package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.ClassContainer
import com.anatawa12.relocator.reference.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class ClassPath internal constructor(files: List<File>) {
    private val containers = files.map(ClassContainer.Companion::create)
    val files = containers.flatMap { it.files }.toSet()
    protected val classTree = ConcurrentHashMap<String, ClassFile>()
    val classes: Collection<ClassFile> = Collections.unmodifiableCollection(classTree.values)

    internal open suspend fun init() {
    }

    suspend fun loadFile(path: String): ByteArray? =
        containers.firstNotNullOfOrNull { it.loadFile(path) }

    suspend fun loadFiles(path: String): List<ByteArray> =
        containers.mapNotNull { it.loadFile(path) }

    suspend fun findClass(name: String): ClassFile? {
        val dottedName = name.replace('/', '.')
        return classTree[dottedName]
            ?: loadClass(dottedName)
                ?.also { classTree[dottedName] = it }
    }

    protected abstract suspend fun loadClass(name: String): ClassFile?
}

class CombinedClassPath(
    val classpath: List<ClassPath>,
) {
    suspend fun findClass(name: String): ClassFile? =
        classpath.firstNotNullOfOrNull { it.findClass(name) }
    suspend fun findClass(ref: ClassReference): ClassFile? =
        classpath.firstNotNullOfOrNull { it.findClass(ref.name) }

    private suspend inline fun deepClasses(rootClass: ClassReference): Flow<ClassFile> = flow {
        val classes = LinkedList<ClassReference>()
        classes.add(rootClass)
        while (classes.isNotEmpty()) {
            val classFile = findClass(classes.removeFirst()) ?: continue
            emit(classFile)
            classFile.superName?.let(classes::addFirst)
            classes.addAll(0, classFile.interfaces)
        }
    }

    suspend fun findMethod(ref: MethodReference): ClassMethod? =
        deepClasses(ref.owner)
            .mapNotNull { it.findMethod(ref) }
            .firstOrNull()

    @Suppress("EXPERIMENTAL_API_USAGE")
    suspend fun findMethods(ref: PartialMethodReference): List<ClassMethod> =
        deepClasses(ref.owner).flatMapMerge { it.findMethods(ref).asFlow() }.toList()

    @Suppress("EXPERIMENTAL_API_USAGE")
    suspend fun findMethods(ref: TypelessMethodReference): List<ClassMethod> =
        deepClasses(ref.owner).flatMapMerge { it.findMethods(ref).asFlow() }.toList()

    suspend fun findField(ref: FieldReference): ClassField? =
        deepClasses(ref.owner).mapNotNull { it.findField(ref) }.firstOrNull()

    @Suppress("EXPERIMENTAL_API_USAGE")
    suspend fun findFields(ref: PartialFieldReference): List<ClassField> =
        deepClasses(ref.owner).flatMapMerge { it.findFields(ref).asFlow() }.toList()

    suspend fun findRecordField(ref: RecordFieldReference): ClassRecordField? =
        deepClasses(ref.owner).mapNotNull { it.findRecordField(ref.name, ref.descriptor) }.firstOrNull()
}
