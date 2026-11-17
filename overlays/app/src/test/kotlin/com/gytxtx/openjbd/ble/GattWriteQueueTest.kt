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

    private class Request(val name: String) {
        var cancelled = false
    }

    @Test
    fun cancelledPendingRequestIsRemovedAndNeverStarted() {
        val queue = GattWriteQueue<Request>()
        val first = Request("first")
        val cancelled = Request("cancelled")
        val third = Request("third")
        listOf(first, cancelled, third).forEach(queue::enqueue)

        assertEquals(first, queue.beginNext())
        assertTrue(queue.cancelPending(cancelled))
        queue.completeActive()

        assertEquals(third, queue.beginNext())
        queue.completeActive()
        assertNull(queue.beginNext())
        assertTrue(queue.isEmpty)
    }

    @Test
    fun activeRequestIsNotCancelledByCancelPending() {
        val queue = GattWriteQueue<Request>()
        val active = Request("active")
        queue.enqueue(active)

        assertEquals(active, queue.beginNext())
        queue.onWriteStarted()

        assertFalse(queue.cancelPending(active))
        assertEquals(active, queue.activeItem)
    }

    @Test
    fun beginNextSkipsRequestsFlaggedCancelledBeforeStart() {
        val queue = GattWriteQueue<Request>()
        val cancelled = Request("cancelled").apply { this.cancelled = true }
        val next = Request("next")
        queue.enqueue(cancelled)
        queue.enqueue(next)

        assertEquals(next, queue.beginNext { it.cancelled })
        queue.completeActive()
        assertNull(queue.beginNext { it.cancelled })
    }

    @Test
    fun startRefusedRequestCancelledBeforeRetryIsNotRetried() {
        val queue = GattWriteQueue<Request>()
        val request = Request("refused")
        queue.enqueue(request)

        assertEquals(request, queue.beginNext { it.cancelled })
        assertTrue(queue.retryStartFailure())
        request.cancelled = true

        assertNull(queue.beginNext { it.cancelled })
        assertTrue(queue.isEmpty)
    }
}
