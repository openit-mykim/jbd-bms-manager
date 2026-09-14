package com.gytxtx.openjbd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gytxtx.openjbd.balance.BalanceCellDiagnostic
import com.gytxtx.openjbd.balance.BalanceCellDiagnostics
import com.gytxtx.openjbd.balance.BalanceReadoutResolver
import com.gytxtx.openjbd.balance.CellHighlight
import com.gytxtx.openjbd.balance.CellSafetyBand
import com.gytxtx.openjbd.balance.CellThresholdResolver
import com.gytxtx.openjbd.balance.CellThresholds
import com.gytxtx.openjbd.balance.ThresholdSource
import com.gytxtx.openjbd.balance.voltsToMillivolts
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BalanceFragment : Fragment() {
    @Inject lateinit var repository: BmsRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var placeholder: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var cellList: LinearLayout
    private lateinit var cellCountText: TextView
    private lateinit var balanceSummaryText: TextView
    private lateinit var activeBalanceCountText: TextView
    private lateinit var balanceCurrentCard: View
    private lateinit var balanceCurrentText: TextView
    private lateinit var emptyBodyText: TextView
    private lateinit var cellMinText: TextView
    private lateinit var cellMaxText: TextView
    private lateinit var cellAverageText: TextView
    private lateinit var cellDeltaText: TextView

    private val cellBandNormal by lazy { requireContext().getColor(R.color.cell_band_normal) }
    private val cellBandCaution by lazy { requireContext().getColor(R.color.cell_band_caution) }
    private val cellBandDanger by lazy { requireContext().getColor(R.color.cell_band_danger) }
    private val cellLabelColorNormal by lazy { requireContext().getColor(R.color.text_secondary) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_balance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        placeholder = view.findViewById(R.id.placeholder_balance)
        content = view.findViewById(R.id.content_balance)
        cellList = view.findViewById(R.id.list_balance_cells)
        emptyBodyText = view.findViewById(R.id.txt_balance_empty_body)
        cellCountText = view.findViewById(R.id.txt_balance_cell_count)
        balanceSummaryText = view.findViewById(R.id.txt_balance_summary)
        activeBalanceCountText = view.findViewById(R.id.txt_balance_active_count)
        balanceCurrentCard = view.findViewById(R.id.card_balance_current)
        balanceCurrentText = view.findViewById(R.id.txt_balance_current)
        cellMinText = view.findViewById(R.id.txt_balance_cell_min)
        cellMaxText = view.findViewById(R.id.txt_balance_cell_max)
        cellAverageText = view.findViewById(R.id.txt_balance_cell_average)
        cellDeltaText = view.findViewById(R.id.txt_balance_cell_delta)
        configureLegend()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                combine(repository.uiState, settingsRepository.state) { snapshot, settingsState ->
                    snapshot to CellThresholdResolver.from(settingsState)
                }.collect { (snapshot, thresholds) ->
                    renderState(snapshot, thresholds)
                }
            }
        }
    }

    private fun renderState(snapshot: BmsUiState, thresholds: CellThresholds) {
        val voltages = snapshot.cellVoltages
        if (!snapshot.connected || voltages == null || voltages.cells.isEmpty()) {
            showEmptyContent(snapshot.connected)
            return
        }

        placeholder.visibility = View.GONE
        content.visibility = View.VISIBLE
        cellCountText.setTextIfChanged(
            getString(
                R.string.format_value_integer,
                snapshot.basicInfo?.cellCount ?: voltages.cells.size
            )
        )

        val basicInfo = snapshot.basicInfo
        val balanceStates = basicInfo?.balanceStates ?: BooleanArray(0)
        val diagnostics = BalanceCellDiagnostics.analyze(
            cells = voltages.cells,
            balanceStates = balanceStates,
            thresholds = thresholds
        )
        val readout = BalanceReadoutResolver.resolve(
            balanceStates = balanceStates,
            visibleCellCount = diagnostics.size,
            hasBalanceCurrent = basicInfo?.hasBalanceCurrent == true,
            balanceCurrentA = basicInfo?.balanceCurrentA ?: 0f
        )
        val thresholdSource = getString(
            when (thresholds.source) {
                ThresholdSource.MEASURED -> R.string.balance_threshold_source_measured
                ThresholdSource.DEFAULT -> R.string.balance_threshold_source_default
            },
            thresholds.underVoltageMv,
            thresholds.overVoltageMv
        )
        balanceSummaryText.visibility = View.VISIBLE
        balanceSummaryText.setTextIfChanged(
            if (basicInfo == null) {
                thresholdSource
            } else {
                getString(
                    R.string.balance_summary_with_threshold_source,
                    requireContext().balanceSummary(basicInfo),
                    thresholdSource
                )
            }
        )

        activeBalanceCountText.visibility = if (readout.activeBalancingCellCount > 0) {
            activeBalanceCountText.setTextIfChanged(
                resources.getQuantityString(
                    R.plurals.balance_active_cell_count,
                    readout.activeBalancingCellCount,
                    readout.activeBalancingCellCount
                )
            )
            View.VISIBLE
        } else {
            View.GONE
        }

        balanceCurrentCard.visibility = if (readout.balanceCurrentA != null) {
            balanceCurrentText.setTextIfChanged(
                getString(R.string.format_value_current_2, readout.balanceCurrentA)
            )
            View.VISIBLE
        } else {
            View.GONE
        }

        cellMinText.setTextIfChanged(getString(R.string.format_value_voltage_3, voltages.min))
        cellMaxText.setTextIfChanged(getString(R.string.format_value_voltage_3, voltages.max))
        cellAverageText.setTextIfChanged(getString(R.string.format_value_voltage_3, voltages.average))
        cellDeltaText.setTextIfChanged(getString(R.string.format_value_voltage_3, voltages.delta))

        if (cellList.childCount != diagnostics.size) {
            cellList.removeAllViews()
            repeat(diagnostics.size) {
                cellList.addView(
                    LayoutInflater.from(requireContext()).inflate(
                        R.layout.row_cell_voltage,
                        cellList,
                        false
                    )
                )
            }
        }
        diagnostics.forEachIndexed { index, diagnostic ->
            updateCellVoltageRow(cellList.getChildAt(index), diagnostic)
        }
    }

    private fun updateCellVoltageRow(row: View, diagnostic: BalanceCellDiagnostic) {
        val label = row.findViewById<TextView>(R.id.txt_cell_label)
        val value = row.findViewById<TextView>(R.id.txt_cell_value)
        val valueMv = row.findViewById<TextView>(R.id.txt_cell_value_mv)
        val progress = row.findViewById<LinearProgressIndicator>(R.id.progress_cell)
        val statusSuffix = when {
            diagnostic.isBalancing -> getString(R.string.balance_badge_balancing_suffix)
            diagnostic.highlight == CellHighlight.HIGHEST ->
                getString(R.string.balance_badge_highest_suffix)
            diagnostic.highlight == CellHighlight.LOWEST ->
                getString(R.string.balance_badge_lowest_suffix)
            else -> ""
        }
        val bandLabel = getString(diagnostic.band.labelResource())

        label.setTextIfChanged(
            getString(
                R.string.balance_cell_label_with_band,
                getString(R.string.cell_label, diagnostic.cellNumber),
                bandLabel,
                statusSuffix
            )
        )
        label.setTextColor(cellLabelColorNormal)
        value.setTextIfChanged(getString(R.string.format_value_voltage_3, diagnostic.voltage))
        valueMv.setTextIfChanged(
            getString(R.string.format_value_voltage_mv, voltsToMillivolts(diagnostic.voltage))
        )
        progress.setProgressCompat(diagnostic.progress, false)
        progress.setIndicatorColor(diagnostic.band.color())
    }

    private fun configureLegend() {
        val listParent = cellList.parent as? LinearLayout ?: return
        val cellListIndex = listParent.indexOfChild(cellList)
        val legendRow = listParent.getChildAt(cellListIndex - 1) as? LinearLayout ?: return
        val legendItems = listOf(
            Triple(R.string.balance_band_legend_normal, cellBandNormal, 0),
            Triple(R.string.balance_band_legend_caution, cellBandCaution, 1),
            Triple(R.string.balance_band_legend_danger, cellBandDanger, 2)
        )
        legendItems.forEach { (textResource, color, index) ->
            (legendRow.getChildAt(index) as? TextView)?.apply {
                setText(textResource)
                setTextColor(color)
            }
        }

        val badgeLegend = TextView(requireContext()).apply {
            setText(R.string.balance_badge_legend)
            setTextAppearance(R.style.TextAppearance_OpenJbd_Supporting)
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_8))
        }
        listParent.addView(badgeLegend, cellListIndex)
    }

    private fun CellSafetyBand.labelResource() = when (this) {
        CellSafetyBand.NORMAL -> R.string.balance_band_label_normal
        CellSafetyBand.CAUTION -> R.string.balance_band_label_caution
        CellSafetyBand.DANGER -> R.string.balance_band_label_danger
    }

    private fun CellSafetyBand.color() = when (this) {
        CellSafetyBand.NORMAL -> cellBandNormal
        CellSafetyBand.CAUTION -> cellBandCaution
        CellSafetyBand.DANGER -> cellBandDanger
    }

    private fun showEmptyContent(connected: Boolean) {
        content.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
        emptyBodyText.setText(if (connected) R.string.empty_cells_connected else R.string.empty_cells)
        cellList.removeAllViews()
    }
}
