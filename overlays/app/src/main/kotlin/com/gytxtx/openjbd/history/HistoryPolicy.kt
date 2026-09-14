package com.gytxtx.openjbd.history

data class HistoryBucket(
    val startMillis: Long,
    val minValue: Float,
    val maxValue: Float,
    val averageValue: Float
)

object HistoryPolicy {
    const val SAMPLE_INTERVAL_MILLIS = 10_000L
    const val RETENTION_DAYS = 7L
    const val PRUNE_INTERVAL_MILLIS = 60L * 60L * 1_000L
    const val MAX_SAMPLES = 70_000

    private const val MILLIS_PER_DAY = 86_400_000L

    fun shouldRecord(connected: Boolean, hasBasicInfo: Boolean): Boolean =
        connected && hasBasicInfo

    fun retentionCutoffMillis(nowMillis: Long): Long =
        nowMillis - RETENTION_DAYS * MILLIS_PER_DAY

    fun bucketize(
        samples: List<HistorySample>,
        valueOf: (HistorySample) -> Float?,
        maxBuckets: Int
    ): List<HistoryBucket> {
        if (samples.isEmpty() || maxBuckets <= 0) return emptyList()

        val valuedSamples = samples.mapNotNull { sample ->
            valueOf(sample)?.let { value -> ValuedSample(sample.timestampMillis, value) }
        }.sortedBy(ValuedSample::timestampMillis)
        if (valuedSamples.isEmpty()) return emptyList()

        if (valuedSamples.size <= maxBuckets) {
            return valuedSamples.map { sample ->
                HistoryBucket(
                    startMillis = sample.timestampMillis,
                    minValue = sample.value,
                    maxValue = sample.value,
                    averageValue = sample.value
                )
            }
        }

        val firstTimestamp = valuedSamples.first().timestampMillis
        val lastTimestamp = valuedSamples.last().timestampMillis
        if (firstTimestamp == lastTimestamp) {
            return listOf(valuedSamples.toBucket(firstTimestamp))
        }

        val timeRange = (lastTimestamp - firstTimestamp).toDouble()
        return valuedSamples
            .groupBy { sample ->
                (((sample.timestampMillis - firstTimestamp).toDouble() / timeRange) * maxBuckets)
                    .toInt()
                    .coerceIn(0, maxBuckets - 1)
            }
            .toSortedMap()
            .map { (bucketIndex, bucketSamples) ->
                val bucketStart = firstTimestamp +
                    (timeRange * bucketIndex.toDouble() / maxBuckets.toDouble()).toLong()
                bucketSamples.toBucket(bucketStart)
            }
    }

    private fun List<ValuedSample>.toBucket(startMillis: Long): HistoryBucket {
        var minimum = Float.POSITIVE_INFINITY
        var maximum = Float.NEGATIVE_INFINITY
        var total = 0.0
        forEach { sample ->
            minimum = minOf(minimum, sample.value)
            maximum = maxOf(maximum, sample.value)
            total += sample.value.toDouble()
        }
        return HistoryBucket(
            startMillis = startMillis,
            minValue = minimum,
            maxValue = maximum,
            averageValue = (total / size).toFloat()
        )
    }

    private data class ValuedSample(val timestampMillis: Long, val value: Float)
}
