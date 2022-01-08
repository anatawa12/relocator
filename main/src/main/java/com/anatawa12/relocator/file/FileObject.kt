
package com.anatawa12.relocator.file

@Suppress("OVERLOADS_WITHOUT_DEFAULT_ARGUMENTS")
class FileObject @JvmOverloads constructor(
    var path: String,
    val files: MutableList<SingleFile>,
) {
    // list of files
}

class SingleFile @JvmOverloads constructor(var data: ByteArray, val release: Int = 0) {
    init {
        require(release == 0 || release in 9..Int.MAX_VALUE) { "invalid multi release version" }
    }
}
