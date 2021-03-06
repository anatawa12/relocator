package com.anatawa12.relocator.diagnostic

import com.anatawa12.relocator.classes.MethodDescriptor
import com.anatawa12.relocator.classes.TypeDescriptor
import com.anatawa12.relocator.diagnostic.SuppressingLocation.*
import com.google.common.collect.HashMultimap
import org.intellij.lang.annotations.Language
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// TODO: pattern based location
class SuppressionContainer {
    private val withLocation = HashMultimap.create<Pair<SuppressingLocation, String>, SuppressingDiagnostic>()
    private val withoutLocation = HashMultimap.create<String, SuppressingDiagnostic>()
    // Location for caching for location, String for package name
    private val caching = ConcurrentHashMap<Pair<Any, String>, Set<SuppressingDiagnostic>>()

    fun getDiagnosticList(location: Location, name: String): Set<SuppressingDiagnostic> {
        return caching[location to name] ?: computeDiagnosticList(location, name)
            .also { caching[location to name] = it }
    }

    private fun computeDiagnosticList(location: Location, name: String): Set<SuppressingDiagnostic> = buildSet {
        when (location) {
            Location.None -> {
            }
            is Location.Class -> {
                val className = location.name
                addAll(withLocation[InClass(className) to name])
                val packageName = className.substringBeforeLast('/', "")
                addAll(getDiagnosticListForPackage(packageName, name))
            }
            is Location.RecordField -> {
                addAll(getDiagnosticList(Location.Class(location.owner), name))
                addAll(withLocation[InField(location.owner, location.name) to name])
                addAll(withLocation[InFieldWithType(location.owner, location.name, location.descriptor) to name])
            }
            is Location.Method -> {
                addAll(getDiagnosticList(Location.Class(location.owner), name))
                addAll(withLocation[InMethod(location.owner, location.name) to name])
                addAll(withLocation[InMethodWithType(location.owner, location.name, location.descriptor) to name])
            }
            is Location.MethodLocal -> {
                addAll(getDiagnosticList(Location.Class(location.owner), name))
                addAll(withLocation[InMethod(location.owner, location.mName) to name])
                addAll(withLocation[InMethodWithType(location.owner, location.mName, location.descriptor) to name])
            }
            is Location.Field -> {
                addAll(getDiagnosticList(Location.Class(location.owner), name))
                addAll(withLocation[InField(location.owner, location.name) to name])
                addAll(withLocation[InFieldWithType(location.owner, location.name, location.descriptor) to name])
            }
        }
        addAll(withoutLocation[name])
    }

    private fun getDiagnosticListForPackage(packageName: String, name: String): Set<SuppressingDiagnostic> {
        caching[packageName to name]?.let { return it }
        val list = buildSet {
            @Suppress("NAME_SHADOWING") var packageName = packageName
            while (packageName.isNotEmpty()) {
                val cached = caching[packageName to name]
                if (cached != null) {
                    addAll(cached)
                    break
                }
                addAll(withLocation[InPackage(packageName) to name])
                packageName = packageName.substringBeforeLast('/', "")
            }
        }
        caching[packageName to name] = list
        return list
    }

    fun add(diagnostic: SuppressingDiagnostic) {
        if (diagnostic.location == null) {
            withoutLocation.put(diagnostic.name, diagnostic)
        } else {
            withLocation.put(diagnostic.location to diagnostic.name, diagnostic)
        }
    }

    fun add(location: SuppressingLocation?, name: String, vararg values: SuppressingValue<*>) {
        add(SuppressingDiagnostic(location, name, *values))
    }
}

class SuppressingDiagnostic(
    val location: SuppressingLocation?,
    val name: String,
    val values: List<SuppressingValue<*>>,
) {
    constructor(location: SuppressingLocation?, name: String, vararg values: SuppressingValue<*>) :
            this(location, name, values.asList())
}

sealed class SuppressingValue<in T>() {
    object Any : SuppressingValue<Any>()
    class IntRange private constructor(val min: Int, val max: Int) : SuppressingValue<Int>()
    class StringPattern(val pattern: Pattern) : SuppressingValue<Int>() {
        constructor(@Language("RegExp") pattern: String) : this(Pattern.compile(pattern))
    }
    class IntValue(val value: Int) : SuppressingValue<Int>()
    class StringValue(val value: String) : SuppressingValue<Int>()
}

sealed class SuppressingLocation {
    class InPackage(name: String) : SuppressingLocation() {
        val name: String

        init {
            this.name = name.replace('.', '/')
        }

        override fun equals(other: Any?): Boolean = other is InPackage
                && other.name == this.name

        override fun hashCode(): Int = 0
            .shl(31).plus(name.hashCode())

        override fun toString(): String = "InPackage($name)"
    }

    class InClass(name: String) : SuppressingLocation() {
        val name: String

        init {
            this.name = name.replace('.', '/')
        }

        override fun equals(other: Any?): Boolean = other is InClass
                && other.name == this.name

        override fun hashCode(): Int = 0
            .shl(31).plus(name.hashCode())

        override fun toString(): String = "InClass($name)"
    }

    class InMethod(owner: String, val name: String) : SuppressingLocation() {
        val owner: String

        init {
            this.owner = owner.replace('.', '/')
        }

        override fun equals(other: Any?): Boolean = other is InMethod
                && other.owner == this.owner
                && other.name == this.name

        override fun hashCode(): Int = 0
            .shl(31).plus(name.hashCode())

        override fun toString(): String = "InMethod($owner, $name)"
    }

    class InMethodWithType(owner: String, val name: String, val descriptor: MethodDescriptor) : SuppressingLocation() {
        val owner: String

        init {
            this.owner = owner.replace('.', '/')
        }

        override fun equals(other: Any?): Boolean = other is InMethodWithType
                && other.owner == this.owner
                && other.name == this.name
                && other.descriptor == this.descriptor

        override fun hashCode(): Int = 0
            .shl(31).plus(name.hashCode())

        override fun toString(): String = "InMethodWithType($owner, $name, $descriptor)"
    }

    class InField(owner: String, val name: String) : SuppressingLocation() {
        val owner: String

        init {
            this.owner = owner.replace('.', '/')
        }

        override fun equals(other: Any?): Boolean = other is InField
                && other.owner == this.owner
                && other.name == this.name

        override fun hashCode(): Int = 0
            .shl(31).plus(name.hashCode())

        override fun toString(): String = "InField($owner, $name)"
    }

    class InFieldWithType(owner: String, val name: String, val descriptor: TypeDescriptor) : SuppressingLocation() {
        val owner: String

        init {
            this.owner = owner.replace('.', '/')
        }

        override fun equals(other: Any?): Boolean = other is InFieldWithType
                && other.owner == this.owner
                && other.name == this.name
                && other.descriptor == this.descriptor

        override fun hashCode(): Int = 0
            .shl(31).plus(name.hashCode())

        override fun toString(): String = "InFieldWithType($owner, $name)"
    }
}
