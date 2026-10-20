package com.gytxtx.openjbd.data

import com.gytxtx.openjbd.cells.CellVoltagePolicy
import com.gytxtx.openjbd.protocol.JbdBasicInfo
import com.gytxtx.openjbd.protocol.JbdCellVoltages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BmsRepositoryCellVoltageTest {
    @Test
    fun unrelatedStateEmissionsDoNotAdvanceCellDebounce() {
        val repository = BmsRepository()
        val firstFrame = JbdCellVoltages(listOf(3.300f, 0.300f))
        repository.update(connectedSnapshot(firstFrame))

        repeat(5) {
            val current = repository.getSnapshot()
            repository.update(
                current.withConnectionState(
                    connectionState = ConnectionState.READY,
                    connected = true,
                    status = "status-$it"
                )
            )
        }

        assertFalse(repository.getSnapshot().cellVoltageAnalysis!!.readings[1].isAbsent)

        repository.update(connectedSnapshot(JbdCellVoltages(listOf(3.300f, 0.300f))))
        assertFalse(repository.getSnapshot().cellVoltageAnalysis!!.readings[1].isAbsent)

        repository.update(connectedSnapshot(JbdCellVoltages(listOf(3.300f, 0.300f))))
        assertTrue(repository.getSnapshot().cellVoltageAnalysis!!.readings[1].isAbsent)
    }

    @Test
    fun disconnectResetsCellPresenceDebounce() {
        val repository = BmsRepository()
        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES) {
            repository.update(connectedSnapshot(JbdCellVoltages(listOf(3.300f, 0.300f))))
        }
        assertTrue(repository.getSnapshot().cellVoltageAnalysis!!.readings[1].isAbsent)

        repository.update(BmsUiState.disconnected("Test BMS", DEVICE_ADDRESS, "disconnected"))
        repository.update(connectedSnapshot(JbdCellVoltages(listOf(3.300f, 0.300f))))

        assertFalse(repository.getSnapshot().cellVoltageAnalysis!!.readings[1].isAbsent)
    }

    @Test
    fun zeroVoltageChannelCollapsesUpperSlotsOnFirstFrame() {
        val repository = BmsRepository()
        repository.update(
            connectedSnapshot(
                JbdCellVoltages(
                    (0 until 14).map { index ->
                        when (index) {
                            12 -> 0.006f
                            13 -> 3.312f
                            else -> 3.300f + index * 0.001f
                        }
                    }
                ),
                cellCount = 14
            )
        )

        val analysis = checkNotNull(repository.getSnapshot().cellVoltageAnalysis)
        assertEquals(12, analysis.effectiveCellCount)
        assertEquals(13, analysis.zeroVoltageCellNumber)
        assertNull(analysis.wiringShortCellNumber)
        assertTrue(analysis.readings.drop(12).all { it.isAbsent })
        assertEquals((0..11).toSet(), analysis.connectedCellIndices)
    }

    private fun connectedSnapshot(cells: JbdCellVoltages, cellCount: Int = 2): BmsUiState =
        BmsUiState.withConnectionState(
            connectionState = ConnectionState.READY,
            connected = true,
            deviceName = "Test BMS",
            deviceAddress = DEVICE_ADDRESS,
            status = null,
            basicInfo = basicInfo(cellCount),
            cellVoltages = cells
        )

    private fun basicInfo(cellCount: Int = 2) = JbdBasicInfo(
        totalVoltage = 6.6f,
        current = 0f,
        remainingAh = 50f,
        nominalAh = 100f,
        cycleCount = 10,
        productionDate = "2026-01-01",
        soc = 50,
        balanceState = 0,
        balanceStates = BooleanArray(2),
        protectionState = 0,
        protectionStates = BooleanArray(16),
        chargeEnabled = true,
        dischargeEnabled = true,
        cellCount = cellCount,
        ntcCount = 1,
        softwareVersion = "1.0",
        temperaturesC = listOf(25f),
        hasLearnCapacity = false,
        learnCapacityAh = 0f,
        hasExtendedInfo = false,
        extensionMarker = 0,
        alter = 0,
        hasBalanceCurrent = false,
        balanceCurrentA = 0f
    )

    private companion object {
        const val DEVICE_ADDRESS = "00:11:22:33:44:55"
    }
}
