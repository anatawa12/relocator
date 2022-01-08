package com.anatawa12.relocator.internal

import java.util.*

internal class OwnerBasedSet<E : Any, T : Any>(
    private val owner: T,
    private val accessor: OwnerAccessor<E, T>,
) : AbstractMutableSet<E>() {
    private val backed = LinkedList<E>()

    override fun add(element: E): Boolean {
        if (!accessor.preAdd(element, owner, true)) return false
        backed.add(element)
        return true
    }

    override fun iterator(): MutableIterator<E> = IteratorImpl(backed.iterator())

    override fun remove(element: E): Boolean {
        if (accessor.check(element, owner)) return false
        assert(backed.remove(element)) { "backed must have the element whose target is correct" }
        accessor.postRemove(element)
        return true
    }

    override val size: Int by backed::size

    override fun contains(element: E): Boolean = accessor.check(element, owner)

    override fun isEmpty(): Boolean = backed.isEmpty()

    open inner class IteratorImpl(private val backed: MutableIterator<E>): MutableIterator<E> {
        private var last: E? = null

        override fun hasNext(): Boolean = backed.hasNext()

        override fun next(): E = backed.next().also { last = it }

        override fun remove() {
            val value = last ?: throw IllegalStateException()
            backed.remove()
            accessor.postRemove(value)
            last = null
        }
    }
}

internal class OwnerBasedList<E : Any, T : Any>(
    private val owner: T,
    private val accessorGetter: () -> OwnerAccessor<E, T>,
) : AbstractMutableList<E>() {
    private val accessor: OwnerAccessor<E, T> get() = accessorGetter()
    private val backed = ArrayList<E>()

    override fun add(element: E): Boolean {
        accessor.preAdd(element, owner, false)
        return backed.add(element)
    }

    override fun add(index: Int, element: E) {
        accessor.preAdd(element, owner, false)
        backed.add(index, element)
    }

    override fun get(index: Int): E = backed[index]

    override fun iterator(): MutableIterator<E> = listIterator()

    override fun listIterator(index: Int): MutableListIterator<E> = IteratorImpl(backed.listIterator(index))

    override fun remove(element: E): Boolean {
        if (accessor.check(element, owner)) return false
        assert(backed.remove(element)) { "backed must have the element whose target is correct" }
        accessor.postRemove(element)
        return true
    }

    override fun removeAt(index: Int): E = backed.removeAt(index).also { accessor.clear(it) }

    override fun set(index: Int, element: E): E {
        accessor.preAdd(element, owner, false)
        return backed.set(index, element)
    }

    override val size: Int by backed::size

    override fun contains(element: E): Boolean = accessor.check(element, owner)

    override fun isEmpty(): Boolean = backed.isEmpty()

    open inner class IteratorImpl(private val backed: MutableListIterator<E>): MutableListIterator<E> {
        private var last: E? = null

        override fun add(element: E) {
            accessor.preAdd(element, owner, false)
            backed.add(element)
            last = null
        }

        override fun hasNext(): Boolean = backed.hasNext()

        override fun hasPrevious(): Boolean = backed.hasPrevious()

        override fun next(): E = backed.next().also { last = it }

        override fun nextIndex(): Int = backed.nextIndex()

        override fun previous(): E = backed.previous().also { last = it }

        override fun previousIndex(): Int = backed.previousIndex()

        override fun remove() {
            val value = last ?: throw IllegalStateException()
            backed.remove()
            accessor.postRemove(value)
            last = null
        }

        override fun set(element: E) {
            accessor.preAdd(element, owner, false)
            backed.set(element)
        }
    }
}

fun <E : Any, T : Any> OwnerAccessor<E, T>.preAdd(element: E, target: T, allowDuplicate: Boolean): Boolean {
    if (!trySet(element, target)) {
        require(allowDuplicate && check(element, target)) { "The value is already added to some other value" }
        return false
    }
    return true
}

fun <E : Any, T : Any> OwnerAccessor<E, T>.postRemove(element: E) {
    clear(element)
}

inline fun <E : Any, V : E?, T : Any> OwnerAccessor<E, T>.doSet(target: T, field: E?, value: V, setter: (V) -> Unit) {
    if (field === target) return
    if (value != null) preAdd(value, target, false)
    setter(value)
    if (field != null) clear(field)
}

fun <E : Any, V : E?, T : Any> OwnerAccessor<E, T>.preInit(target: T, value: V): V {
    if (value != null) preAdd(value, target, false)
    return value
}

abstract class OwnerAccessor<E : Any, T : Any> {
    abstract fun trySet(element: E, target: T): Boolean
    abstract fun check(element: E, target: T): Boolean
    abstract fun clear(element: E)
    abstract fun get(element: E): T
}
