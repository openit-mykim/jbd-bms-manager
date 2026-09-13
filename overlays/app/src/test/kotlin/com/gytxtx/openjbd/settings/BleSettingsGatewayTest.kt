package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.protocol.JbdFrame
import java.util.ArrayDeque
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BleSettingsGatewayTest {
    @Test
    fun responseOutcomeIsReturned() = runTest {
        val host = FakeSettingsCommandHost()
        val gateway = BleSettingsGateway(host)
        val expected = GatewayOutcome.Response(response(0x24, byteArrayOf(0x10, 0x68)))

        val result = async { gateway.execute(byteArrayOf(1), 0x24, 500) }
        runCurrent()
        host.completeNext(expected)

        assertSame(expected, result.await())
        assertArrayEquals(byteArrayOf(1), host.requests.single().frame)
        assertEquals(0x24, host.requests.single().responseAddress)
        assertEquals(500L, host.requests.single().timeoutMs)
    }

    @Test
    fun noResponseOutcomeIsReturned() = runTest {
        val host = FakeSettingsCommandHost()
        val result = async {
            BleSettingsGateway(host).execute(byteArrayOf(2), 0x25, 600)
        }
        runCurrent()

        host.completeNext(GatewayOutcome.NoResponse)

        assertSame(GatewayOutcome.NoResponse, result.await())
    }

    @Test
    fun transportFailureOutcomeIsReturned() = runTest {
        val host = FakeSettingsCommandHost()
        val expected = GatewayOutcome.TransportFailure("write failed")
        val result = async {
            BleSettingsGateway(host).execute(byteArrayOf(3), 0x26, 700)
        }
        runCurrent()

        host.completeNext(expected)

        assertEquals(expected, result.await())
    }

    @Test
    fun disconnectedExecuteShortCircuitsWithoutEnqueue() = runTest {
        val host = FakeSettingsCommandHost(connected = false)

        val result = BleSettingsGateway(host).execute(byteArrayOf(4), 0x27, 800)

        assertEquals(GatewayOutcome.TransportFailure("not connected"), result)
        assertTrue(host.requests.isEmpty())
    }

    @Test
    fun concurrentExecuteCallsStayInInvocationOrder() = runTest {
        val host = FakeSettingsCommandHost()
        val gateway = BleSettingsGateway(host)
        val first = async { gateway.execute(byteArrayOf(1), 0x20, 1_000) }
        runCurrent()
        val second = async { gateway.execute(byteArrayOf(2), 0x21, 1_000) }
        runCurrent()

        assertEquals(1, host.requests.size)
        assertArrayEquals(byteArrayOf(1), host.requests[0].frame)

        host.completeNext(GatewayOutcome.NoResponse)
        runCurrent()
        assertEquals(2, host.requests.size)
        assertArrayEquals(byteArrayOf(2), host.requests[1].frame)
        host.completeNext(GatewayOutcome.NoResponse)

        assertSame(GatewayOutcome.NoResponse, first.await())
        assertSame(GatewayOutcome.NoResponse, second.await())
    }

    @Test
    fun withSessionBeginsAndEndsAroundSuccessfulBlock() = runTest {
        val host = FakeSettingsCommandHost()

        val result = BleSettingsGateway(host).withSession {
            host.events += "block"
            42
        }

        assertEquals(42, result)
        assertEquals(listOf("begin", "block", "end"), host.events)
    }

    @Test
    fun withSessionEndsWhenBlockThrows() = runTest {
        val host = FakeSettingsCommandHost()

        try {
            BleSettingsGateway(host).withSession<Unit> {
                host.events += "block"
                throw TestFailure()
            }
            fail("Expected TestFailure")
        } catch (_: TestFailure) {
            // Expected.
        }

        assertEquals(listOf("begin", "block", "end"), host.events)
    }

    @Test
    fun withSessionEndsWhenBlockIsCancelled() = runTest {
        val host = FakeSettingsCommandHost()
        val gateway = BleSettingsGateway(host)
        val job = launch {
            gateway.withSession {
                gateway.execute(byteArrayOf(5), 0x28, 1_000)
            }
        }
        runCurrent()

        job.cancelAndJoin()

        assertEquals(listOf("begin", "end"), host.events)
    }

    @Test
    fun cancelledExecuteIgnoresLateCallbackAndAllowsNextCommand() = runTest {
        val host = FakeSettingsCommandHost()
        val gateway = BleSettingsGateway(host)
        val cancelled = launch {
            gateway.execute(byteArrayOf(6), 0x29, 1_000)
        }
        runCurrent()
        cancelled.cancelAndJoin()

        host.completeNext(GatewayOutcome.NoResponse)
        val next = async { gateway.execute(byteArrayOf(7), 0x2A, 1_000) }
        runCurrent()
        host.completeNext(GatewayOutcome.TransportFailure("later failure"))

        assertTrue(cancelled.isCancelled)
        assertEquals(GatewayOutcome.TransportFailure("later failure"), next.await())
    }

    private class FakeSettingsCommandHost(
        private var connected: Boolean = true
    ) : SettingsCommandHost {
        val requests = mutableListOf<Request>()
        val events = mutableListOf<String>()
        private val callbacks = ArrayDeque<(GatewayOutcome) -> Unit>()

        override fun isConnected(): Boolean = connected

        override fun beginSettingsSession() {
            events += "begin"
        }

        override fun endSettingsSession() {
            events += "end"
        }

        override fun enqueueSettingsCommand(
            frame: ByteArray,
            responseAddress: Int,
            timeoutMs: Long,
            callback: (GatewayOutcome) -> Unit
        ) {
            requests += Request(frame.copyOf(), responseAddress, timeoutMs)
            callbacks.addLast(callback)
        }

        fun completeNext(outcome: GatewayOutcome) {
            callbacks.removeFirst().invoke(outcome)
        }
    }

    private data class Request(
        val frame: ByteArray,
        val responseAddress: Int,
        val timeoutMs: Long
    )

    private class TestFailure : RuntimeException()

    companion object {
        private fun response(address: Int, payload: ByteArray): JbdFrame {
            val status = 0
            val length = payload.size
            val checksum = (-(status + length + payload.sumOf { it.toInt() and 0xFF })) and 0xFFFF
            return JbdFrame.parse(
                byteArrayOf(0xDD.toByte(), address.toByte(), status.toByte(), length.toByte()) +
                    payload +
                    byteArrayOf((checksum ushr 8).toByte(), checksum.toByte(), 0x77)
            )
        }
    }
}
