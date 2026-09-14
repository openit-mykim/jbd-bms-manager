package com.gytxtx.openjbd.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceHighlightsTest {
    @Test
    fun analyzeMarksVoltageExtremesAndActiveBalancingCells() {
        val result = BalanceCellDiagnostics.analyze(
            cells = listOf(3.312f, 3.287f, 3.301f, 3.325f),
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
        val result = BalanceCellDiagnostics.analyze(
            cells = listOf(3.2f, 3.3f),
            balanceStates = booleanArrayOf(true),
            thresholds = CellThresholds.nmcDefault()
        )

        assertTrue(result[0].isBalancing)
        assertFalse(result[1].isBalancing)
    }
}
