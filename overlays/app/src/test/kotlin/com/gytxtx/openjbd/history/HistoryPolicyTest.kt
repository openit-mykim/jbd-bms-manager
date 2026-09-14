package com.gytxtx.openjbd.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPolicyTest {
    @Test
    fun connectedWithBasicInfoIsRecorded() {
        assertTrue(HistoryPolicy.shouldRecord(connected = true, hasBasicInfo = true))
    }

    @Test
    fun disconnectedSnapshotIsNotRecorded() {
        assertEquals(false, HistoryPolicy.shouldRecord(connected = false, hasBasicInfo = true))
    }

    @Test
    fun snapshotWithoutBasicInfoIsNotRecorded() {
        assertEquals(false, HistoryPolicy.shouldRecord(connected = true, hasBasicInfo = false))
    }

    @Test
    fun disconnectedSnapshotWithoutBasicInfoIsNotRecorded() {
        assertEquals(false, HistoryPolicy.shouldRecord(connected = false, hasBasicInfo = false))
    }

    @Test
    fun retentionCutoffIsExactlySevenDaysBeforeNow() {
        val now = 1_000_000_000L

        assertEquals(395_200_000L, HistoryPolicy.retentionCutoffMillis(now))
    }

    @Test
    fun retentionCutoffCanBeNegativeNearEpoch() {
        assertEquals(-604_799_999L, HistoryPolicy.retentionCutoffMillis(1L))
    }

    @Test
    fun emptyInputProducesNoBuckets() {
        assertTrue(HistoryPolicy.bucketize(emptyList(), { it.socPercent.toFloat() }, 10).isEmpty())
    }

    @Test
    fun nonPositiveBucketLimitProducesNoBuckets() {
        val samples = listOf(sample(timestampMillis = 1L, socPercent = 50))

        assertTrue(HistoryPolicy.bucketize(samples, { it.socPercent.toFloat() }, 0).isEmpty())
        assertTrue(HistoryPolicy.bucketize(samples, { it.socPercent.toFloat() }, -1).isEmpty())
    }

    @Test
    fun samplesAtOrBelowLimitProduceOneBucketEach() {
        val samples = listOf(
            sample(timestampMillis = 30L, socPercent = 30),
            sample(timestampMillis = 10L, socPercent = 10),
            sample(timestampMillis = 20L, socPercent = 20)
        )

        val buckets = HistoryPolicy.bucketize(samples, { it.socPercent.toFloat() }, 3)

        assertEquals(listOf(10L, 20L, 30L), buckets.map { it.startMillis })
        assertEquals(listOf(10f, 20f, 30f), buckets.map { it.averageValue })
    }

    @Test
    fun largerInputUsesRequestedNumberOfTimeBuckets() {
        val samples = (0 until 100).map { index ->
            sample(timestampMillis = index * 1_000L, socPercent = index)
        }

        val buckets = HistoryPolicy.bucketize(samples, { it.socPercent.toFloat() }, 10)

        assertEquals(10, buckets.size)
    }

    @Test
    fun everyBucketAverageStaysBetweenMinimumAndMaximum() {
        val samples = (0 until 100).map { index ->
            sample(timestampMillis = index * 1_000L, socPercent = (index * 7) % 101)
        }

        val buckets = HistoryPolicy.bucketize(samples, { it.socPercent.toFloat() }, 8)

        assertTrue(buckets.all { it.minValue <= it.averageValue && it.averageValue <= it.maxValue })
    }

    @Test
    fun samplesWithNullValuesAreExcluded() {
        val samples = listOf(
            sample(timestampMillis = 1L, cellDeltaMillivolts = null),
            sample(timestampMillis = 2L, cellDeltaMillivolts = 12),
            sample(timestampMillis = 3L, cellDeltaMillivolts = null)
        )

        val buckets = HistoryPolicy.bucketize(
            samples,
            { it.cellDeltaMillivolts?.toFloat() },
            10
        )

        assertEquals(1, buckets.size)
        assertEquals(2L, buckets.single().startMillis)
        assertEquals(12f, buckets.single().averageValue)
    }

    @Test
    fun samplingAndRetentionConstantsMatchApprovedPolicy() {
        assertEquals(10_000L, HistoryPolicy.SAMPLE_INTERVAL_MILLIS)
        assertEquals(7L, HistoryPolicy.RETENTION_DAYS)
        assertEquals(3_600_000L, HistoryPolicy.PRUNE_INTERVAL_MILLIS)
        assertEquals(70_000, HistoryPolicy.MAX_SAMPLES)
    }

    private fun sample(
        timestampMillis: Long,
        socPercent: Int = 50,
        cellDeltaMillivolts: Int? = 10
    ) = HistorySample(
        timestampMillis = timestampMillis,
        socPercent = socPercent,
        packMillivolts = 52_000,
        currentMilliamps = 0,
        cellDeltaMillivolts = cellDeltaMillivolts
    )
}
