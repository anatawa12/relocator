package com.anatawa12.relocator.internal

import java.util.*

/**
 * Replacement of kotlin's experimental stdlib until stabilize
 */
internal inline fun <E> buildSet(builderAction: MutableSet<E>.() -> Unit): Set<E> {
    return Collections.unmodifiableSet(mutableSetOf<E>().apply(builderAction))
}

internal fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
