package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import java.lang.StringBuilder

//import com.anatawa12.relocator.classes.TypeParameter
//import com.anatawa12.relocator.classes.TypeSignature

internal object DescriptorSignatures {
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

        fun getAndNext(): Char {
            if (index !in value.indices) error("unexpected end")
            return value[index++]
        }

        fun tryGetAndNext(): Int {
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

        fun slice(since: Int): String = value.substring(since, index)
        fun valueOrNull(since: Int): String? = if (since == 0 && isEnd()) value else null
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

    @JvmStatic
    fun parseSimpleName(name: String, reading: String) =
        Cursor(name, reading).runCursor(::parseIdentifier)

    private fun parseIdentifier(cursor: Cursor) {
        cursor.require(cursor.getAndNext() !in blockedChars, "empty identifier")
        while (true) {
            if (cursor.isEnd()) return
            if (cursor.getAndNext() in blockedChars) {
                return cursor.movePrev()
            }
        }
    }

    @JvmStatic
    fun parseClassInternalName(internalName: String) =
        Cursor(internalName, "class internal name").runCursor(::parseSlashedIdentifier)

    private fun parseSlashedIdentifier(cursor: Cursor) {
        var c = cursor.getAndNext()
        if (c == '/') cursor.error("slash at the first")
        cursor.require(c !in blockedChars, "empty identifier")
        while (true) {
            if (cursor.isEnd()) return
            c = cursor.getAndNext()
            if (c == '/') {
                c = cursor.getAndNext()
                if (c == '/') cursor.error("double slash")
                cursor.require(c !in blockedChars, "slash at the end of identifier")
            } else if (c in blockedChars)
                return cursor.movePrev()
        }
    }

    @JvmStatic
    private fun <B> tryParseParameters(cursor: Cursor, builder: B, add: B.(TypeParameter) -> Unit) {
        if (cursor.get() != '<') return
        var count = 0
        cursor.getAndNext()
        while (cursor.get() != '>') {
            builder.add(parseTypeParameter(cursor))
            count++
        }
        cursor.getAndNext()
        cursor.require(count != 0, "empty parameters")
    }

    @JvmStatic
    fun parseTypeParameter(signature: String): TypeParameter =
        Cursor(signature, "type parameter").runCursor(::parseTypeParameter)

    @JvmStatic
    private fun parseTypeParameter(cursor: Cursor): TypeParameter {
        val idBegin = cursor.index
        parseIdentifier(cursor)
        val builder = TypeParameter.Builder(cursor.slice(idBegin))
        cursor.requireChar(':')
        if (cursor.get() != ':')
            builder.classBound(parseTypeSignature(cursor, TypeKind.RefOnly))

        while (cursor.tryGet() == ':'.code) {
            cursor.getAndNext()
            builder.addInterfaceBound(parseTypeSignature(cursor, TypeKind.RefOnly))
        }

        return builder.build()
    }

    // signatures
    @JvmStatic
    fun parseClassSignature(signature: String): ClassSignature =
        Cursor(signature, "class signature").runCursor { parseClassSignature(it) }

    @JvmStatic
    private fun parseClassSignature(cursor: Cursor): ClassSignature {
        val begin = cursor.index
        val builder = ClassSignature.Builder()
        tryParseParameters(cursor, builder, ClassSignature.Builder::addTypeParam)
        builder.superClass(parseTypeSignature(cursor, TypeKind.ClassOnly))
        while (cursor.tryGet() != -1)
            builder.addInterface(parseTypeSignature(cursor, TypeKind.ClassOnly))
        return builder.buildInternal(cursor.valueOrNull(begin))
    }

    @JvmStatic
    fun parseMethodSignature(signature: String): MethodSignature =
        Cursor(signature, "method signature").runCursor { parseMethodSignature(it) }

    @JvmStatic
    private fun parseMethodSignature(cursor: Cursor): MethodSignature {
        val begin = cursor.index
        val builder = MethodSignature.Builder()
        tryParseParameters(cursor, builder, MethodSignature.Builder::addTypeParam)
        cursor.requireChar('(')
        while (cursor.get() != ')')
            builder.addValueParam(parseTypeSignature(cursor, TypeKind.Primitive))
        cursor.getAndNext() // skip ')'
        builder.returns(parseTypeSignature(cursor, TypeKind.Voidable))
        while (cursor.tryGetAndNext() == '^'.code)
            builder.addThrows(parseTypeSignature(cursor, TypeKind.Primitive))
        return builder.buildInternal(cursor.valueOrNull(begin))
    }

    @JvmStatic
    fun parseTypeSignature(signature: String, type: TypeKind): TypeSignature =
        Cursor(signature, "type signature").runCursor { parseTypeSignature(it, type) }

    @JvmStatic
    private fun parseTypeSignature(cursor: Cursor, type: TypeKind): TypeSignature {
        val begin = cursor.index
        while (true) {
            if (cursor.get() != '[') break
            cursor.getAndNext()
        }
        val dimension = cursor.index - begin
        val tag = cursor.getAndNext()
        if (dimension == 0 && type.void && tag == 'V') return TypeSignature.VOID.array(dimension)
        if (type.prim) {
            val i = BASIC_TYPES.indexOf(tag)
            if (i != -1) return basicTypeSignatures[i].array(dimension)
        }

        when (tag) {
            'T' -> {
                if (!type.variable) cursor.error("invalid tag $tag")
                parseIdentifier(cursor)
                cursor.requireChar(';')
                return SimpleTypeSignature(cursor.slice(begin), dimension)
            }
            'L' -> {
                val beginClassName = cursor.index
                parseSlashedIdentifier(cursor)
                val builder = TypeSignature.ClassBuilder(cursor.slice(beginClassName))
                tryParseArguments(cursor, builder)
                while (cursor.get() == '.') {
                    cursor.getAndNext()
                    val beginIdent = cursor.index
                    parseIdentifier(cursor)
                    builder.innerClassName(cursor.slice(beginIdent))
                    tryParseArguments(cursor, builder)
                }
                cursor.requireChar(';')
                return builder.buildInternal(cursor.valueOrNull(begin), dimension)
            }
            else -> cursor.error("invalid tag: $tag")
        }
    }

    @JvmStatic
    private fun tryParseArguments(cursor: Cursor, builder: TypeSignature.ClassBuilder) {
        if (cursor.get() != '<') return
        var count = 0

        cursor.getAndNext()
        while (true) {
            when (cursor.getAndNext()) {
                '*' -> builder.addWildcard()
                '-' -> builder.addTypeArgument(parseTypeSignature(cursor, TypeKind.RefOnly), TypeVariant.Contravariant)
                '+' -> builder.addTypeArgument(parseTypeSignature(cursor, TypeKind.RefOnly), TypeVariant.Covariant)
                '>' -> return cursor.require(count != 0, "empty arguments")
                else -> {
                    cursor.movePrev()
                    builder.addTypeArgument(parseTypeSignature(cursor, TypeKind.RefOnly))
                }
            }
            count++
        }
    }

    @JvmStatic
    fun StringBuilder.appendParams(typeParameters: List<TypeParameter>) {
        val iterator = typeParameters.iterator()
        if (!iterator.hasNext()) return
        append('<')
        do {
            val arg = iterator.next()
            append(arg.name)
            append(':')
            arg.classBound?.let(::append)
            for (interfaceBound in arg.interfaceBounds)
                append(':').append(interfaceBound)
        } while (iterator.hasNext())
        append('>')
    }

    @Suppress("SpellCheckingInspection")
    private const val BASIC_TYPES = "BCDFIJSZ"
    private const val blockedChars = "/.[<>:;"
    private val basicTypeSignatures = arrayOf(
        TypeSignature.BYTE,
        TypeSignature.CHAR,
        TypeSignature.DOUBLE,
        TypeSignature.FLOAT,
        TypeSignature.INT,
        TypeSignature.LONG,
        TypeSignature.SHORT,
        TypeSignature.BOOLEAN,
    )
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
