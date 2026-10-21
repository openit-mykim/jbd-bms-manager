package com.gytxtx.openjbd.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GattWriteQueueTest {
    @Test
    fun startsOneWriteAtATimeInOrder() {
        val queue = GattWriteQueue<String>()
        queue.enqueue("first")
        queue.enqueue("second")

        assertEquals("first", queue.beginNext())
        assertNull(queue.beginNext())

        assertEquals("first", queue.completeActive())
        assertEquals("second", queue.beginNext())
    }

    @Test
    fun responseBeforeWriteCallbackDoesNotReleaseNextWrite() {
        val queue = GattWriteQueue<String>()
        queue.enqueue("first")
        queue.enqueue("second")

        assertEquals("first", queue.beginNext())
        queue.onWriteStarted()
        assertEquals("first", queue.completeActive())

        assertTrue(queue.writeCallbackPending)
        assertNull(queue.beginNext())

        queue.onWriteCallback()

        assertFalse(queue.writeCallbackPending)
        assertEquals("second", queue.beginNext())
    }

    @Test
    fun completeBeforeWriteCallbackLeavesCallbackMarkerPending() {
        val queue = GattWriteQueue<String>()
        queue.enqueue("first")
        queue.enqueue("second")

        queue.beginNext()
        queue.onWriteStarted()
        queue.completeActive()

        assertTrue(queue.writeCallbackPending)
        assertNull(queue.beginNext())
        assertFalse(queue.isEmpty)
    }

    @Test
    fun refusedStartRetriesAtHeadWithoutReordering() {
        val queue = GattWriteQueue<String>(maxStartRetries = 2)
        queue.enqueue("first")
        queue.enqueue("second")

        assertEquals("first", queue.beginNext())
        assertTrue(queue.retryStartFailure())

        assertEquals("first", queue.beginNext())
        assertEquals("first", queue.completeActive())
        assertEquals("second", queue.beginNext())
    }

    @Test
    fun refusedStartStopsAfterRetryBudget() {
        val queue = GattWriteQueue<String>(maxStartRetries = 1)
        queue.enqueue("only")

        assertEquals("only", queue.beginNext())
        assertTrue(queue.retryStartFailure())
        assertEquals("only", queue.beginNext())
        assertFalse(queue.retryStartFailure())

        assertNull(queue.beginNext())
        assertTrue(queue.isEmpty)
    }

    @Test
    fun retryIsRefusedOnceWriteStarted() {
        val queue = GattWriteQueue<String>()
        queue.enqueue("only")

        assertEquals("only", queue.beginNext())
        queue.onWriteStarted()

        assertFalse(queue.retryStartFailure())
        assertEquals("only", queue.activeItem)
    }

    @Test
    fun clearReturnsActiveThenQueuedInOrderAndResetsCallbackState() {
        val queue = GattWriteQueue<String>()
        queue.enqueue("first")
        queue.enqueue("second")
        queue.enqueue("third")

        assertEquals("first", queue.beginNext())
        queue.onWriteStarted()

        assertEquals(listOf("first", "second", "third"), queue.clear())
        assertTrue(queue.isEmpty)
        assertFalse(queue.writeCallbackPending)
        assertNull(queue.beginNext())
    }

    @Test
    fun lostWriteCallbackCanBeAbandonedByWatchdog() {
        val queue = GattWriteQueue<String>()
        queue.enqueue("only")

        assertEquals("only", queue.beginNext())
        queue.onWriteStarted()

        queue.abandonLostWriteCallback()

        assertFalse(queue.writeCallbackPending)
        assertEquals("only", queue.completeActive())
        assertTrue(queue.isEmpty)
    }
}
