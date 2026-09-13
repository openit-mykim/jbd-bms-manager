package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.protocol.JbdFrame
import java.util.ArrayDeque
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun readGroupsPopulatesPhysicalValuesUnitsAccessModeAndTimestamp() = runTest {
        val host = FakeSettingsCommandHost(
            outcomes = arrayOf(
                ack(0x00),
                register(0x2A, 3_500),
                register(0x2B, 15),
                register(0x2D, 0b1100),
                ack(0x01)
            )
        )
        val repository = repository(host, clock = { 12_345L })

        repository.readGroups(listOf(SettingsGroup.BALANCE))
        advanceUntilIdle()

        val state = repository.state.value
        val balance = state.sections.getValue(SettingsGroup.BALANCE)
        assertEquals(3_500.0, balance.values.getValue(SettingField.BAL_START).physicalValue, 0.0)
        assertEquals("mV", SettingField.BAL_START.register.unit)
        assertEquals(15.0, balance.values.getValue(SettingField.BAL_WINDOW).physicalValue, 0.0)
        assertEquals(1, balance.values.getValue(SettingField.BALANCE_ENABLE).raw)
        assertEquals(SettingsAccessMode.FACTORY, balance.accessMode)
        assertEquals(12_345L, balance.lastUpdatedAtMillis)
        assertFalse(balance.loading)
        assertFalse(state.notConnected)
        assertEquals(null, state.lastError)
        assertEquals(listOf("begin", "end"), host.events)
    }

    @Test
    fun disconnectedReadSetsErrorAndSendsNoFrames() = runTest {
        val host = FakeSettingsCommandHost(connected = false)
        val repository = repository(host)

        repository.readGroups(listOf(SettingsGroup.BALANCE))
        advanceUntilIdle()

        assertTrue(repository.state.value.notConnected)
        assertEquals("not connected", repository.state.value.lastError)
        assertTrue(host.frames.isEmpty())
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun repeatedReadWhileRunningIsIgnored() = runTest {
        val host = FakeSettingsCommandHost(
            outcomes = arrayOf(
                ack(0x00),
                register(0x2A, 3_500),
                register(0x2B, 15),
                register(0x2D, 0),
                ack(0x01)
            )
        )
        val repository = repository(host)

        repository.readGroups(listOf(SettingsGroup.BALANCE))
        repository.readGroups(listOf(SettingsGroup.BALANCE))
        advanceUntilIdle()

        assertEquals(5, host.frames.size)
        assertEquals(listOf("begin", "end"), host.events)
    }

    @Test
    fun directFallbackStateRetainsAccessWarning() = runTest {
        val host = FakeSettingsCommandHost(
            outcomes = arrayOf(
                // Factory entry is attempted twice (one retry) before the direct fallback.
                GatewayOutcome.NoResponse,
                GatewayOutcome.NoResponse,
                register(0x2A, 3_500),
                register(0x2B, 15),
                register(0x2D, 0)
            )
        )
        val repository = repository(host)

        repository.readGroups(listOf(SettingsGroup.BALANCE))
        advanceUntilIdle()

        val section = repository.state.value.sections.getValue(SettingsGroup.BALANCE)
        assertEquals(SettingsAccessMode.DIRECT, section.accessMode)
        assertTrue(
            SettingsSessionWarning.FACTORY_ENTRY_FAILED_DIRECT_FALLBACK in section.warnings
        )
    }

    @Test
    fun applyChangesWrapsSessionAndRecordsCommittedResult() = runTest {
        val host = FakeSettingsCommandHost(
            outcomes = arrayOf(
                ack(0x00),
                register(0x24, 4_200),
                ack(0x24),
                register(0x24, 4_100),
                counterResponse(IntArray(11)),
                ack(0x01),
                // Post-commit confirmation session: re-enter, re-read changed register, exit.
                ack(0x00),
                register(0x24, 4_100),
                ack(0x01)
            )
        )
        val repository = repository(host)

        val result = repository.applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.Committed)
        assertTrue(result.postCommitConfirmation?.mismatched?.isEmpty() == true)
        assertSame(result, repository.state.value.lastWriteResult)
        assertEquals(listOf("begin", "end"), host.events)
        assertEquals(9, host.frames.size)
        assertEquals(null, repository.state.value.lastError)
    }

    @Test
    fun disconnectedApplyRecordsFailureAndSendsNoFrames() = runTest {
        val host = FakeSettingsCommandHost(connected = false)
        val repository = repository(host)

        val result = repository.applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.EntryFailed)
        assertSame(result, repository.state.value.lastWriteResult)
        assertTrue(repository.state.value.notConnected)
        assertTrue(host.frames.isEmpty())
        assertTrue(host.events.isEmpty())
    }

    private fun TestScope.repository(
        host: FakeSettingsCommandHost,
        clock: () -> Long = { 1L }
    ): SettingsRepository = SettingsRepository(
        BleSettingsGateway(host),
        clock,
        StandardTestDispatcher(testScheduler)
    )

    private class FakeSettingsCommandHost(
        private val connected: Boolean = true,
        outcomes: Array<GatewayOutcome> = emptyArray()
    ) : SettingsCommandHost {
        private val outcomes = ArrayDeque(outcomes.toList())
        val frames = mutableListOf<ByteArray>()
        val events = mutableListOf<String>()

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
            frames += frame.copyOf()
            check(timeoutMs > 0)
            check(outcomes.isNotEmpty()) { "No outcome for address $responseAddress" }
            callback(outcomes.removeFirst())
        }
    }

    companion object {
        private fun ack(address: Int): GatewayOutcome =
            GatewayOutcome.Response(response(address, byteArrayOf()))

        private fun register(address: Int, raw: Int): GatewayOutcome =
            GatewayOutcome.Response(response(address, u16(raw)))

        private fun counterResponse(counters: IntArray): GatewayOutcome {
            require(counters.size == 11)
            val payload = counters.flatMap { u16(it).asIterable() }.toByteArray()
            return GatewayOutcome.Response(response(0xAA, payload))
        }

        private fun u16(value: Int): ByteArray =
            byteArrayOf((value ushr 8).toByte(), value.toByte())

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
