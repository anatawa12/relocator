package com.anatawa12.relocator

import java.io.File
import java.util.*
import java.util.function.Function

class Relocator {
    /**
     * The list of jar files/directories which has omit-able classes.
     */
    private val classPath: MutableList<File> = ArrayList()

    /**
     * Add a file or directory to classpath whose classes may be omitted.
     * @param classPath The path to file or directory to classpath.
     * The classes contain in the classpath may be omitted.
     */
    fun addClassPath(classPath: File) {
        this.classPath.add(classPath)
    }

    fun getClassPath(): List<File> {
        return Collections.unmodifiableList(classPath)
    }

    /**
     * The list of jar files/directories which has un-omit-able classes.
     */
    private val rootPath: MutableList<File> = ArrayList()

    /**
     * Add a file or directory to classpath whose classes never be omitted.
     * @param classPath The path to file or directory to classpath.
     * The classes contain in this classpath never be omitted.
     */
    fun addRootClassPath(classPath: File) {
        rootPath.add(classPath)
    }

    fun getRootPath(): List<File> {
        return Collections.unmodifiableList(rootPath)
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
}
