package com.gytxtx.openjbd.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.gytxtx.openjbd.R

class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.progress_track)
        strokeWidth = density
    }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val averagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.primary)
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var buckets: List<HistoryBucket> = emptyList()
    private var chartBounds: ChartBounds? = null
    private var startMillis: Long = 0L
    private var endMillis: Long = 0L

    fun setBuckets(buckets: List<HistoryBucket>, bounds: ChartBounds?) {
        this.buckets = buckets
        chartBounds = bounds
        if (buckets.isEmpty()) {
            startMillis = 0L
            endMillis = 0L
        } else {
            var firstTimestamp = Long.MAX_VALUE
            var lastTimestamp = Long.MIN_VALUE
            for (bucket in buckets) {
                firstTimestamp = minOf(firstTimestamp, bucket.startMillis)
                lastTimestamp = maxOf(lastTimestamp, bucket.startMillis)
            }
            startMillis = firstTimestamp
            endMillis = lastTimestamp
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = chartBounds ?: return
        if (buckets.isEmpty()) return

        val chartWidth = width.toFloat()
        val chartHeight = height.toFloat()
        for (lineIndex in 0 until GRID_LINE_COUNT) {
            val y = chartHeight * lineIndex / (GRID_LINE_COUNT - 1).toFloat()
            canvas.drawLine(0f, y, chartWidth, y, gridPaint)
        }

        var hasPreviousAverage = false
        var previousX = 0f
        var previousY = 0f
        for (bucket in buckets) {
            val x = HistoryChartGeometry.xForTime(
                bucket.startMillis,
                startMillis,
                endMillis,
                chartWidth
            )
            val minimumY = HistoryChartGeometry.yForValue(
                bucket.minValue,
                bounds,
                chartHeight
            )
            val maximumY = HistoryChartGeometry.yForValue(
                bucket.maxValue,
                bounds,
                chartHeight
            )
            val averageY = HistoryChartGeometry.yForValue(
                bucket.averageValue,
                bounds,
                chartHeight
            )
            canvas.drawLine(x, maximumY, x, minimumY, rangePaint)
            if (hasPreviousAverage) {
                canvas.drawLine(previousX, previousY, x, averageY, averagePaint)
            } else {
                canvas.drawPoint(x, averageY, averagePaint)
                hasPreviousAverage = true
            }
            previousX = x
            previousY = averageY
        }
    }

    private companion object {
        const val GRID_LINE_COUNT = 4
    }
}
