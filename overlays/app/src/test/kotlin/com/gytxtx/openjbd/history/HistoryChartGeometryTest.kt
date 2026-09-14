package com.gytxtx.openjbd.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryChartGeometryTest {
    @Test
    fun emptyBucketsHaveNoBounds() {
        assertNull(HistoryChartGeometry.bounds(emptyList()))
    }

    @Test
    fun constantValuesExpandToNonZeroBounds() {
        val bounds = assertNotNullBounds(
            HistoryChartGeometry.bounds(listOf(bucket(minimum = 50f, maximum = 50f)))
        )

        assertTrue(bounds.minValue < 50f)
        assertTrue(bounds.maxValue > 50f)
        assertTrue(HistoryChartGeometry.yForValue(50f, bounds, 100f).isFinite())
    }

    @Test
    fun greaterValuesMapHigherOnChart() {
        val bounds = ChartBounds(minValue = 0f, maxValue = 100f)

        assertEquals(0f, HistoryChartGeometry.yForValue(100f, bounds, 200f), 0.0001f)
        assertEquals(200f, HistoryChartGeometry.yForValue(0f, bounds, 200f), 0.0001f)
    }

    @Test
    fun yCoordinatesClampOutsideBounds() {
        val bounds = ChartBounds(minValue = 10f, maxValue = 20f)

        assertEquals(100f, HistoryChartGeometry.yForValue(5f, bounds, 100f), 0.0001f)
        assertEquals(0f, HistoryChartGeometry.yForValue(25f, bounds, 100f), 0.0001f)
    }

    @Test
    fun xCoordinatesClampOutsideTimeRange() {
        assertEquals(
            0f,
            HistoryChartGeometry.xForTime(500L, 1_000L, 2_000L, 300f),
            0.0001f
        )
        assertEquals(
            300f,
            HistoryChartGeometry.xForTime(2_500L, 1_000L, 2_000L, 300f),
            0.0001f
        )
    }

    @Test
    fun invalidTimeRangeMapsToZero() {
        assertEquals(
            0f,
            HistoryChartGeometry.xForTime(1_500L, 2_000L, 2_000L, 300f),
            0.0001f
        )
        assertEquals(
            0f,
            HistoryChartGeometry.xForTime(1_500L, 2_000L, 1_000L, 300f),
            0.0001f
        )
    }

    @Test
    fun boundsApplyPaddingRatio() {
        val bounds = assertNotNullBounds(
            HistoryChartGeometry.bounds(
                listOf(bucket(minimum = 10f, maximum = 20f)),
                padRatio = 0.2f
            )
        )

        assertEquals(8f, bounds.minValue, 0.0001f)
        assertEquals(22f, bounds.maxValue, 0.0001f)
    }

    private fun bucket(minimum: Float, maximum: Float) = HistoryBucket(
        startMillis = 1_000L,
        minValue = minimum,
        maxValue = maximum,
        averageValue = (minimum + maximum) / 2f
    )

    private fun assertNotNullBounds(bounds: ChartBounds?): ChartBounds {
        assertNotNull(bounds)
        return checkNotNull(bounds)
    }
}
