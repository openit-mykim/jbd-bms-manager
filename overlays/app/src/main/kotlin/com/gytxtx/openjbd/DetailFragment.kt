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
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DetailFragment : Fragment() {
    @Inject lateinit var repository: BmsRepository

    private lateinit var placeholder: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var sectionLists: Map<DetailSectionType, LinearLayout>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        placeholder = view.findViewById(R.id.placeholder_detail)
        content = view.findViewById(R.id.section_detail_content)
        sectionLists = mapOf(
            DetailSectionType.BATTERY to view.findViewById(R.id.list_detail_battery),
            DetailSectionType.DEVICE to view.findViewById(R.id.list_detail_device),
            DetailSectionType.CONNECTION to view.findViewById(R.id.list_detail_connection),
            DetailSectionType.STATUS to view.findViewById(R.id.list_detail_status),
            DetailSectionType.REFRESH to view.findViewById(R.id.list_detail_refresh)
        )
        renderState(repository.getSnapshot())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderState(it) }
            }
        }
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
    }
}
