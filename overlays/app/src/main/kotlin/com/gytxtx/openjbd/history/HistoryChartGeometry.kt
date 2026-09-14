package com.gytxtx.openjbd.history

import kotlin.math.abs
import kotlin.math.max

data class ChartBounds(val minValue: Float, val maxValue: Float)

object HistoryChartGeometry {
    fun bounds(
        buckets: List<HistoryBucket>,
        padRatio: Float = 0.1f
    ): ChartBounds? {
        if (buckets.isEmpty()) return null

        var minimum = Float.POSITIVE_INFINITY
        var maximum = Float.NEGATIVE_INFINITY
        for (bucket in buckets) {
            minimum = minOf(minimum, bucket.minValue)
            maximum = maxOf(maximum, bucket.maxValue)
        }

        if (minimum == maximum) {
            val expansion = max(1f, abs(minimum) * 0.01f)
            minimum -= expansion
            maximum += expansion
        }

        val padding = (maximum - minimum) * padRatio.coerceAtLeast(0f)
        return ChartBounds(
            minValue = minimum - padding,
            maxValue = maximum + padding
        )
    }

    fun yForValue(value: Float, bounds: ChartBounds, heightPx: Float): Float {
        val height = heightPx.coerceAtLeast(0f)
        val valueRange = bounds.maxValue - bounds.minValue
        if (valueRange <= 0f) return 0f

        return ((bounds.maxValue - value) / valueRange * height).coerceIn(0f, height)
    }

    fun xForTime(
        timestampMillis: Long,
        startMillis: Long,
        endMillis: Long,
        widthPx: Float
    ): Float {
        val width = widthPx.coerceAtLeast(0f)
        if (endMillis <= startMillis) return 0f

        val fraction = (timestampMillis - startMillis).toDouble() /
            (endMillis - startMillis).toDouble()
        return (fraction * width).toFloat().coerceIn(0f, width)
    }
}
