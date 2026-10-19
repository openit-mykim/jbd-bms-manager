package com.gytxtx.openjbd.cells

import com.gytxtx.openjbd.balance.BalanceCellDiagnostic
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
    fun fourteenSlotProductWithTenConnectedCellsExcludesDebouncedAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 10)
    }

    @Test
    fun fourteenSlotProductWithElevenConnectedCellsExcludesDebouncedAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 11)
    }

    @Test
    fun fourteenSlotProductWithTwelveConnectedCellsExcludesDebouncedAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 12)
    }

    @Test
    fun fourteenSlotProductWithThirteenConnectedCellsExcludesDebouncedAbsentCells() {
        assertFourteenSlotAbsentCellCase(connectedCellCount = 13)
    }

    private fun assertFourteenSlotAbsentCellCase(connectedCellCount: Int) {
        val tracker = CellVoltageTracker()
        val cells = fourteenSlotCells(connectedCellCount)
        var analysis = tracker.update(cells, configuredCellCount = 14)
        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES - 1) {
            analysis = tracker.update(cells, configuredCellCount = 14)
        }

        val balanceStates = BooleanArray(14) { index ->
            index == 0 || index >= connectedCellCount
        }
        var diagnostics = BalanceCellDiagnostics.analyze(
            analysis = analysis,
            balanceStates = balanceStates,
            thresholds = CellThresholds.nmcDefault()
        )
        val expectedMaximum = 3.400f + (connectedCellCount - 1) * 0.001f
        val expectedAverage = (3.400f + expectedMaximum) / 2f
        val stats = checkNotNull(analysis.stats)

        assertEquals(connectedCellCount, analysis.connectedCellIndices.size)
        assertEquals(3.400f, stats.minimum, 0.0001f)
        assertEquals(expectedMaximum, stats.maximum, 0.0001f)
        assertEquals(expectedAverage, stats.average, 0.0001f)
        assertEquals(1, activeBalancingCount(diagnostics))
        assertTrue(diagnostics.first().isBalancing)
        assertTrue(diagnostics.drop(connectedCellCount).all { it.isAbsent })
        assertTrue(diagnostics.drop(connectedCellCount).none { it.isBalancing })

        val oneFrameGhost = cells.mapIndexed { index, voltage ->
            if (index >= connectedCellCount) 3.600f else voltage
        }
        analysis = tracker.update(oneFrameGhost, configuredCellCount = 14)
        diagnostics = BalanceCellDiagnostics.analyze(
            analysis = analysis,
            balanceStates = balanceStates,
            thresholds = CellThresholds.nmcDefault()
        )

        assertEquals(connectedCellCount, analysis.connectedCellIndices.size)
        assertEquals(expectedAverage, checkNotNull(analysis.stats).average, 0.0001f)
        assertEquals(1, activeBalancingCount(diagnostics))
        assertTrue(diagnostics.drop(connectedCellCount).all { it.isAbsent })
    }

    @Test
    fun lowVoltageRequiresConsecutiveSamplesBeforeAbsent() {
        val tracker = CellVoltageTracker()

        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES - 1) {
            val analysis = tracker.update(listOf(0.006f), configuredCellCount = 1)
            assertFalse(analysis.readings.single().isAbsent)
        }

        val confirmed = tracker.update(listOf(0.006f), configuredCellCount = 1)
        assertTrue(confirmed.readings.single().isAbsent)
        assertNull(confirmed.stats)
    }

    @Test
    fun absentCellRequiresConsecutiveSamplesAboveRecoveryThreshold() {
        val tracker = CellVoltageTracker()
        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES) {
            tracker.update(listOf(0.006f), configuredCellCount = 1)
        }

        val middle = tracker.update(listOf(0.750f), configuredCellCount = 1)
        assertTrue(middle.readings.single().isAbsent)

        repeat(CellVoltagePolicy.PRESENT_DEBOUNCE_SAMPLES - 1) {
            val recovering = tracker.update(listOf(3.300f), configuredCellCount = 1)
            assertTrue(recovering.readings.single().isAbsent)
        }

        val recovered = tracker.update(listOf(3.300f), configuredCellCount = 1)
        assertFalse(recovered.readings.single().isAbsent)
    }

    @Test
    fun middleBandPreservesStateAndBreaksConsecutiveStreak() {
        val tracker = CellVoltageTracker()

        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES - 1) {
            tracker.update(listOf(0.006f), configuredCellCount = 1)
        }
        assertFalse(
            tracker.update(listOf(0.750f), configuredCellCount = 1)
                .readings.single().isAbsent
        )
        assertFalse(
            tracker.update(listOf(0.006f), configuredCellCount = 1)
                .readings.single().isAbsent
        )

        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES - 1) {
            tracker.update(listOf(0.006f), configuredCellCount = 1)
        }
        assertTrue(
            tracker.update(listOf(0.750f), configuredCellCount = 1)
                .readings.single().isAbsent
        )
    }

    @Test
    fun readingsBeyondConfiguredCellCountAreImmediatelyAbsent() {
        val analysis = CellVoltageTracker().update(
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
    fun thresholdBoundariesStayInMiddleBand() {
        val tracker = CellVoltageTracker()
        repeat(CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES) {
            tracker.update(listOf(0.006f, 0.500f), configuredCellCount = 2)
        }

        val atBoundaries = tracker.update(
            listOf(1.000f, 0.500f),
            configuredCellCount = 2
        )

        assertTrue(atBoundaries.readings[0].isAbsent)
        assertFalse(atBoundaries.readings[1].isAbsent)
    }

    private fun fourteenSlotCells(connectedCellCount: Int): List<Float> =
        (0 until 14).map { index ->
            if (index < connectedCellCount) 3.400f + index * 0.001f else 0.006f
        }

    private fun activeBalancingCount(diagnostics: List<BalanceCellDiagnostic>): Int =
        BalanceReadoutResolver.resolve(
            balanceStates = diagnostics.map { it.isBalancing }.toBooleanArray(),
            visibleCellCount = diagnostics.size,
            hasBalanceCurrent = false,
            balanceCurrentA = 0f
        ).activeBalancingCellCount
}
