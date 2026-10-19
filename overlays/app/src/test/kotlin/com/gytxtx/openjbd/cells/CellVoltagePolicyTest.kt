package com.gytxtx.openjbd.cells

import com.gytxtx.openjbd.balance.BalanceCellDiagnostics
import com.gytxtx.openjbd.balance.BalanceReadoutResolver
import com.gytxtx.openjbd.balance.CellThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellVoltagePolicyTest {
    @Test
    fun fourteenSlotProductWithTenConnectedCellsExcludesAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 10)
    }

    @Test
    fun fourteenSlotProductWithElevenConnectedCellsExcludesAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 11)
    }

    @Test
    fun fourteenSlotProductWithTwelveConnectedCellsExcludesAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 12)
    }

    @Test
    fun fourteenSlotProductWithThirteenConnectedCellsExcludesAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 13)
    }

    private fun assertFourteenSlotAbsentCellCase(connectedCellCount: Int) {
        val cells = (0 until 14).map { index ->
            if (index < connectedCellCount) 3.400f + index * 0.001f else 0.006f
        }
        val analysis = CellVoltagePolicy.analyze(
            cells = cells,
            configuredCellCount = connectedCellCount
        )
        val balanceStates = BooleanArray(14) { index ->
            index == 0 || index >= connectedCellCount
        }
        val diagnostics = BalanceCellDiagnostics.analyze(
            cells = cells,
            balanceStates = balanceStates,
            thresholds = CellThresholds.nmcDefault(),
            configuredCellCount = connectedCellCount
        )
        val expectedMaximum = 3.400f + (connectedCellCount - 1) * 0.001f
        val expectedAverage = (3.400f + expectedMaximum) / 2f
        val stats = checkNotNull(analysis.stats)
        val readout = BalanceReadoutResolver.resolve(
            balanceStates = diagnostics.map { it.isBalancing }.toBooleanArray(),
            visibleCellCount = diagnostics.size,
            hasBalanceCurrent = false,
            balanceCurrentA = 0f
        )

        assertEquals(connectedCellCount, analysis.connectedCellIndices.size)
        assertEquals(3.400f, stats.minimum, 0.0001f)
        assertEquals(expectedMaximum, stats.maximum, 0.0001f)
        assertEquals(expectedAverage, stats.average, 0.0001f)
        assertEquals(1, readout.activeBalancingCellCount)
        assertTrue(diagnostics.first().isBalancing)
        assertTrue(diagnostics.drop(connectedCellCount).all { it.isAbsent })
        assertTrue(diagnostics.drop(connectedCellCount).none { it.isBalancing })
    }

    @Test
    fun readingsBeyondConfiguredCellCountAreAbsentEvenAtNormalVoltage() {
        val analysis = CellVoltagePolicy.analyze(
            cells = listOf(3.300f, 3.310f, 3.320f, 3.330f),
            configuredCellCount = 2
        )

        assertFalse(analysis.readings[0].isAbsent)
        assertFalse(analysis.readings[1].isAbsent)
        assertTrue(analysis.readings[2].isAbsent)
        assertTrue(analysis.readings[3].isAbsent)
        val stats = checkNotNull(analysis.stats)
        assertEquals(3.300f, stats.minimum, 0.0001f)
        assertEquals(3.310f, stats.maximum, 0.0001f)
    }

    @Test
    fun lowVoltageWithinConfiguredCellCountRemainsMonitored() {
        val analysis = CellVoltagePolicy.analyze(
            cells = listOf(0.006f, 0.500f),
            configuredCellCount = 2
        )

        assertFalse(analysis.readings[0].isAbsent)
        assertFalse(analysis.readings[1].isAbsent)
        assertEquals(0.006f, checkNotNull(analysis.stats).minimum, 0.0001f)
    }

    @Test
    fun noConnectedCellsProduceNoStatistics() {
        val analysis = CellVoltagePolicy.analyze(
            cells = listOf(0.006f, 0.006f),
            configuredCellCount = 0
        )

        assertTrue(analysis.connectedCellIndices.isEmpty())
        assertNull(analysis.stats)
    }
}
