package com.anatawa12.relocator.internal

internal class SMAPParser(private val string: String) {
    private var index: Int = 0

    private fun get() = string[index]
    private fun getOpt() = if (index in string.indices) string[index].code else -1
    private fun getMove() = string[index++]

    private fun tryRead(value: Char): Boolean {
        return if (getOpt() == value.code) {
            getMove()
            true
        } else {
            false
        }
    }

    private inline fun require(value: Char, message: (Char) -> String) {
        require(value == getMove()) { message(string[index - 1]) }
    }

    private inline fun require(value: Boolean, message: () -> String) {
        if (!value) error(message())
    }

    private fun skipWS() {
        while (true) when (getOpt()) {
            ' '.code, '\t'.code -> {
                getMove()
                continue
            }
            else -> return
        }
    }

    private fun readNewLine(optional: Boolean = false) {
        skipWS()
        if (optional && index == string.length) return
        when (val crOrLF = getMove()) {
            '\n' -> {}
            '\r' -> tryRead('\n')
            else -> unexpectedChar(crOrLF)
        }
    }

    private fun readToNewLine(skipWS: Boolean = true): String {
        if (skipWS) skipWS()
        val begin = index
        val string = string
        val newLine = string.indexOfAny(newlineChars, startIndex = begin)
        val result = if (newLine == -1) {
            index = string.length
            string.substring(begin)
        } else {
            index = newLine
            readNewLine()
            string.substring(begin, newLine)
        }
        return if (skipWS) result.trim() else result
    }

    private fun error(message: String): Nothing = throw IllegalArgumentException(message)

    private fun unexpectedChar(c: Char): Nothing = error("unexpected char: '$c'")

    private fun readInt(): Int {
        val begin = index
        var result = 0
        while (true) {
            val c = get()
            if (c !in '0'..'9') break
            getMove()
            if (result >= intMaxDiv10) error("integer too big for Int")
            result *= 10
            result += c - '0'
        }
        if (begin == index)
            error("expected int but was '${get()}'")
        skipWS()
        return result
    }

    private fun sectionSequence() = sequence {
        while (true) {
            require('*') { "invalid section id: '*' expected but was 'c'" }
            when (val sectionId = getMove()) {
                'S' -> yield(SMAPStratumSection(readToNewLine()))
                'F' -> {
                    readNewLine()
                    val infos = mutableListOf<SMAPFileInfo>()
                    while (get() != '*') {
                        if (get() == '+') {
                            getMove()
                            val id = readInt()
                            val name = readToNewLine()
                            val path = readToNewLine()
                            infos.add(SMAPFileInfo(id = id, name = name, path = path))
                        } else {
                            val id = readInt()
                            val name = readToNewLine()
                            infos.add(SMAPFileInfo(id = id, name = name))
                        }
                    }
                    yield(SMAPFileSection(infos))
                }
                'L' -> {
                    readNewLine()
                    val infos = mutableListOf<SMAPLineInfo>()
                    while (get() != '*') {
                        val inputStart = readInt()
                        val fileId = if (tryRead('#')) readInt() else -1
                        val repeatCount = if (tryRead(',')) readInt() else -1
                        require(':') { "':' expected but was '$it'" }
                        val outputStart = readInt()
                        val outputIncrement = if (tryRead(',')) readInt() else -1
                        readNewLine()
                        infos.add(SMAPLineInfo(inputStart, fileId, repeatCount, outputStart, outputIncrement))
                    }
                    yield(SMAPLineSection(infos))
                }
                'O' -> {
                    val smaps = mutableListOf<SMAP>()
                    val name = readToNewLine()
                    while (get() != '*')
                        smaps.add(readSMAPInternal())
                    getMove()
                    require('C') { "close section expected" }
                    val nameAtEnd = readToNewLine()
                    require(name == nameAtEnd) { "stratumId for embed SMAP mismatch: '$name' and '$nameAtEnd'" }
                    yield(EmbedSMAP(name, smaps))
                }
                'V' -> {
                    val lines = mutableListOf<String>()
                    val vendorId = readToNewLine()
                    while (get() != '*')
                        lines.add(readToNewLine(skipWS = false))
                    yield(SMAPVendorSection(vendorId, lines))
                }
                'E' -> {
                    readNewLine(optional = true)
                    break
                }
                else -> throw IllegalArgumentException("invalid section id: $sectionId")
            }
        }
    }

    class IteratorWrapper<T: Any>(val iterator: Iterator<T>): Iterator<T> {
        private var back: T? = null
        fun pushBack(value: T) {
            check(back == null)
            back = value
        }
        override fun hasNext(): Boolean = back != null || iterator.hasNext()
        override fun next(): T {
            back?.let { back ->
                this.back = null
                return back
            }
            return iterator.next()
        }
    }

    private fun readSMAPInternal(): SMAP {
        require('S') { "invalid header: ${string.substring(index - 0, index + 4)}" }
        require('M') { "invalid header: ${string.substring(index - 1, index + 3)}" }
        require('A') { "invalid header: ${string.substring(index - 2, index + 2)}" }
        require('P') { "invalid header: ${string.substring(index - 3, index + 1)}" }
        readNewLine()
        val outputFileName = readToNewLine()
        val defaultStratumId = readToNewLine()
        val sections = IteratorWrapper(sectionSequence().iterator())
        val elements = mutableListOf<SMAPElement>()
        while (sections.hasNext()) {
            when (val firstSec = sections.next()) {
                is SMAPStratumSection -> {
                    val stratumElements = mutableListOf<SMAPStratumElement>()
                    while (sections.hasNext()) {
                        val section = sections.next()
                        if (section is SMAPStratumElement) {
                            stratumElements.add(section)
                        } else {
                            sections.pushBack(section)
                            break
                        }
                    }
                    elements.add(SMAPStratum(firstSec.stratumId, stratumElements))
                }
                is SMAPVendorSection -> elements.add(firstSec)
                is EmbedSMAP -> elements.add(firstSec)
                is SMAPFileSection, is SMAPLineSection -> error("unexpected section: ${firstSec.javaClass.simpleName}")
            }
        }
        check(!sections.hasNext()) { "assertion error" }
        return SMAP(outputFileName, defaultStratumId, elements)
    }

    fun readSMAP(): SMAP {
        val smap = readSMAPInternal()
        // skip new line and whitespace
        while (index in string.indices && string[index] in whiteSpace) index++
        require(index == string.length) { "unexpected trailing text found: '${string.substring(index)}'" }
        return smap
    }

    companion object {
        private val whiteSpace = "\r\n \t"
        private val newlineChars = "\r\n".toCharArray()
        private const val intMaxDiv10 = Int.MAX_VALUE / 10
    }
}

internal sealed interface SMAPSection {
    fun appendTo(builder: StringBuilder)
}

internal sealed interface SMAPElement {
    fun appendTo(builder: StringBuilder)
}

internal sealed interface SMAPStratumElement {
    fun appendTo(builder: StringBuilder)
}

internal data class SMAP(
    var sourceName: String,
    var stratumName: String,
    val elements: MutableList<SMAPElement>,
) {
    constructor(sourceName: String, stratumName: String, vararg elements: SMAPElement)
            : this(sourceName, stratumName, elements.toMutableList())
    fun appendTo(builder: StringBuilder) {
        builder.appendLine("SMAP")
        builder.appendLine(sourceName)
        builder.appendLine(stratumName)
        for (element in elements)
            element.appendTo(builder)
        builder.appendLine("*E")
    }
}

internal data class SMAPStratum(
    var stratumId: String,
    val elements: MutableList<SMAPStratumElement>
) : SMAPElement {
    constructor(stratumId: String, vararg elements: SMAPStratumElement) : this(stratumId, elements.toMutableList())
    override fun appendTo(builder: StringBuilder) {
        builder.append("*S ").append(stratumId).appendLine()
        for (element in elements)
            element.appendTo(builder)
    }
}

internal data class EmbedSMAP(var name: String, val smaps: MutableList<SMAP>) : SMAPElement, SMAPSection {
    constructor(name: String, vararg smaps: SMAP) : this(name, smaps.toMutableList())
    override fun appendTo(builder: StringBuilder) {
        builder.append("*O ").append(name).appendLine()
        for (smap in smaps)
            smap.appendTo(builder)
        builder.append("*C ").append(name).appendLine()
    }
}

internal data class SMAPVendorSection(
    var vendorId: String,
    val lines: MutableList<String>
) : SMAPElement, SMAPSection {
    override fun appendTo(builder: StringBuilder) {
        builder.append("*V ").append(vendorId).appendLine()
        for (line in lines)
            builder.append(line)
    }
}

internal data class SMAPFileSection(val files: MutableList<SMAPFileInfo>) : SMAPStratumElement, SMAPSection {
    constructor(vararg files: SMAPFileInfo) : this(files.toMutableList())
    override fun appendTo(builder: StringBuilder) {
        builder.appendLine("*F")
        for (file in files)
            file.appendTo(builder)
    }
}

internal data class SMAPFileInfo(var id: Int, var name: String, var path: String? = null) {
    fun appendTo(builder: StringBuilder) {
        if (path == null) {
            builder.append(id).append(' ').append(name).appendLine()
        } else {
            builder.append('+').append(' ').append(id).append(' ').append(name).appendLine()
            builder.append(path).appendLine()
        }
    }
}

internal data class SMAPLineSection(val lines: MutableList<SMAPLineInfo>) : SMAPStratumElement, SMAPSection {
    constructor(vararg files: SMAPLineInfo) : this(files.toMutableList())
    override fun appendTo(builder: StringBuilder) {
        builder.appendLine("*L")
        for (line in lines)
            line.appendTo(builder)
    }
}

internal data class SMAPLineInfo(
    var inputStart: Int,
    var fileId: Int = -1,
    var repeatCount: Int = -1,
    var outputStart: Int,
    var outputIncrement: Int = -1,
) {
    fun appendTo(builder: StringBuilder) {
        builder.append(inputStart)
        if (fileId >= 0) builder.append('#').append(fileId)
        if (repeatCount >= 0) builder.append(',').append(repeatCount)
        builder.append(':').append(outputStart)
        if (outputIncrement >= 0) builder.append(',').append(outputIncrement)
        builder.appendLine()
    }
}

// parsing sections

internal data class SMAPStratumSection(val stratumId: String): SMAPSection {
    override fun appendTo(builder: StringBuilder) {
        builder.append("*S ").append(stratumId).appendLine()
    }
}
