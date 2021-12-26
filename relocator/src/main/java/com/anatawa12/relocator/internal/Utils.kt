package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.KeyValuePair
import com.anatawa12.relocator.diagnostic.Diagnostic
import com.anatawa12.relocator.diagnostic.DiagnosticHandler
import com.google.common.collect.BiMap
import com.google.common.collect.ImmutableBiMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

internal fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
internal operator fun DiagnosticHandler.invoke(diagnostic: Diagnostic) = handle(diagnostic)
internal inline fun <reified R, T : R> Array<T>.copy(): Array<R> = Array(size) { this[it] }

internal inline fun <reified R, T> Array<T>.mapToArray(map: (T) -> R): Array<R> = Array(size) { map(this[it]) }

class TaskQueue(
    val scope: CoroutineScope,
) {
    private val queue = ConcurrentLinkedQueue<Deferred<Unit>>()

    fun start(block: suspend TaskQueue.() -> Unit) {
        queue.add(scope.async { block() })
    }

    suspend fun startRoot(block: suspend TaskQueue.() -> Unit) {
        start(block)
        while (true) {
            (queue.poll() ?: return).await()
        }
    }

    companion object {
        suspend operator fun invoke(block: suspend TaskQueue.() -> Unit) = coroutineScope {
            TaskQueue(this).startRoot(block)
        }
    }
}

internal fun Int.checkBits(bits: Int, param: String): Int {
    if (this in 0 until (1 shl bits)) return this
    else throw IllegalArgumentException("$param is out of range: this")
}

internal fun Int.checkBitsOrM1(bits: Int, param: String): Int {
    if (this in -1  until (1 shl bits) - 1) return this
    else throw IllegalArgumentException("$param is out of range: this")
}

internal fun <K, V> biMapOf(vararg pairs: Pair<K, V>): BiMap<K, V> = ImmutableBiMap.builder<K, V>().apply {
    for ((k, v) in pairs)
        put(k, v)
}.build()

internal inline fun <reified K : Enum<K>, V> enumMapOf(
    vararg pairs: Pair<K, V>,
): EnumMap<K, V> = EnumMap<K, V>(K::class.java).apply { putAll(pairs) }

internal fun assertError(message: String): Nothing = throw AssertionError(message)

internal inline fun <reified T> Array<T>.takeIfNonZero(count: Int) = if (count == 0) this.asList() else take(count)

internal operator fun KeyValuePair.component1() = key
internal operator fun KeyValuePair.component2() = value
internal fun singleRange(value: Int): IntRange = value..value
