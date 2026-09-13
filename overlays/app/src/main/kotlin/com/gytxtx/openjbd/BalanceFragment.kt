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
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BalanceFragment : Fragment() {
    @Inject lateinit var repository: BmsRepository

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

    private val cellColorAccent by lazy { requireContext().getColor(R.color.accent) }
    private val cellColorPrimary by lazy { requireContext().getColor(R.color.primary) }
    private val cellColorNormal by lazy { requireContext().getColor(R.color.cell_voltage_normal) }
    private val cellColorBalancing by lazy { requireContext().getColor(R.color.accent_dark) }
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderState(it) }
            }
        }
    }

    private fun renderState(snapshot: BmsUiState) {
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
        val diagnostics = BalanceCellDiagnostics.analyze(voltages.cells, balanceStates)
        val readout = BalanceReadoutResolver.resolve(
            balanceStates = balanceStates,
            visibleCellCount = diagnostics.size,
            hasBalanceCurrent = basicInfo?.hasBalanceCurrent == true,
            balanceCurrentA = basicInfo?.balanceCurrentA ?: 0f
        )
        if (basicInfo == null) {
            balanceSummaryText.visibility = View.GONE
        } else {
            balanceSummaryText.visibility = View.VISIBLE
            balanceSummaryText.setTextIfChanged(requireContext().balanceSummary(basicInfo))
        }

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
        val progress = row.findViewById<LinearProgressIndicator>(R.id.progress_cell)
        val suffix = if (diagnostic.isBalancing) {
            getString(R.string.cell_balancing_suffix)
        } else {
            ""
        }

        label.setTextIfChanged(getString(R.string.cell_label, diagnostic.cellNumber) + suffix)
        label.setTextColor(if (diagnostic.isBalancing) cellColorBalancing else cellLabelColorNormal)
        value.setTextIfChanged(getString(R.string.format_value_voltage_3, diagnostic.voltage))
        progress.setProgressCompat(diagnostic.progress, false)
        progress.setIndicatorColor(
            when (diagnostic.highlight) {
                CellHighlight.HIGHEST -> cellColorAccent
                CellHighlight.LOWEST -> cellColorPrimary
                CellHighlight.NORMAL -> cellColorNormal
            }
        )
    }

    private fun showEmptyContent(connected: Boolean) {
        content.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
        emptyBodyText.setText(if (connected) R.string.empty_cells_connected else R.string.empty_cells)
        cellList.removeAllViews()
    }
}
