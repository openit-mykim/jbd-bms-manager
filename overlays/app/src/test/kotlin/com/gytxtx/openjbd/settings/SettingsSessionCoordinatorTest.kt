package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.protocol.JbdCommands
import com.gytxtx.openjbd.protocol.JbdConfigCommands
import com.gytxtx.openjbd.protocol.JbdFrame
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class SettingsSessionCoordinatorTest {
    @Test
    fun readHappyPathDeduplicatesRegistersAndNeverCommits() = runTest {
        val gateway = FakeGateway(
            ack(0x00),
            register(0x24, 4_200),
            register(0x2D, 0b1100),
            ack(0x01)
        )
        val coordinator = SettingsSessionCoordinator(gateway)

        val result = coordinator.readFields(
            listOf(
                SettingField.COVP,
                SettingField.BALANCE_ENABLE,
                SettingField.CHARGE_BALANCE_ENABLE
            )
        )

        assertEquals(SettingsAccessMode.FACTORY, result.accessMode)
        assertEquals(4_200, result.values.getValue(SettingField.COVP).raw)
        assertEquals(1, result.values.getValue(SettingField.BALANCE_ENABLE).raw)
        assertEquals(1, result.values.getValue(SettingField.CHARGE_BALANCE_ENABLE).raw)
        assertFrames(
            gateway,
            JbdCommands.openFactoryMode(),
            JbdConfigCommands.readRegister(0x24),
            JbdConfigCommands.readRegister(0x2D),
            JbdCommands.closeFactoryMode()
        )
    }

    @Test
    fun failedEntryFallsBackToExactlyOneDirectReadProbe() = runTest {
        val gateway = FakeGateway(
            GatewayOutcome.NoResponse,
            register(0x24, 4_200)
        )

        val result = SettingsSessionCoordinator(gateway).readFields(listOf(SettingField.COVP))

        assertEquals(SettingsAccessMode.DIRECT, result.accessMode)
        assertEquals(4_200, result.values.getValue(SettingField.COVP).raw)
        assertTrue(
            SettingsSessionWarning.FACTORY_ENTRY_FAILED_DIRECT_FALLBACK in result.warnings
        )
        assertFrames(
            gateway,
            JbdCommands.openFactoryMode(),
            JbdConfigCommands.readRegister(0x24)
        )
    }

    @Test
    fun failedEntryAndDirectProbeReturnBlocked() = runTest {
        val gateway = FakeGateway(GatewayOutcome.NoResponse, GatewayOutcome.NoResponse)

        val result = SettingsSessionCoordinator(gateway).readFields(listOf(SettingField.COVP))

        assertEquals(SettingsAccessMode.BLOCKED, result.accessMode)
        assertTrue(result.values.isEmpty())
        assertTrue(result.registerErrors.getValue(0x24) is RegisterAccessError.NoResponse)
    }

    @Test
    fun registerErrorStatusIsCollectedAndReadSessionStillExits() = runTest {
        val gateway = FakeGateway(ack(0x00), error(0x24), ack(0x01))

        val result = SettingsSessionCoordinator(gateway).readFields(listOf(SettingField.COVP))

        assertEquals(SettingsAccessMode.FACTORY, result.accessMode)
        assertEquals(RegisterAccessError.ErrorStatus(0x80), result.registerErrors[0x24])
        assertArrayEquals(JbdCommands.closeFactoryMode(), gateway.frames.last())
    }

    @Test
    fun unconfirmedReadExitProducesFactoryModeWarning() = runTest {
        val gateway = FakeGateway(ack(0x00), register(0x24, 4_200), GatewayOutcome.NoResponse)

        val result = SettingsSessionCoordinator(gateway).readFields(listOf(SettingField.COVP))

        assertTrue(SettingsSessionWarning.EXIT_UNCONFIRMED in result.warnings)
        assertTrue(SettingsSessionWarning.DEVICE_MAY_REMAIN_IN_FACTORY_MODE in result.warnings)
    }

    @Test
    fun allVerifiedWritesSnapshotCountersThenCommitLast() = runTest {
        val counters = IntArray(11) { it + 10 }
        val gateway = FakeGateway(
            ack(0x00),
            register(0x24, 4_200),
            ack(0x24),
            register(0x24, 4_100),
            counterResponse(counters),
            ack(0x01)
        )

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.Committed)
        assertTrue(result.changes.single().detail is ChangeDetail.Verified)
        assertArrayEquals(counters, result.errorCountersBeforeCommit)
        assertFrames(
            gateway,
            JbdCommands.openFactoryMode(),
            JbdConfigCommands.readRegister(0x24),
            JbdConfigCommands.writeRegister(0x24, u16(4_100)),
            JbdConfigCommands.readRegister(0x24),
            JbdConfigCommands.readRegister(0xAA),
            JbdConfigCommands.exitFactoryModeWithCommit()
        )
    }

    @Test
    fun readBackMismatchUsesNoCommitExit() = runTest {
        val gateway = FakeGateway(
            ack(0x00),
            register(0x24, 4_200),
            ack(0x24),
            register(0x24, 4_150),
            counterResponse(IntArray(11)),
            ack(0x01)
        )

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.NotCommitted)
        assertTrue(result.changes.single().detail is ChangeDetail.ReadBackMismatch)
        assertArrayEquals(JbdCommands.closeFactoryMode(), gateway.frames.last())
    }

    @Test
    fun noChangeSkipsRegisterWriteAndExitsWithoutCommit() = runTest {
        val gateway = FakeGateway(
            ack(0x00),
            register(0x24, 4_100),
            counterResponse(IntArray(11)),
            ack(0x01)
        )

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.NotCommitted)
        assertTrue(result.changes.single().detail is ChangeDetail.SkippedNoChange)
        assertFrames(
            gateway,
            JbdCommands.openFactoryMode(),
            JbdConfigCommands.readRegister(0x24),
            JbdConfigCommands.readRegister(0xAA),
            JbdCommands.closeFactoryMode()
        )
    }

    @Test
    fun bitfieldReadModifyWritePreservesUnrelatedBits() = runTest {
        val gateway = FakeGateway(
            ack(0x00),
            register(0x2D, 0x00A2),
            ack(0x2D),
            register(0x2D, 0x00A6),
            counterResponse(IntArray(11)),
            ack(0x01)
        )

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.BALANCE_ENABLE, 1))
        )

        assertTrue(result.outcome is WriteSessionOutcome.Committed)
        assertArrayEquals(
            JbdConfigCommands.writeRegister(0x2D, u16(0x00A6)),
            gateway.frames[2]
        )
    }

    @Test
    fun transportFailureMidSessionAttemptsBestEffortNoCommitExit() = runTest {
        val gateway = FakeGateway(
            ack(0x00),
            register(0x24, 4_200),
            GatewayOutcome.TransportFailure("disconnected"),
            ack(0x01)
        )

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.NotCommitted)
        assertTrue(result.changes.single().detail is ChangeDetail.TransportFailure)
        assertTrue(SettingsSessionWarning.COUNTER_SNAPSHOT_UNAVAILABLE in result.warnings)
        assertArrayEquals(JbdCommands.closeFactoryMode(), gateway.frames.last())
    }

    @Test
    fun invalidChangeProducesNoGatewayTraffic() = runTest {
        val gateway = FakeGateway()

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.COVP, 9_999))
        )

        assertTrue(result.outcome is WriteSessionOutcome.ValidationFailed)
        assertTrue(gateway.frames.isEmpty())
    }

    @Test
    fun relationallyInvalidStagedPairProducesNoGatewayTraffic() = runTest {
        val gateway = FakeGateway()

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(
                FieldChange(SettingField.COVP, 4_100),
                FieldChange(SettingField.COVP_RELEASE, 4_100)
            )
        )

        assertTrue(result.outcome is WriteSessionOutcome.ValidationFailed)
        assertTrue(gateway.frames.isEmpty())
    }

    @Test
    fun writesRequireConfirmedFactoryEntry() = runTest {
        val gateway = FakeGateway(GatewayOutcome.NoResponse)

        val result = SettingsSessionCoordinator(gateway).applyChanges(
            listOf(FieldChange(SettingField.COVP, 4_100))
        )

        assertTrue(result.outcome is WriteSessionOutcome.EntryFailed)
        assertEquals(1, gateway.frames.size)
        assertArrayEquals(JbdCommands.openFactoryMode(), gateway.frames.single())
    }

    private class FakeGateway(vararg outcomes: GatewayOutcome) : SettingsGateway {
        private val queued = ArrayDeque(outcomes.toList())
        val frames = mutableListOf<ByteArray>()

        override suspend fun execute(
            frame: ByteArray,
            responseAddress: Int,
            timeoutMs: Long
        ): GatewayOutcome {
            frames += frame.copyOf()
            check(timeoutMs > 0)
            check(queued.isNotEmpty()) { "No fake outcome queued for address $responseAddress" }
            return queued.removeFirst()
        }
    }

    private fun assertFrames(gateway: FakeGateway, vararg expected: ByteArray) {
        assertEquals(expected.size, gateway.frames.size)
        expected.forEachIndexed { index, frame ->
            assertArrayEquals("frame $index", frame, gateway.frames[index])
        }
    }

    companion object {
        private fun ack(address: Int): GatewayOutcome =
            GatewayOutcome.Response(response(address, 0x00, byteArrayOf()))

        private fun error(address: Int): GatewayOutcome =
            GatewayOutcome.Response(response(address, 0x80, byteArrayOf()))

        private fun register(address: Int, raw: Int): GatewayOutcome =
            GatewayOutcome.Response(response(address, 0x00, u16(raw)))

        private fun counterResponse(counters: IntArray): GatewayOutcome {
            require(counters.size == 11)
            val payload = counters.flatMap { u16(it).asIterable() }.toByteArray()
            return GatewayOutcome.Response(response(0xAA, 0x00, payload))
        }

        private fun u16(value: Int): ByteArray =
            byteArrayOf((value ushr 8).toByte(), value.toByte())

        private fun response(address: Int, status: Int, payload: ByteArray): JbdFrame {
            val length = payload.size
            val checksum =
                (-(status + length + payload.sumOf { it.toInt() and 0xFF })) and 0xFFFF
            return JbdFrame.parse(
                byteArrayOf(0xDD.toByte(), address.toByte(), status.toByte(), length.toByte()) +
                    payload +
                    byteArrayOf((checksum ushr 8).toByte(), checksum.toByte(), 0x77)
            )
        }
    }
}
