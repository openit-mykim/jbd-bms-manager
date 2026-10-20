package com.gytxtx.openjbd.balance

import com.gytxtx.openjbd.cells.CellVoltagePolicy
import com.gytxtx.openjbd.cells.CellVoltageTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceHighlightsTest {
    @Test
    fun analyzeMarksVoltageExtremesAndActiveBalancingCells() {
        val analysis = CellVoltageTracker().update(
            cells = listOf(3.312f, 3.287f, 3.301f, 3.325f),
            configuredCellCount = 4
        )
        val result = BalanceCellDiagnostics.analyze(
            analysis = analysis,
            balanceStates = booleanArrayOf(false, true, false, true),
            thresholds = CellThresholds.nmcDefault()
        )

        assertEquals(CellHighlight.NORMAL, result[0].highlight)
        assertEquals(CellHighlight.LOWEST, result[1].highlight)
        assertEquals(CellHighlight.NORMAL, result[2].highlight)
        assertEquals(CellHighlight.HIGHEST, result[3].highlight)
        assertFalse(result[0].isBalancing)
        assertTrue(result[1].isBalancing)
        assertTrue(result[3].isBalancing)
        assertEquals(80, result[1].progress)
        assertEquals(1000, result[3].progress)
    }

    @Test
    fun analyzeTreatsMissingBalanceFlagsAsInactive() {
        val analysis = CellVoltageTracker().update(
            cells = listOf(3.2f, 3.3f),
            configuredCellCount = 2
        )
        val result = BalanceCellDiagnostics.analyze(
            analysis = analysis,
            balanceStates = booleanArrayOf(true),
            thresholds = CellThresholds.nmcDefault()
        )

        assertTrue(result[0].isBalancing)
        assertFalse(result[1].isBalancing)
    }

    @Test
    fun analyzeKeepsAbsentRowsNeutralAndExcludesTheirBalancingFlags() {
        val tracker = CellVoltageTracker()
        var analysis = tracker.update(
            cells = listOf(3.300f, 3.310f, 0.006f, 3.330f),
            configuredCellCount = 3
        )
        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES - 1) {
            analysis = tracker.update(
                cells = listOf(3.300f, 3.310f, 0.006f, 3.330f),
                configuredCellCount = 3
            )
        }
        val result = BalanceCellDiagnostics.analyze(
            analysis = analysis,
            balanceStates = booleanArrayOf(false, false, true, true),
            thresholds = CellThresholds.nmcDefault()
        )

        assertFalse(result[0].isAbsent)
        assertFalse(result[1].isAbsent)
        assertTrue(result[2].isAbsent)
        assertTrue(result[3].isAbsent)
        assertFalse(result[2].isBalancing)
        assertFalse(result[3].isBalancing)
        assertEquals(CellHighlight.NORMAL, result[2].highlight)
        assertEquals(CellHighlight.NORMAL, result[3].highlight)
        assertEquals(null, result[2].band)
        assertEquals(null, result[3].band)
    }
}
