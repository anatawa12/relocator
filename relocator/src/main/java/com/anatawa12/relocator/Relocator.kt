package com.anatawa12.relocator

import java.io.File
import java.util.*
import java.util.function.Function

class Relocator {
    private val _classPath: MutableList<File> = ArrayList()
    /**
     * The list of jar files/directories which has classes that will be embedded if needed.
     */
    val classPath: List<File> = Collections.unmodifiableList(_classPath)

    /**
     * Add a file or directory to [Relocator.classPath].
     * @param classPath The path to file or directory to [Relocator.classPath].
     */
    fun addClassPath(classPath: File) {
        this._classPath.add(classPath)
    }

    private val _rootPath: MutableList<File> = ArrayList()

    /**
     * The list of jar files/directories which has classes that always be embedded.
     */
    val rootPath: List<File> = Collections.unmodifiableList(_rootPath)

    /**
     * Add a file or directory to [Relocator.rootPath].
     * @param classPath The path to file or directory to [Relocator.rootPath].
     */
    fun addRootClassPath(classPath: File) {
        _rootPath.add(classPath)
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
