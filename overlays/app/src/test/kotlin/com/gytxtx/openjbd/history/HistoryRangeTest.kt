package com.gytxtx.openjbd.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRangeTest {
    @Test
    fun last24HoursHasExactDuration() {
        assertEquals(86_400_000L, HistoryRange.LAST_24H.durationMillis)
    }

    @Test
    fun last7DaysHasExactDuration() {
        assertEquals(604_800_000L, HistoryRange.LAST_7D.durationMillis)
    }

    @Test
    fun startMillisSubtractsRangeDuration() {
        assertEquals(13_600_000L, HistoryRange.LAST_24H.startMillis(100_000_000L))
    }

    @Test
    fun last7DaysMatchesRetentionPolicy() {
        assertEquals(
            HistoryPolicy.RETENTION_DAYS * 24L * 60L * 60L * 1_000L,
            HistoryRange.LAST_7D.durationMillis
        )
    }

    @Test
    fun socMetricConvertsToFloat() {
        assertEquals(73f, checkNotNull(valueOf(HistoryMetric.SOC_PERCENT, sample())), 0f)
    }

    @Test
    fun packVoltageMetricConvertsMillivoltsToVolts() {
        assertEquals(
            52.345f,
            checkNotNull(valueOf(HistoryMetric.PACK_VOLTS, sample())),
            0.0001f
        )
    }

    @Test
    fun cellDeltaMetricConvertsToFloat() {
        assertEquals(18f, checkNotNull(valueOf(HistoryMetric.CELL_DELTA_MV, sample())), 0f)
    }

    @Test
    fun cellDeltaMetricPropagatesNull() {
        assertNull(
            valueOf(
                HistoryMetric.CELL_DELTA_MV,
                sample(cellDeltaMillivolts = null)
            )
        )
    }

    private fun sample(cellDeltaMillivolts: Int? = 18) = HistorySample(
        timestampMillis = 1_000L,
        socPercent = 73,
        packMillivolts = 52_345,
        currentMilliamps = -1_250,
        cellDeltaMillivolts = cellDeltaMillivolts
    )
}
