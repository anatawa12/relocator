package com.anatawa12.relocator.internal

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.*
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function

// this ConcurrentIdentityHashMap is based on 
// ConcurrentHashMap implementation written by Doug Lea and can be downloaded from
// http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html
// which is released to the public domain.

/**
 * A HashMap which supports concurrent operations and 
 */
@Suppress("unused")
class ConcurrentIdentityHashMap<K : Any, V : Any> @JvmOverloads constructor(
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
    loadFactor: Float = DEFAULT_LOAD_FACTOR,
) : AbstractMutableMap<K, V>(), MutableMap<K, V>, Serializable {
    /*
    The basic strategy is an optimistic-style scheme based on
    the guarantee that the hash table and its lists are always
    kept in a consistent enough state to be read without locking:

    * Read operations first proceed without locking, by traversing the
       apparently correct list of the apparently correct bin. If an
       entry is found, but not invalidated (value field null), it is
       returned. If not found, operations must recheck (after a memory
       barrier) to make sure they are using both the right list and
       the right table (which can change under resizes). If
       invalidated, reads must acquire main update lock to wait out
       the update, and then re-traverse.

    * All list additions are at the front of each bin, making it easy
       to check changes, and also fast to traverse.  Entry next
       pointers are never assigned. Remove() builds new nodes when
       necessary to preserve this.

    * Remove() (also clear()) invalidates removed nodes to alert read
       operations that they must wait out the full modifications.

    * Locking for puts, removes (and, when necessary gets, etc)
      is controlled by Segments, each covering a portion of the
      table. During operations requiring global exclusivity (mainly
      resize and clear), ALL of these locks are acquired at once.
      Note that these segments are NOT contiguous -- they are based
      on the least 5 bits of hashcodes. This ensures that the same
      segment controls the same slots before and after resizing, which
      is necessary for supporting concurrent retrievals. This
      comes at the price of a mismatch of logical vs physical locality,
      but this seems not to be a performance problem in practice.
 
  */
    /**
     * The hash table data.
     */
    @Transient
    private var table: Array<Entry<K, V>?>

    /**
     * Bookkeeping for each concurrency control segment.
     * Each segment contains a local count of the number of
     * elements in its region.
     * However, the main use of a Segment is for its lock.
     */
    private class Segment : Serializable {
        /**
         * Get the count under synch.
         */
        /**
         * The number of elements in this segment's region.
         * It is always updated within synchronized blocks.
         */
        @get:Synchronized
        var count = 0

        /**
         * Force a synchronization
         */
        @Synchronized
        fun synch() {
        }
    }

    /**
     * The array of concurrency control segments.
     */
    private val segments = Array(CONCURRENCY_LEVEL) { Segment() }

    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    private val loadFactor: Float

    /**
     * Per-segment resize threshold.
     *
     * @serial
     */
    private var threshold = 0

    /**
     * Number of segments voting for resize. The table is
     * doubled when 1/4 of the segments reach threshold.
     * Volatile but updated without synch since this is just a heuristic.
     */
    @Volatile
    @Transient
    private var votesForResize = 0

    /**
     * Returns the appropriate capacity (power of two) for the specified
     * initial capacity argument.
     */
    private fun p2capacity(initialCapacity: Int): Int {

        // Compute the appropriate capacity
        var result: Int
        if (initialCapacity > MAXIMUM_CAPACITY || initialCapacity < 0) {
            result = MAXIMUM_CAPACITY
        } else {
            result = MINIMUM_CAPACITY
            while (result < initialCapacity) result = result shl 1
        }
        return result
    }

    /** Create table array and set the per-segment threshold  */
    private fun newTable(capacity: Int): Array<Entry<K, V>?> {
        threshold = (capacity * loadFactor / CONCURRENCY_LEVEL).toInt() + 1
        return arrayOfNulls(capacity)
    }

    /**
     * Constructs a new map with the same mappings as the given map.  The
     * map is created with a capacity of twice the number of mappings in
     * the given map or 32 (whichever is greater), and a default load factor.
     */
    constructor(t: Map<out K, V>) :
            this(((t.size / DEFAULT_LOAD_FACTOR).toInt() + 1).coerceAtLeast(MINIMUM_CAPACITY), DEFAULT_LOAD_FACTOR) {
        putAll(t)
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */
    override val size: Int
        get() {
            var c = 0
            for (i in segments.indices) c += segments[i].count
            return c
        }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings.
     */
    override fun isEmpty(): Boolean {
        for (i in segments.indices) if (segments[i].count != 0) return false
        return true
    }

    /**
     * Returns the value to which the specified key is mapped in this table.
     *
     * @param   key   a key in the table.
     * @return  the value to which the key is mapped in this table;
     * `null` if the key is not mapped to any value in
     * this table.
     * @exception  NullPointerException  if the key is
     * `null`.
     * @see .put
     */
    override fun get(key: K): V? {
        val hash = hash(key) // throws null pointer exception if key null

        // Try first without locking...
        var tab = table
        var index = hash and tab.size - 1
        val first = tab[index]
        var e: Entry<K, V>?
        e = first
        while (e != null) {
            if (e.hash == hash && key === e.key) {
                val value = e._value
                return value ?: break
            }
            e = e.next
        }

        // Recheck under synch if key apparently not there or interference
        val seg = segments[hash and SEGMENT_MASK]
        synchronized(seg) {
            tab = table
            index = hash and tab.size - 1
            val newFirst = tab[index]
            if (e != null || first !== newFirst) {
                e = newFirst
                while (e != null) {
                    if (e!!.hash == hash && key === e!!.key) return e!!._value
                    e = e!!.next
                }
            }
            return null
        }
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param   key   possible key.
     * @return  `true` if and only if the specified object
     * is a key in this table, as determined by the
     * <tt>equals</tt> method; `false` otherwise.
     * @exception  NullPointerException  if the key is
     * `null`.
     * @see .contains
     */
    override fun containsKey(key: K): Boolean = get(key) != null

    /**
     * Maps the specified `key` to the specified
     * `value` in this table. Neither the key nor the
     * value can be `null`. (Note that this policy is
     * the same as for java.util.Hashtable, but unlike java.util.HashMap,
     * which does accept nulls as valid keys and values.)
     *
     *
     *
     * The value can be retrieved by calling the `get` method
     * with a key that is equal to the original key.
     *
     * @param      key     the table key.
     * @param      value   the value.
     * @return     the previous value of the specified key in this table,
     * or `null` if it did not have one.
     * @exception  NullPointerException  if the key or value is
     * `null`.
     * @see Object.equals
     * @see .get
     */
    override fun put(key: K, value: V): V? {
        val hash = hash(key)
        val seg = segments[hash and SEGMENT_MASK]
        var segcount: Int
        var tab: Array<Entry<K, V>?>
        var votes: Int
        synchronized(seg) {
            tab = table
            val index = hash and tab.size - 1
            val first = tab[index]
            var e = first
            while (e != null) {
                if (e.hash == hash && key === e.key) {
                    val oldValue = e._value
                    e._value = value
                    return oldValue
                }
                e = e.next
            }

            //  Add to front of list
            val newEntry = Entry(hash, key, value, first)
            tab[index] = newEntry
            segcount = ++seg.count
            if (segcount < threshold) return null
            val bit = 1 shl (hash and SEGMENT_MASK)
            votes = votesForResize
            if (votes and bit == 0) {
                votesForResize = votesForResize or bit
                votes = votesForResize
            }
        }

        // Attempt resize if 1/4 segs vote,
        // or if this seg itself reaches the overall threshold.
        // (The latter check is just a safeguard to avoid pathological cases.)
        if (bitcount(votes) >= CONCURRENCY_LEVEL / 4 ||
            segcount > threshold * CONCURRENCY_LEVEL
        ) resize(0, tab)
        return null
    }

    /**
     * Gather all locks in order to call rehash, by
     * recursing within synch blocks for each segment index.
     * @param index the current segment. initially call value must be 0
     * @param assumedTab the state of table on first call to resize. If
     * this changes on any call, the attempt is aborted because the
     * table has already been resized by another thread.
     */
    private fun resize(index: Int, assumedTab: Array<Entry<K, V>?>) {
        val seg = segments[index]
        synchronized(seg) {
            if (assumedTab === table) {
                val next = index + 1
                if (next < segments.size) resize(next, assumedTab) else rehash()
            }
        }
    }

    /**
     * Rehashes the contents of this map into a new table
     * with a larger capacity.
     */
    private fun rehash() {
        votesForResize = 0 // reset
        val oldTable = table
        val oldCapacity = oldTable.size
        if (oldCapacity >= MAXIMUM_CAPACITY) {
            threshold = Int.MAX_VALUE // avoid retriggering
            return
        }
        val newCapacity = oldCapacity shl 1
        val newTable = newTable(newCapacity)
        val mask = newCapacity - 1

        /*
         * Reclassify nodes in each list to new Map.  Because we are
         * using power-of-two expansion, the elements from each bin
         * must either stay at same index, or move to
         * oldCapacity+index. We also eliminate unnecessary node
         * creation by catching cases where old nodes can be reused
         * because their next fields won't change. Statistically, at
         * the default threshhold, only about one-sixth of them need
         * cloning. (The nodes they replace will be garbage
         * collectable as soon as they are no longer referenced by any
         * reader thread that may be in the midst of traversing table
         * right now.)
         */
        for (i in 0 until oldCapacity) {
            // We need to guarantee that any existing reads of old Map can
            //  proceed. So we cannot yet null out each bin.  
            val e = oldTable[i]
            if (e != null) {
                val idx = e.hash and mask
                val next = e.next

                //  Single node on list
                if (next == null) newTable[idx] = e else {
                    // Reuse trailing consecutive sequence of all same bit
                    var lastRun: Entry<K, V> = e
                    var lastIdx = idx
                    var last = next
                    while (last != null) {
                        val k = last.hash and mask
                        if (k != lastIdx) {
                            lastIdx = k
                            lastRun = last
                        }
                        last = last.next
                    }
                    newTable[lastIdx] = lastRun

                    // Clone all remaining nodes
                    var p: Entry<K, V> = e
                    while (p !== lastRun) {
                        val k = p.hash and mask
                        newTable[k] = Entry(p.hash, p.key, p._value, newTable[k])
                        p = p.next!!
                    }
                }
            }
        }
        table = newTable
    }

    /**
     * Removes the key (and its corresponding value) from this
     * table. This method does nothing if the key is not in the table.
     *
     * @param   key   the key that needs to be removed.
     * @return  the value to which the key had been mapped in this table,
     * or `null` if the key did not have a mapping.
     * @exception  NullPointerException  if the key is
     * `null`.
     */
    override fun remove(key: K): V? {
        return remove1(key, null)
    }

    override fun remove(key: K, value: V): Boolean = remove1(key, value) != null    

    /**
     * Removes the (key, value) pair from this
     * table. This method does nothing if the key is not in the table,
     * or if the key is associated with a different value. This method
     * is needed by EntrySet.
     *
     * @param   key   the key that needs to be removed.
     * @param   value   the associated value. If the value is null,
     * it means "any value".
     * @return  the value to which the key had been mapped in this table,
     * or `null` if the key did not have a mapping.
     * @exception  NullPointerException  if the key is
     * `null`.
     */
    private fun remove1(key: K, value: V?): V? {
        /*
          Find the entry, then 
            1. Set value field to null, to force get() to retry
            2. Rebuild the list without this entry.
               All entries following removed node can stay in list, but
               all preceeding ones need to be cloned.  Traversals rely
               on this strategy to ensure that elements will not be
              repeated during iteration.
        */
        val hash = hash(key)
        val seg = segments[hash and SEGMENT_MASK]
        synchronized(seg) {
            val tab = table
            val index = hash and tab.size - 1
            val first = tab[index]
            var e = first
            while (true) {
                if (e == null) return null
                if (e.hash == hash && key === e.key) break
                e = e.next
            }
            first!!
            e!!
            val oldValue = e._value
            if (value != null && !(value === oldValue)) return null
            e._value = null
            var head = e.next
            var p: Entry<K, V> = first
            while (p !== e) {
                head = Entry(p.hash, p.key, p._value, head)
                p = p.next!!
            }
            tab[index] = head
            seg.count--
            return oldValue
        }
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than
     * method <tt>containsKey</tt>.
     *
     * @param value value whose presence in this map is to be tested.
     * @return <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     * @exception  NullPointerException  if the value is `null`.
     */
    override fun containsValue(value: V): Boolean {
        for (s in segments.indices) {
            val seg = segments[s]
            var tab: Array<Entry<K, V>?>
            synchronized(seg) { tab = table }
            var i = s
            while (i < tab.size) {
                var e = tab[i]
                while (e != null) {
                    if (value === e.value) return true
                    e = e.next
                }
                i += segments.size
            }
        }
        return false
    }

    /**
     * Tests if some key maps into the specified value in this table.
     * This operation is more expensive than the `containsKey`
     * method.
     *
     *
     *
     * Note that this method is identical in functionality to containsValue,
     * (which is part of the Map interface in the collections framework).
     *
     * @param      value   a value to search for.
     * @return     `true` if and only if some key maps to the
     * `value` argument in this table as
     * determined by the <tt>equals</tt> method;
     * `false` otherwise.
     * @exception  NullPointerException  if the value is `null`.
     * @see .containsKey
     * @see .containsValue
     * @see Map
     */
    operator fun contains(value: Any?): Boolean {
        return containsValue(value)
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     *
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified Map.
     *
     * @param from Mappings to be stored in this map.
     */
    override fun putAll(from: Map<out K, V>) {
        val n = from.size
        if (n == 0) return

        // Expand enough to hold at least n elements without resizing.
        // We can only resize table by factor of two at a time.
        // It is faster to rehash with fewer elements, so do it now.
        while (true) {
            var tab: Array<Entry<K, V>?>
            var max: Int
            synchronized(segments[0]) {
                // must synch on some segment. pick 0.
                tab = table
                max = threshold * CONCURRENCY_LEVEL
            }
            if (n < max) break
            resize(0, tab)
        }
        val it = from.entries.iterator()
        while (it.hasNext()) {
            val (key, value) = it.next()
            put(key, value)
        }
    }

    /**
     * Removes all mappings from this map.
     */
    override fun clear() {
        // We don't need all locks at once so long as locks
        //   are obtained in low to high order
        for (s in segments.indices) {
            val seg = segments[s]
            synchronized(seg) {
                val tab = table
                var i = s
                while (i < tab.size) {
                    var e = tab[i]
                    while (e != null) {
                        e._value = null
                        e = e.next
                    }
                    tab[i] = null
                    seg.count = 0
                    i += segments.size
                }
            }
        }
    }

    init {
        require(loadFactor > 0) {
            "Illegal Load factor: " +
                    loadFactor
        }
        this.loadFactor = loadFactor
        val cap = p2capacity(initialCapacity)
        table = newTable(cap)
    }

    /**
     * Returns a set view of the keys contained in this map.  The set is
     * backed by the map, so changes to the map are reflected in the set, and
     * vice-versa.  The set supports element removal, which removes the
     * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
     * <tt>clear</tt> operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * @return a set view of the keys contained in this map.
     */
    override val keys: MutableSet<K> by lazy(LazyThreadSafetyMode.NONE) { KeySet() }

    private inner class KeySet : AbstractSet<K>() {
        override fun iterator(): MutableIterator<K> = KeyIterator()
        override val size: Int get() = this@ConcurrentIdentityHashMap.size
        override operator fun contains(element: K): Boolean = this@ConcurrentIdentityHashMap.containsKey(element)
        override fun remove(element: K): Boolean = this@ConcurrentIdentityHashMap.remove(element) != null
        override fun clear() = this@ConcurrentIdentityHashMap.clear()
    }

    /**
     * Returns a collection view of the values contained in this map.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from this map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the values contained in this map.
     */
    override val values: MutableCollection<V> by lazy(LazyThreadSafetyMode.NONE) { Values() }

    private inner class Values : AbstractCollection<V>() {
        override fun iterator(): MutableIterator<V> = ValueIterator()
        override val size: Int get() = this@ConcurrentIdentityHashMap.size
        override fun contains(element: V): Boolean = this@ConcurrentIdentityHashMap.containsValue(element)
        override fun clear() = this@ConcurrentIdentityHashMap.clear()
    }

    /**
     * Returns a collection view of the mappings contained in this map.  Each
     * element in the returned collection is a <tt>Map.Entry</tt>.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the mappings contained in this map.
     */
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy(LazyThreadSafetyMode.NONE) { EntrySet() }

    private inner class EntrySet : AbstractSet<MutableMap.MutableEntry<K, V>>() {
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return EntryIterator()
        }

        override operator fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            val v = this@ConcurrentIdentityHashMap[element.key]
            return v != null && v === element.value
        }

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
            this@ConcurrentIdentityHashMap.remove(element.key, element.value)
        override val size: Int get() = this@ConcurrentIdentityHashMap.size
        override fun clear() = this@ConcurrentIdentityHashMap.clear()
    }


    // Overrides of JDK8 functions as Unsupported
    private fun noJdk8(): Nothing = throw UnsupportedOperationException("jdk8 operations are not supported")
    override fun getOrDefault(key: K, defaultValue: V): V = noJdk8()
    override fun forEach(action: BiConsumer<in K, in V>) = noJdk8()
    override fun replaceAll(function: BiFunction<in K, in V, out V>) = noJdk8()
    override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V = noJdk8()
    @Suppress("RedundantNullableReturnType")
    override fun computeIfPresent(key: K, remappingFunction: BiFunction<in K, in V, out V?>): V? = noJdk8()
    @Suppress("RedundantNullableReturnType")
    override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? = noJdk8()
    @Suppress("RedundantNullableReturnType")
    override fun merge(key: K, value: V, remappingFunction: BiFunction<in V, in V, out V?>): V? = noJdk8()

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return  an enumeration of the keys in this table.
     * @see Enumeration
     *
     * @see .elements
     * @see .keySet
     * @see Map
     */
    fun keys(): Enumeration<*> = KeyIterator()

    /**
     * Returns an enumeration of the values in this table.
     * Use the Enumeration methods on the returned object to fetch the elements
     * sequentially.
     *
     * @return  an enumeration of the values in this table.
     * @see java.util.Enumeration
     *
     * @see .keys
     * @see .values
     * @see Map
     */
    fun elements(): Enumeration<*> = ValueIterator()

    /**
     * ConcurrentHashMap collision list entry.
     */
    private class Entry<K: Any, V: Any>(
        val hash: Int, // Map.Entry Ops 
        /* 
                The use of volatile for value field ensures that
                we can detect status changes without synchronization.
                The other fields are never changed, and are
                marked as final. 
             */
        override val key: K,
        /**
         * Get the value.  Note: In an entrySet or entrySet.iterator,
         * unless you can guarantee lack of concurrent modification,
         * <tt>getValue</tt> *might* return null, reflecting the
         * fact that the entry has been concurrently removed. However,
         * there are no assurances that concurrent removals will be
         * reflected using this method.
         *
         * @return     the current value, or null if the entry has been
         * detectably removed.
         */
        @field:Volatile var _value: V?,
        val next: Entry<K, V>?,
    ) : MutableMap.MutableEntry<K, V> {
        override val value: V get() = _value ?: error("removed entry")

        /**
         * Set the value of this entry.  Note: In an entrySet or
         * entrySet.iterator), unless you can guarantee lack of concurrent
         * modification, <tt>setValue</tt> is not strictly guaranteed to
         * actually replace the value field obtained via the <tt>get</tt>
         * operation of the underlying hash table in multithreaded
         * applications.  If iterator-wide synchronization is not used,
         * and any other concurrent <tt>put</tt> or <tt>remove</tt>
         * operations occur, sometimes even to *other* entries,
         * then this change is not guaranteed to be reflected in the hash
         * table. (It might, or it might not. There are no assurances
         * either way.)
         *
         * @param      newValue   the new value.
         * @return     the previous value, or null if entry has been detectably
         * removed.
         * @exception  NullPointerException  if the value is `null`.
         */
        override fun setValue(newValue: V): V {
            val oldValue = this.value
            this._value = newValue
            return oldValue
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            val (key1, value1) = other
            return key === key1 && _value === value1
        }

        override fun hashCode(): Int {
            return key.hashCode() xor _value.hashCode()
        }

        override fun toString(): String {
            return "$key=$_value"
        }
    }

    private abstract inner class HashIterator<Value> protected constructor() : MutableIterator<Value>, Enumeration<Value> {
        protected val tab: Array<Entry<K, V>?> // snapshot of table
        protected var index: Int // current slot
        protected var entry: Entry<K, V>? = null // current node of slot
        protected var currentKey: K? = null // key for current node
        protected var currentValue: V? = null // value for current node
        protected var lastReturned: Entry<K, V>? = null // last node returned by next

        init {
            // force all segments to synch
            synchronized(segments[0]) { tab = table }
            for (i in 1 until segments.size) segments[i].synch()
            index = tab.size - 1
        }

        override fun hasMoreElements(): Boolean {
            return hasNext()
        }

        override fun nextElement(): Value {
            return next()
        }

        override fun hasNext(): Boolean {
            /*
              currentkey and currentValue are set here to ensure that next()
              returns normally if hasNext() returns true. This avoids
              surprises especially when final element is removed during
              traversal -- instead, we just ignore the removal during
              current traversal.  
            */
            while (true) {
                if (entry != null) {
                    val v = entry!!._value
                    if (v != null) {
                        currentKey = entry!!.key
                        currentValue = v
                        return true
                    } else entry = entry!!.next
                }
                while (entry == null && index >= 0) entry = tab[index--]
                if (entry == null) {
                    currentValue = null
                    currentKey = currentValue
                    return false
                }
            }
        }

        protected abstract fun returnValueOfNext(): Value

        override fun next(): Value {
            if (currentKey == null && !hasNext()) throw NoSuchElementException()
            val result = returnValueOfNext()
            val entry = entry
            lastReturned = entry
            currentValue = null
            currentKey = null
            this.entry = entry!!.next
            return result
        }

        override fun remove() {
            checkNotNull(lastReturned)
            this@ConcurrentIdentityHashMap.remove(lastReturned!!.key)
            lastReturned = null
        }
    }

    private inner class EntryIterator : HashIterator<Entry<K, V>>() {
        override fun returnValueOfNext(): Entry<K, V> = entry!!
    }

    private inner class KeyIterator : HashIterator<K>() {
        override fun returnValueOfNext(): K = currentKey!!
    }

    private inner class ValueIterator : HashIterator<V>() {
        override fun returnValueOfNext(): V = currentValue!!
    }

    /**
     * Save the state of the <tt>ConcurrentHashMap</tt>
     * instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData
     * An estimate of the table size, followed by
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    @Throws(IOException::class)
    private fun writeObject(s: ObjectOutputStream) {
        // Write out the loadfactor, and any hidden stuff
        s.defaultWriteObject()

        // Write out capacity estimate. It is OK if this
        // changes during the write, since it is only used by
        // readObject to set initial capacity, to avoid needless resizings.
        var cap: Int
        synchronized(segments[0]) { cap = table.size }
        s.writeInt(cap)

        // Write out keys and values (alternating)
        for (k in segments.indices) {
            val seg = segments[k]
            var tab: Array<Entry<K, V>?>
            synchronized(seg) { tab = table }
            var i = k
            while (i < tab.size) {
                var e = tab[i]
                while (e != null) {
                    s.writeObject(e.key)
                    s.writeObject(e._value)
                    e = e.next
                }
                i += segments.size
            }
        }
        s.writeObject(null)
        s.writeObject(null)
    }

    /**
     * Reconstitute the <tt>ConcurrentHashMap</tt>
     * instance from a stream (i.e.,
     * deserialize it).
     */
    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(s: ObjectInputStream) {
        // Read in the threshold, loadfactor, and any hidden stuff
        s.defaultReadObject()
        val cap = s.readInt()
        table = newTable(cap)
        for (i in segments.indices) segments[i] = Segment()


        // Read the keys and values, and put the mappings in the table
        while (true) {
            val key = s.readObject()
            val value = s.readObject()
            if (key == null) break
            @Suppress("UNCHECKED_CAST")
            put(key as K, value as V)
        }
    }

    companion object {
        /**
         * The number of concurrency control segments.
         * The value can be at most 32 since ints are used
         * as bitsets over segments. Emprically, it doesn't
         * seem to pay to decrease it either, so the value should be at least 32.
         * In other words, do not redefine this :-)
         */
        private const val CONCURRENCY_LEVEL = 32

        /**
         * Mask value for indexing into segments
         */
        private const val SEGMENT_MASK = CONCURRENCY_LEVEL - 1

        /**
         * The default initial number of table slots for this table (32).
         * Used when not otherwise specified in constructor.
         */
        private const val DEFAULT_INITIAL_CAPACITY = 32

        /**
         * The minimum capacity, used if a lower value is implicitly specified
         * by either of the constructors with arguments.
         * MUST be a power of two.
         */
        private const val MINIMUM_CAPACITY = 32

        /**
         * The maximum capacity, used if a higher value is implicitly specified
         * by either of the constructors with arguments.
         * MUST be a power of two <= 1<<30.
         */
        private const val MAXIMUM_CAPACITY = 1 shl 30

        /**
         * The default load factor for this table (0.75)
         * Used when not otherwise specified in constructor.
         */
        const val DEFAULT_LOAD_FACTOR = 0.75f

        /**
         * Return the number of set bits in w.
         * For a derivation of this algorithm, see
         * "Algorithms and data structures with applications to
         * graphics and geometry", by Jurg Nievergelt and Klaus Hinrichs,
         * Prentice Hall, 1993.
         * See also notes by Torsten Sillke at
         * http://www.mathematik.uni-bielefeld.de/~sillke/PROBLEMS/bitcount
         */
        @JvmStatic
        private fun bitcount(w: Int): Int {
            @Suppress("NAME_SHADOWING")
            var w = w
            w -= -0x55555556 and w ushr 1
            w = (w and 0x33333333) + (w ushr 2 and 0x33333333)
            w = w + (w ushr 4) and 0x0f0f0f0f
            w += w ushr 8
            w += w ushr 16
            return w and 0xff
        }

        /**
         * Return hash code for Object x. Since we are using power-of-two
         * tables, it is worth the effort to improve hashcode via
         * the same multiplicative scheme as used in IdentityHashMap.
         */
        @JvmStatic
        private fun hash(x: Any): Int {
            val h = x.hashCode()
            // Multiply by 127 (quickly, via shifts), and mix in some high
            // bits to help guard against bunching of codes that are
            // consecutive or equally spaced.
            return (h shl 7) - h + (h ushr 9) + (h ushr 17)
        }
    }
}
