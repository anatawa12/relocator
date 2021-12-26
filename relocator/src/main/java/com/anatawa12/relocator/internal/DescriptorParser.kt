package com.anatawa12.relocator.internal

//import com.anatawa12.relocator.classes.TypeParameter
//import com.anatawa12.relocator.classes.TypeSignature

internal object DescriptorParser {
    private class Cursor(val value: String, val reading: String) {
        var index = 0

        fun get(): Char {
            if (index !in value.indices) error("unexpected end")
            return value[index]
        }

        fun tryGet(): Int {
            if (index !in value.indices) return -1
            return value[index].code
        }

        fun getAndNext() : Char {
            if (index !in value.indices) error("unexpected end")
            return value[index++]
        }

        fun tryGetAndNext() : Int {
            if (index !in value.indices) return -1
            return value[index++].code
        }

        fun error(message: String): Nothing = throw IllegalArgumentException("invalid $reading: $message")

        fun require(boolean: Boolean, message: String) {
            if (!boolean) error(message)
        }

        fun requireChar(char: Char) {
            val c = getAndNext()
            if (c != char) error("invalid char: '$c'")
        }

        fun end() {
            if (!isEnd()) error("unexpected trailing string")
        }

        fun movePrev() {
            index--
        }

        fun isEnd(): Boolean = index == value.length
    }

    private inline fun <R> Cursor.runCursor(withCursor: (Cursor) -> R): R {
        val result = withCursor(this)
        end()
        return result
    }

    /**
     * @return the array of index of end of a type (exclusive)
     */
    internal fun parseMethodDesc(descriptor: String): IntArray =
        Cursor(descriptor, "method descriptor").runCursor(::parseMethodDesc)

    private fun parseMethodDesc(cursor: Cursor): IntArray {
        val indices = ArrayList<Int>()
        cursor.require(cursor.getAndNext() == '(', "expected '('")
        while (cursor.get() != ')') {
            parseTypeDesc(cursor, TypeKind.Primitive)
            indices.add(cursor.index)
        }
        cursor.getAndNext() // skip ')'
        parseTypeDesc(cursor, TypeKind.Voidable)
        cursor.end()
        return indices.toIntArray()
    }

    internal fun parseTypeDesc(descriptor: String, type: TypeKind) =
        Cursor(descriptor, "type descriptor").runCursor { parseTypeDesc(it, type) }

    private fun parseTypeDesc(cursor: Cursor, type: TypeKind) {
        while (true) {
            if (cursor.get() != '[') break
            cursor.getAndNext()
        }
        val tag = cursor.getAndNext()
        if (type.void && tag == 'V') return
        if (type.prim && BASIC_TYPES.contains(tag)) return
        cursor.require(tag == 'L', "invalid tag")
        parseSlashedIdentifier(cursor)
        cursor.requireChar(';')
    }

    // utils

    fun parseSimpleName(name: String) =
        Cursor(name, "name").runCursor(::parseIdentifier)

    private fun parseIdentifier(cursor: Cursor) {
        cursor.require(cursor.getAndNext() !in blockedChars, "empty identifier")
        while (true) {
            if (cursor.getAndNext() in blockedChars) {
                return cursor.movePrev()
            }
        }
    }

    fun parseClassInternalName(internalName: String) =
        Cursor(internalName, "class internal name").runCursor(::parseSlashedIdentifier)

    private fun parseSlashedIdentifier(cursor: Cursor) {
        var c = cursor.getAndNext()
        if (c == '/') cursor.error("slash at the first")
        cursor.require(c !in blockedChars, "empty identifier")
        while (true) {
            c = cursor.getAndNext()
            if (c == '/') {
                c = cursor.getAndNext()
                if (c == '/') cursor.error("double slash")
                cursor.require(c !in blockedChars, "slash at the end of identifier")
            } else if (c in blockedChars)
                return cursor.movePrev()
        }
    }

    private fun tryParseParameters(cursor: Cursor): List<TypeParameterIndices> {
        if (cursor.get() != '<') return emptyList()
        return buildList {
            cursor.getAndNext()
            while (cursor.get() != '>') {
                add(parseTypeParameter(cursor))
            }
            cursor.getAndNext()
            cursor.require(isNotEmpty(), "empty parameters")
        }
    }

    fun parseTypeParameter(signature: String): TypeParameterIndices =
        Cursor(signature, "type parameter").runCursor(::parseTypeParameter)

    private fun parseTypeParameter(cursor: Cursor): TypeParameterIndices {
        val idBegin = cursor.index
        parseIdentifier(cursor)
        val idEnd = cursor.index
        var boundBegin = idEnd
        cursor.requireChar(':')
        val indices = buildList {
            if (!cursor.isEnd() && cursor.get() in "LT[") {
                add(parseTypeSignature(cursor, TypeKind.RefOnly).apply {
                    value[0].also { it.main = boundBegin..it.main.last }
                })
            } else {
                add(TypeSignatureIndices(boundBegin..boundBegin))
            }
            boundBegin = cursor.index
            while (cursor.tryGet() == ':'.code) {
                cursor.getAndNext()
                add(parseTypeSignature(cursor, TypeKind.RefOnly).apply {
                    value[0].also { it.main = boundBegin..it.main.last }
                })
                boundBegin = cursor.index
            }
        }
        return TypeParameterIndices(idBegin until idEnd, indices)
    }

    // signatures
    internal fun parseClassSignature(signature: String): ClassSignatureIndices =
        Cursor(signature, "class signature").runCursor { parseClassSignature(it) }

    private fun parseClassSignature(cursor: Cursor): ClassSignatureIndices {
        val parameters = tryParseParameters(cursor)
        cursor.requireChar('(')
        val superClass = parseTypeSignature(cursor, TypeKind.ClassOnly)
        val interfaces = buildList {
            while (cursor.tryGetAndNext() != -1)
                add(parseTypeSignature(cursor, TypeKind.ClassOnly))
        }
        return ClassSignatureIndices(parameters, superClass, interfaces)
    }

    internal fun parseMethodSignature(signature: String): MethodSignatureIndices =
        Cursor(signature, "method signature").runCursor { parseMethodSignature(it) }

    private fun parseMethodSignature(cursor: Cursor): MethodSignatureIndices {
        val parameters = tryParseParameters(cursor)
        cursor.requireChar('(')
        val args = buildList {
            while (cursor.get() != ')')
                add(parseTypeSignature(cursor, TypeKind.Primitive))
        }
        cursor.getAndNext() // skip ')'
        val returns = parseTypeSignature(cursor, TypeKind.Voidable)
        val throws = buildList {
            while (cursor.tryGetAndNext() == '^'.code)
                add(parseTypeSignature(cursor, TypeKind.Primitive))
        }
        return MethodSignatureIndices(parameters, args, returns, throws)
    }

    internal fun parseTypeSignature(signature: String, type: TypeKind): TypeSignatureIndices =
        Cursor(signature, "type signature").runCursor { parseTypeSignature(it, type) }

    private fun parseTypeSignature(cursor: Cursor, type: TypeKind): TypeSignatureIndices {
        val begin = cursor.index
        while (true) {
            if (cursor.get() != '[') break
            cursor.getAndNext()
        }
        val dimension = cursor.index - begin
        val tag = cursor.getAndNext()
        if (dimension == 0 && type.void && tag == 'V') return TypeSignatureIndices(begin until cursor.index)
        if (type.prim) {
            val i = BASIC_TYPES.indexOf(tag)
            if (i != -1) return TypeSignatureIndices(begin until cursor.index)
        }
        if (!type.variable) {
            cursor.requireChar('L')
            cursor.movePrev()
        }

        when (tag) {
            'T' -> {
                parseIdentifier(cursor)
                cursor.requireChar(';')
                return TypeSignatureIndices(begin until cursor.index)
            }
            'L' -> {
                parseSlashedIdentifier(cursor)
                val trailing = mutableListOf(
                    TypeSignatureIndicesElement(begin until cursor.index, tryParseArguments(cursor))
                )
                while (cursor.get() == '.') {
                    cursor.getAndNext()
                    val beginIdent = cursor.index
                    parseIdentifier(cursor)
                    trailing.add(
                        TypeSignatureIndicesElement(beginIdent until cursor.index, tryParseArguments(cursor))
                    )
                }
                cursor.requireChar(';')
                return TypeSignatureIndices(trailing)
            }
            else -> cursor.error("invalid tag: $tag")
        }
    }

    private fun tryParseArguments(cursor: Cursor): List<TypeSignatureIndices> {
        if (cursor.get() != '<') return emptyList()
        return buildList {
            cursor.getAndNext()
            var begin = cursor.index
            while (true) {
                when (cursor.getAndNext()) {
                    '*' -> {
                        add(TypeSignatureIndices(singleRange(begin)))
                        begin = cursor.index
                    }
                    '-' -> {
                        add(parseTypeSignature(cursor, TypeKind.RefOnly).apply {
                            value[0].also { it.main = begin .. it.main.last }
                        })
                        begin = cursor.index
                    }
                    '+' -> {
                        add(parseTypeSignature(cursor, TypeKind.RefOnly).apply {
                            value[0].also { it.main = begin .. it.main.last }
                        })
                        begin = cursor.index
                    }
                    '>' -> {
                        cursor.require(isNotEmpty(), "empty arguments")
                        return@buildList
                    }
                    else -> {
                        cursor.movePrev()
                        add(parseTypeSignature(cursor, TypeKind.RefOnly).apply {
                            value[0].also { it.main = begin .. it.main.last }
                        })
                        begin = cursor.index
                    }
                }
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    private const val BASIC_TYPES = "BCDFIJSZ"
    private const val blockedChars = "/.[<>:;"
}

internal enum class TypeKind(
    // true if allow void to parse
    val void: Boolean,
    // true if allow primitives parse
    val prim: Boolean,
    // true if allow type variable parse
    val variable: Boolean,
) {
    ClassOnly(false, false, false),
    RefOnly(false, false, true),
    Primitive(false, true, true),
    Voidable(true, true, true),
}

internal class TypeSignatureIndicesElement(
    // range for main part. this doesn't include heading '.'.
    // this may include '+-:' at the first
    var main: IntRange,
    val args: List<TypeSignatureIndices>,
) {
    constructor(main: IntRange, vararg args: TypeSignatureIndices) : this(main, args.asList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TypeSignatureIndicesElement

        if (main != other.main) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = main.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "($main, $args)"
    }
}

internal class TypeSignatureIndices(val value: List<TypeSignatureIndicesElement>) {
    constructor(vararg value: TypeSignatureIndicesElement) : this(value.asList())
    constructor(main: IntRange): this(TypeSignatureIndicesElement(main, emptyList()))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TypeSignatureIndices

        if (value != other.value) return false

        return true
    }

    fun getTypeRange(signature: String): IntRange? {
        var begin = value.first().main.first
        when (signature[begin]) {
            // wildcard
            '*' -> return null
            '+' -> begin += 1
            '-' -> begin += 1
            // type argument bounds
            ':' -> begin += 1
        }
        when (signature[begin]) {
            // type argument empty class bound
            ':' -> return null
            '>' -> return null
            // for class types, requires end check
            'L' -> {}
            // for others, main must refer the signature
            else -> return value.first().main
        }
        val last = value.last()
        var end: Int
        if (last.args.isNotEmpty()) {
            val lastArg = last.args.last()
            // now, the end of last type argument
            end = lastArg.getTypeRange(signature)?.last ?: lastArg.value.last().main.last
            end += 1 // skip '>'
        } else {
            // it's just before ';'
            end = last.main.last
        }
        end += 1 // skip ';'
        return begin..end
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "($value)"
}

internal class TypeParameterIndices(
    // range for main part. this doesn't include heading '.'.
    // this may include '+-:' at the first
    var main: IntRange,
    val args: List<TypeSignatureIndices>,
) {
    constructor(main: IntRange, vararg args: TypeSignatureIndices) : this(main, args.asList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TypeParameterIndices

        if (main != other.main) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = main.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String = "($main, $args)"
}

internal class MethodSignatureIndices(
    val params: List<TypeParameterIndices>,
    val args: List<TypeSignatureIndices>,
    val returns: TypeSignatureIndices,
    val throws: List<TypeSignatureIndices>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodSignatureIndices

        if (params != other.params) return false
        if (args != other.args) return false
        if (returns != other.returns) return false
        if (throws != other.throws) return false

        return true
    }

    override fun hashCode(): Int {
        var result = params.hashCode()
        result = 31 * result + args.hashCode()
        result = 31 * result + returns.hashCode()
        result = 31 * result + throws.hashCode()
        return result
    }

    override fun toString(): String {
        return "($params, $args, $returns, $throws)"
    }
}

internal class ClassSignatureIndices(
    val params: List<TypeParameterIndices>,
    val superClass: TypeSignatureIndices,
    val interfaces: List<TypeSignatureIndices>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClassSignatureIndices

        if (params != other.params) return false
        if (superClass != other.superClass) return false
        if (interfaces != other.interfaces) return false

        return true
    }

    override fun hashCode(): Int {
        var result = params.hashCode()
        result = 31 * result + superClass.hashCode()
        result = 31 * result + interfaces.hashCode()
        return result
    }

    override fun toString(): String {
        return "($params, $superClass, $interfaces)"
    }
}

/*
internal class TypeSignatureList(val signature: String, val indicesList: List<TypeSignatureIndices>) : AbstractList<TypeSignature>() {
    override val size: Int get() = indicesList.size

    override fun get(index: Int): TypeSignature {
        val indices = indicesList
        // TypeSignatureIndices
        if (index !in indices.indices)
            throw IndexOutOfBoundsException("index: $index, count: ${indices.size}")
        val sigIndices = indices[index]
        return TypeSignature(signature.substring(sigIndices.getTypeRange(signature)!!))
    }
}

internal class TypeParameterList(val signature: String, val indicesList: List<TypeParameterIndices>) : AbstractList<TypeParameter>() {
    override val size: Int get() = indicesList.size

    override fun get(index: Int): TypeParameter {
        val indices = indicesList
        // TypeSignatureIndices
        if (index !in indices.indices)
            throw IndexOutOfBoundsException("index: $index, count: ${indices.size}")
        val sigIndices = indices[index]
        val lastBound = sigIndices.args.last()
        val last = lastBound.getTypeRange(signature)?.last ?: lastBound.value.last().main.last
        return TypeParameter(signature, sigIndices.main.first.. last, sigIndices)
    }
}
*/
