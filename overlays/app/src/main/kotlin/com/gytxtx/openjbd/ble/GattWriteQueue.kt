package com.gytxtx.openjbd.ble

/**
 * Serializes GATT write requests and owns the "one outstanding operation" invariant.
 *
 * Android allows a single GATT operation per connection. A response notification can arrive
 * before [onWriteCallback], so finishing a command and finishing its GATT write are separate
 * events: the next write may only start once both have happened.
 *
 * A refused start (`writeCharacteristic` returned false / `ERROR_GATT_WRITE_REQUEST_BUSY`) means
 * the controller never transmitted the command, so retrying it cannot duplicate a write. Retries
 * are bounded and reinserted at the head of the queue so ordering is preserved. Once
 * [onWriteStarted] has been reported the request is never retried here: a lost response is
 * ambiguous and must be resolved by the caller's own retry policy.
 */
internal class GattWriteQueue<T>(private val maxStartRetries: Int = DEFAULT_MAX_START_RETRIES) {
    private data class Entry<T>(val item: T, val startFailures: Int = 0)

    private val queue = ArrayDeque<Entry<T>>()
    private var active: Entry<T>? = null
    private var writeStarted = false

    init {
        require(maxStartRetries >= 0) { "maxStartRetries must not be negative" }
    }

    val activeItem: T? get() = active?.item

    val isEmpty: Boolean get() = active == null && queue.isEmpty()

    val writeCallbackPending: Boolean get() = writeStarted

    fun enqueue(item: T) {
        queue.addLast(Entry(item))
    }

    /** Returns the next item to write, or null while a write or its callback is outstanding. */
    fun beginNext(): T? {
        if (writeStarted || active != null) return null
        val entry = queue.removeFirstOrNull() ?: return null
        active = entry
        return entry.item
    }

    fun onWriteStarted() {
        writeStarted = true
    }

    fun onWriteCallback() {
        writeStarted = false
    }

    /**
     * Records that the transport refused to start the active write. Requeues the same request at
     * the head while the retry budget lasts and returns true; returns false when it must fail.
     */
    fun retryStartFailure(): Boolean {
        val entry = active ?: return false
        if (writeStarted) return false
        active = null
        if (entry.startFailures >= maxStartRetries) return false
        queue.addFirst(entry.copy(startFailures = entry.startFailures + 1))
        return true
    }

    /** Clears the active request without retrying it and returns it. */
    fun completeActive(): T? {
        val entry = active ?: return null
        active = null
        return entry.item
    }

    /** Abandons all pending work and returns it in original order (active first). */
    fun clear(): List<T> {
        val items = buildList {
            active?.let { add(it.item) }
            queue.mapTo(this) { it.item }
        }
        active = null
        queue.clear()
        writeStarted = false
        return items
    }

    /**
     * Releases the in-flight marker when the platform never delivered `onCharacteristicWrite`.
     * Only a transport watchdog may call this: the write's fate is then unknown, so the caller
     * must treat the link as unusable rather than continuing the queue.
     */
    fun abandonLostWriteCallback() {
        writeStarted = false
    }

    companion object {
        const val DEFAULT_MAX_START_RETRIES = 2
    }
}
