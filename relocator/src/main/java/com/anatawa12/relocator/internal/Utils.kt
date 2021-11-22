package com.anatawa12.relocator.internal

internal fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
