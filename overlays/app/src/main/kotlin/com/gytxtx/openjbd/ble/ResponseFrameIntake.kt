package com.gytxtx.openjbd.ble

import com.gytxtx.openjbd.protocol.JbdFrame
import com.gytxtx.openjbd.protocol.JbdFrameAssembler

/**
 * Thread-safe gate between BLE notifications and command completion (issue #30).
 *
 * Notifications are assembled on a binder thread, while connection resets and command timeouts
 * happen on the main thread. Every assembled [Batch] carries the **generation** it was assembled
 * in; the main thread processes a batch only while [isCurrent] still holds. The generation moves
 * forward on:
 *
 * - [reset] — every connect / disconnect, so frames from a previous connection (already posted to
 *   the main looper, or half-assembled in the buffer) can never complete a command of the next
 *   connection;
 * - [quarantine] — every command timeout. A response that arrives after its command timed out
 *   is *stale*: it must not be consumed as the answer to the retry or to the next command (for
 *   example a late register read taken as a write acknowledgement). Quarantine drops the partial
 *   buffer and any batch already in flight, then discards every notification received during
 *   the quarantine window. The command queue must not start the next command until
 *   [quarantineRemainingMs] reaches zero.
 *
 * Late frames that arrive after the window are still possible; response-shape checks in the
 * settings coordinator are the second line of defence and fail safe (never commit).
 */
internal class ResponseFrameIntake(
    private val clockMs: () -> Long,
    private val assembler: JbdFrameAssembler = JbdFrameAssembler()
) {
    data class Batch(val generation: Long, val frames: List<JbdFrame>)

    private val lock = Any()
    private var generation = 0L
    private var quarantineUntilMs = Long.MIN_VALUE

    /**
     * Called from the notification (binder) thread. [fromCurrentLink] is evaluated under the
     * same lock as [reset]; the caller clears its link reference *before* calling [reset], so a
     * chunk from a closing link is either rejected here or tagged with the old generation.
     */
    fun append(chunk: ByteArray, fromCurrentLink: () -> Boolean = { true }): Batch = synchronized(lock) {
        if (!fromCurrentLink()) return Batch(generation, emptyList())
        if (clockMs() < quarantineUntilMs) {
            assembler.reset()
            return Batch(generation, emptyList())
        }
        Batch(generation, assembler.append(chunk))
    }

    /** True while no reset or quarantine happened after [batch] was assembled. */
    fun isCurrent(batch: Batch): Boolean = synchronized(lock) { batch.generation == generation }

    /** New connection or disconnect: forget everything received so far. */
    fun reset() = synchronized(lock) {
        generation += 1
        quarantineUntilMs = Long.MIN_VALUE
        assembler.reset()
    }

    /** A command timed out: treat everything received in the next [durationMs] as stale. */
    fun quarantine(durationMs: Long) = synchronized(lock) {
        require(durationMs >= 0) { "durationMs must not be negative" }
        generation += 1
        quarantineUntilMs = clockMs() + durationMs
        assembler.reset()
    }

    fun quarantineRemainingMs(): Long = synchronized(lock) {
        if (quarantineUntilMs == Long.MIN_VALUE) 0L else maxOf(0L, quarantineUntilMs - clockMs())
    }
}
