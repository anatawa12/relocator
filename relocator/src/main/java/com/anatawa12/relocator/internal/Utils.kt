package com.anatawa12.relocator.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentLinkedQueue

internal fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag

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
