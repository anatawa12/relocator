package com.anatawa12.relocator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class Relocator {
    /**
     * The list of jar files/directories which has omit-able classes.
     */
    private final List<File> classPath = new ArrayList<>();

    /**
     * Add a file or directory to classpath whose classes may be omitted.
     * @param classPath The path to file or directory to classpath.
     *                  The classes contain in the classpath may be omitted.
     */
    public void addClassPath(File classPath) {
        requireNonNull(classPath, "classPath");
        this.classPath.add(classPath);
    }

    public List<File> getClassPath() {
        return Collections.unmodifiableList(classPath);
    }

    /**
     * The list of jar files/directories which has un-omit-able classes.
     */
    private final List<File> rootPath = new ArrayList<>();

    /**
     * Add a file or directory to classpath whose classes never be omitted.
     * @param classPath The path to file or directory to classpath.
     *                  The classes contain in this classpath never be omitted.
     */
    public void addRootClassPath(File classPath) {
        requireNonNull(classPath, "classPath");
        this.rootPath.add(classPath);
    }

    public List<File> getRootPath() {
        return Collections.unmodifiableList(rootPath);
    }

    /**
     * @implSpec the map is ordered by length of package
     */
    private final SortedMap<String, String> relocateMapping = new TreeMap<>(
            Comparator.comparingInt(String::length).reversed()
                    .thenComparing(Function.identity()));

    public void addRelocateMapping(String relocateFrom, String relocateTo) {
        requireNonNull(relocateFrom, "relocateFrom");
        requireNonNull(relocateTo, "relocateTo");
        if (relocateMapping.putIfAbsent(relocateFrom, relocateTo) != null) {
            throw new IllegalStateException("relocation for " + relocateFrom + " already exists.");
        }
    }
}
