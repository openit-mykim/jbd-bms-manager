package com.gytxtx.openjbd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButtonToggleGroup
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.history.ChartBounds
import com.gytxtx.openjbd.history.HistoryBucket
import com.gytxtx.openjbd.history.HistoryChartGeometry
import com.gytxtx.openjbd.history.HistoryChartView
import com.gytxtx.openjbd.history.HistoryMetric
import com.gytxtx.openjbd.history.HistoryPolicy
import com.gytxtx.openjbd.history.HistoryRange
import com.gytxtx.openjbd.history.HistoryStore
import com.gytxtx.openjbd.history.SqliteHistoryStore
import com.gytxtx.openjbd.history.valueOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HistoryStoreBindingModule {
    @Binds
    abstract fun bindHistoryStore(store: SqliteHistoryStore): HistoryStore
}

@AndroidEntryPoint
class DetailFragment : Fragment() {
    @Inject lateinit var repository: BmsRepository
    @Inject lateinit var historyStore: HistoryStore

    private lateinit var placeholder: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var sectionLists: Map<DetailSectionType, LinearLayout>
    private lateinit var historyChart: HistoryChartView
    private lateinit var historySummary: TextView
    private lateinit var historyEmpty: TextView
    private var selectedHistoryRange = HistoryRange.LAST_24H
    private var selectedHistoryMetric = HistoryMetric.SOC_PERCENT
    private var historyQueryJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        placeholder = view.findViewById(R.id.placeholder_detail)
        content = view.findViewById(R.id.section_detail_content)
        historyChart = view.findViewById(R.id.history_chart)
        historySummary = view.findViewById(R.id.history_summary)
        historyEmpty = view.findViewById(R.id.history_empty)
        sectionLists = mapOf(
            DetailSectionType.BATTERY to view.findViewById(R.id.list_detail_battery),
            DetailSectionType.DEVICE to view.findViewById(R.id.list_detail_device),
            DetailSectionType.CONNECTION to view.findViewById(R.id.list_detail_connection),
            DetailSectionType.STATUS to view.findViewById(R.id.list_detail_status),
            DetailSectionType.REFRESH to view.findViewById(R.id.list_detail_refresh)
        )
        configureHistoryControls(view)
        refreshHistory()
        renderState(repository.getSnapshot())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderState(it) }
            }
        }
    }

    override fun onDestroyView() {
        historyQueryJob?.cancel()
        historyQueryJob = null
        super.onDestroyView()
    }

    private fun configureHistoryControls(view: View) {
        view.findViewById<MaterialButtonToggleGroup>(R.id.history_range_group).apply {
            check(selectedHistoryRange.buttonId())
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                selectedHistoryRange = when (checkedId) {
                    R.id.history_range_7d -> HistoryRange.LAST_7D
                    else -> HistoryRange.LAST_24H
                }
                refreshHistory()
            }
        }
        view.findViewById<MaterialButtonToggleGroup>(R.id.history_metric_group).apply {
            check(selectedHistoryMetric.buttonId())
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                selectedHistoryMetric = when (checkedId) {
                    R.id.history_metric_pack -> HistoryMetric.PACK_VOLTS
                    R.id.history_metric_delta -> HistoryMetric.CELL_DELTA_MV
                    else -> HistoryMetric.SOC_PERCENT
                }
                refreshHistory()
            }
        }
    }

    private fun refreshHistory() {
        historyQueryJob?.cancel()
        val range = selectedHistoryRange
        val metric = selectedHistoryMetric
        historyQueryJob = viewLifecycleOwner.lifecycleScope.launch {
            val nowMillis = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                val samples = historyStore.query(range.startMillis(nowMillis), nowMillis)
                val buckets = HistoryPolicy.bucketize(
                    samples = samples,
                    valueOf = { sample -> valueOf(metric, sample) },
                    maxBuckets = MAX_BUCKETS
                )
                var minimum = Float.POSITIVE_INFINITY
                var maximum = Float.NEGATIVE_INFINITY
                var total = 0.0
                var sampleCount = 0
                for (sample in samples) {
                    val value = valueOf(metric, sample) ?: continue
                    minimum = minOf(minimum, value)
                    maximum = maxOf(maximum, value)
                    total += value.toDouble()
                    sampleCount += 1
                }
                HistoryLoadResult(
                    buckets = buckets,
                    bounds = HistoryChartGeometry.bounds(buckets),
                    sampleCount = sampleCount,
                    minimum = minimum.takeIf { sampleCount > 0 },
                    maximum = maximum.takeIf { sampleCount > 0 },
                    average = if (sampleCount > 0) {
                        (total / sampleCount).toFloat()
                    } else {
                        null
                    }
                )
            }
            renderHistory(range, metric, result)
        }
    }

    private fun renderHistory(
        range: HistoryRange,
        metric: HistoryMetric,
        result: HistoryLoadResult
    ) {
        historyChart.setBuckets(result.buckets, result.bounds)
        historyChart.contentDescription = getString(
            R.string.history_chart_description,
            getString(metric.labelResource()),
            getString(range.labelResource()),
            result.sampleCount
        )
        val hasSamples = result.sampleCount > 0
        historyEmpty.visibility = if (hasSamples) View.GONE else View.VISIBLE
        historySummary.visibility = if (hasSamples) View.VISIBLE else View.GONE
        if (hasSamples) {
            historySummary.setTextIfChanged(
                getString(
                    R.string.history_summary,
                    formatHistoryValue(metric, checkNotNull(result.minimum)),
                    formatHistoryValue(metric, checkNotNull(result.average)),
                    formatHistoryValue(metric, checkNotNull(result.maximum))
                )
            )
        }
    }

    private fun formatHistoryValue(metric: HistoryMetric, value: Float): String = when (metric) {
        HistoryMetric.SOC_PERCENT -> getString(R.string.format_value_percent, value.roundToInt())
        HistoryMetric.PACK_VOLTS -> getString(R.string.format_value_voltage_2, value)
        HistoryMetric.CELL_DELTA_MV ->
            getString(R.string.format_value_voltage_mv, value.roundToInt())
    }

    private fun HistoryRange.labelResource(): Int = when (this) {
        HistoryRange.LAST_24H -> R.string.history_range_24h
        HistoryRange.LAST_7D -> R.string.history_range_7d
    }

    private fun HistoryRange.buttonId(): Int = when (this) {
        HistoryRange.LAST_24H -> R.id.history_range_24h
        HistoryRange.LAST_7D -> R.id.history_range_7d
    }

    private fun HistoryMetric.labelResource(): Int = when (this) {
        HistoryMetric.SOC_PERCENT -> R.string.history_metric_soc
        HistoryMetric.PACK_VOLTS -> R.string.history_metric_pack
        HistoryMetric.CELL_DELTA_MV -> R.string.history_metric_delta
    }

    private fun HistoryMetric.buttonId(): Int = when (this) {
        HistoryMetric.SOC_PERCENT -> R.id.history_metric_soc
        HistoryMetric.PACK_VOLTS -> R.id.history_metric_pack
        HistoryMetric.CELL_DELTA_MV -> R.id.history_metric_delta
    }

    private fun renderState(snapshot: BmsUiState) {
        if (!::sectionLists.isInitialized) return
        val sections = DetailFieldResolver.resolve(snapshot, valueFormatter())
        val hasData = sections != null
        placeholder.visibility = if (hasData) View.GONE else View.VISIBLE
        content.visibility = if (hasData) View.VISIBLE else View.GONE
        if (sections == null) return

        sections.forEach { section ->
            sectionLists.getValue(section.type).renderRows(section.rows)
        }
    }

    private fun valueFormatter(): DetailValueFormatter {
        val ctx = requireContext()
        return DetailValueFormatter(
            unread = getString(R.string.param_unread),
            capacity = { getString(R.string.format_value_capacity_2, it) },
            current = { getString(R.string.format_value_current_2, it) },
            power = { getString(R.string.format_value_power_1, it) },
            percent = { getString(R.string.format_value_percent, it) },
            integer = { getString(R.string.format_value_integer, it) },
            temperatures = { temperatures ->
                if (temperatures.isEmpty()) {
                    getString(R.string.temperature_none)
                } else {
                    val unit = AppSettings.temperatureUnitLabel(ctx)
                    temperatures.mapIndexed { index, celsius ->
                        getString(
                            R.string.temperature_probe_item,
                            index + 1,
                            AppSettings.displayTemperature(ctx, celsius),
                            unit
                        )
                    }.joinToString(separator = "\n")
                }
            },
            onOff = { ctx.onOff(it) },
            balance = { ctx.balanceSummary(it) },
            protection = { ctx.protectionSummary(it) },
            updatedAt = {
                SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).format(Date(it))
            }
        )
    }

    private fun LinearLayout.renderRows(rows: List<DetailRow>) {
        while (childCount < rows.size) {
            addView(LayoutInflater.from(context).inflate(R.layout.row_setting_item, this, false))
        }
        while (childCount > rows.size) {
            removeViewAt(childCount - 1)
        }
        rows.forEachIndexed { index, detailRow ->
            val row = getChildAt(index)
            row.isClickable = false
            row.isFocusable = false
            row.findViewById<ImageView>(R.id.img_setting_icon).apply {
                setImageResource(iconFor(detailRow.labelStringResId))
                setColorFilter(context.getColor(R.color.icon_default))
            }
            row.findViewById<TextView>(R.id.txt_setting_title)
                .setText(detailRow.labelStringResId)
            row.findViewById<TextView>(R.id.txt_setting_subtitle)
                .setTextIfChanged(detailRow.value)
            row.findViewById<View>(R.id.switch_setting_action).visibility = View.GONE
            row.findViewById<View>(R.id.img_setting_chevron).visibility = View.GONE
        }
    }

    private fun iconFor(labelStringResId: Int): Int = when (labelStringResId) {
        R.string.param_soc,
        R.string.param_remaining_capacity,
        R.string.param_nominal_capacity,
        R.string.param_learn_capacity,
        R.string.param_cycle_count,
        R.string.param_cell_count,
        R.string.param_ntc_count,
        R.string.param_balance_state,
        R.string.param_balance_current -> R.drawable.ic_battery_4_bar_24

        R.string.param_bluetooth_name,
        R.string.param_device_address -> R.drawable.ic_bluetooth_searching_24

        R.string.metric_temperature -> R.drawable.ic_thermostat_24

        R.string.param_charge_mos,
        R.string.param_discharge_mos,
        R.string.detail_rated_charge_current,
        R.string.detail_rated_discharge_current,
        R.string.detail_rated_discharge_power -> R.drawable.ic_bolt_24

        R.string.param_protection_state -> R.drawable.ic_widgets_outline_24
        R.string.detail_last_updated -> R.drawable.ic_refresh_24
        else -> R.drawable.ic_info_24
    }

    private companion object {
        const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
        const val MAX_BUCKETS = 120
    }

    private data class HistoryLoadResult(
        val buckets: List<HistoryBucket>,
        val bounds: ChartBounds?,
        val sampleCount: Int,
        val minimum: Float?,
        val maximum: Float?,
        val average: Float?
    )
}
