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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.maintenance.MaintenanceGate
import com.gytxtx.openjbd.settings.BackupRestoreDialogFragment
import com.gytxtx.openjbd.settings.SettingsGroup
import com.gytxtx.openjbd.settings.SettingsGroupDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ControlFragment : Fragment() {
    @Inject lateinit var repository: BmsRepository

    private val maintenanceGate = MaintenanceGate()
    private lateinit var lockedContent: View
    private lateinit var unlockedContent: View
    private lateinit var targetName: TextView
    private lateinit var targetAddress: TextView
    private lateinit var diagnosticsPanel: LinearLayout
    private lateinit var diagnosticsEmpty: TextView
    private lateinit var diagnosticsSectionContainers: Map<ControlDiagnosticsSectionType, View>
    private lateinit var diagnosticsSectionLists: Map<ControlDiagnosticsSectionType, LinearLayout>
    private var diagnosticsExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_control, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lockedContent = view.findViewById(R.id.control_locked_content)
        unlockedContent = view.findViewById(R.id.control_unlocked_content)
        targetName = view.findViewById(R.id.control_target_name)
        targetAddress = view.findViewById(R.id.control_target_address)
        diagnosticsPanel = view.findViewById(R.id.control_diagnostics_panel)
        diagnosticsEmpty = view.findViewById(R.id.control_diagnostics_empty)
        diagnosticsSectionContainers = mapOf(
            ControlDiagnosticsSectionType.CONNECTION to
                view.findViewById(R.id.control_diagnostics_connection_section),
            ControlDiagnosticsSectionType.DEVICE to
                view.findViewById(R.id.control_diagnostics_device_section),
            ControlDiagnosticsSectionType.EXTENSION to
                view.findViewById(R.id.control_diagnostics_extension_section),
            ControlDiagnosticsSectionType.REFRESH to
                view.findViewById(R.id.control_diagnostics_refresh_section)
        )
        diagnosticsSectionLists = mapOf(
            ControlDiagnosticsSectionType.CONNECTION to
                view.findViewById(R.id.list_control_diagnostics_connection),
            ControlDiagnosticsSectionType.DEVICE to
                view.findViewById(R.id.list_control_diagnostics_device),
            ControlDiagnosticsSectionType.EXTENSION to
                view.findViewById(R.id.list_control_diagnostics_extension),
            ControlDiagnosticsSectionType.REFRESH to
                view.findViewById(R.id.list_control_diagnostics_refresh)
        )

        view.findViewById<View>(R.id.control_unlock_button).setOnClickListener {
            showUnlockConfirmation()
        }
        view.findViewById<View>(R.id.control_relock_button).setOnClickListener {
            maintenanceGate.relock()
            renderGateState()
        }
        view.findViewById<View>(R.id.control_diagnostics_row).setOnClickListener {
            diagnosticsExpanded = !diagnosticsExpanded
            diagnosticsPanel.visibility = if (diagnosticsExpanded) View.VISIBLE else View.GONE
            if (diagnosticsExpanded) renderDiagnostics(repository.getSnapshot())
        }
        mapOf(
            R.id.control_balance_settings_row to SettingsGroup.BALANCE,
            R.id.control_protection_row to SettingsGroup.PROTECTION,
            R.id.control_temperature_row to SettingsGroup.TEMPERATURE,
            R.id.control_capacity_row to SettingsGroup.CAPACITY,
            R.id.control_mos_row to SettingsGroup.MOS
        ).forEach { (rowId, group) ->
            view.findViewById<View>(rowId).setOnClickListener {
                SettingsGroupDialogFragment.newInstance(group)
                    .show(parentFragmentManager, "settings-${group.name}")
            }
        }
        view.findViewById<View>(R.id.control_backup_restore_row).setOnClickListener {
            BackupRestoreDialogFragment()
                .show(parentFragmentManager, "settings-backup-restore")
        }

        renderGateState()
        renderState(repository.getSnapshot())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderState(it) }
            }
        }
    }

    private fun showUnlockConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.control_unlock_dialog_title)
            .setMessage(R.string.control_unlock_dialog_message)
            .setNegativeButton(R.string.control_cancel) { _, _ ->
                maintenanceGate.requestUnlock(confirmed = false)
            }
            .setPositiveButton(R.string.control_unlock_confirm) { _, _ ->
                maintenanceGate.requestUnlock(confirmed = true)
                renderGateState()
            }
            .show()
    }

    private fun renderGateState() {
        if (!maintenanceGate.isUnlocked) {
            diagnosticsExpanded = false
            diagnosticsPanel.visibility = View.GONE
        }
        lockedContent.visibility = if (maintenanceGate.isUnlocked) View.GONE else View.VISIBLE
        unlockedContent.visibility = if (maintenanceGate.isUnlocked) View.VISIBLE else View.GONE
    }

    private fun renderState(snapshot: BmsUiState) {
        renderTarget(snapshot)
        renderDiagnostics(snapshot)
    }

    private fun renderTarget(snapshot: BmsUiState) {
        val connectedName = snapshot.deviceName?.takeIf { it.isNotBlank() }
        val connectedAddress = snapshot.deviceAddress?.takeIf { it.isNotBlank() }
        val prefs = AppSettings.prefs(requireContext())
        val savedAddress = prefs.getString(AppSettings.PREF_LAST_DEVICE_ADDRESS, "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
        val savedName = prefs.getString(AppSettings.PREF_LAST_DEVICE_NAME, savedAddress)
            .orEmpty()
            .takeIf { it.isNotBlank() }

        val name: String
        val address: String?
        if (snapshot.connected && (connectedName != null || connectedAddress != null)) {
            name = connectedName ?: connectedAddress!!
            address = connectedAddress
        } else if (savedAddress != null) {
            name = savedName ?: savedAddress
            address = savedAddress
        } else {
            name = getString(R.string.bms_selector_disconnected)
            address = null
        }

        targetName.setTextIfChanged(name)
        targetAddress.visibility = if (address == null) View.GONE else View.VISIBLE
        if (address != null) targetAddress.setTextIfChanged(address)
    }

    private fun renderDiagnostics(snapshot: BmsUiState) {
        if (!::diagnosticsSectionLists.isInitialized) return
        val content = ControlDiagnosticsResolver.resolve(snapshot, diagnosticsValueFormatter())
        diagnosticsEmpty.visibility = if (content.hasDeviceData) View.GONE else View.VISIBLE

        val sectionsByType = content.sections.associateBy { it.type }
        ControlDiagnosticsSectionType.values().forEach { type ->
            val section = sectionsByType[type]
            diagnosticsSectionContainers.getValue(type).visibility =
                if (section == null) View.GONE else View.VISIBLE
            if (section != null) diagnosticsSectionLists.getValue(type).renderRows(section.rows)
        }
    }

    private fun diagnosticsValueFormatter(): ControlDiagnosticsValueFormatter =
        ControlDiagnosticsValueFormatter(
            unread = getString(R.string.param_unread),
            disconnected = getString(R.string.bms_selector_disconnected),
            current = { getString(R.string.format_value_current_2, it) },
            power = { getString(R.string.format_value_power_1, it) },
            integer = { getString(R.string.format_value_integer, it) },
            connectionState = { state, connected, status ->
                status?.takeIf { it.isNotBlank() }
                    ?: if (connected) {
                        getString(R.string.bms_sheet_status_connected)
                    } else if (state == com.gytxtx.openjbd.data.ConnectionState.DISCONNECTED) {
                        getString(R.string.bms_selector_disconnected)
                    } else {
                        state.name.replace('_', ' ')
                    }
            },
            updatedAt = {
                SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).format(Date(it))
            }
        )

    private fun LinearLayout.renderRows(rows: List<ControlDiagnosticsRow>) {
        while (childCount < rows.size) {
            addView(LayoutInflater.from(context).inflate(R.layout.row_setting_item, this, false))
        }
        while (childCount > rows.size) {
            removeViewAt(childCount - 1)
        }
        rows.forEachIndexed { index, diagnosticsRow ->
            val row = getChildAt(index)
            row.isClickable = false
            row.isFocusable = false
            row.findViewById<ImageView>(R.id.img_setting_icon).apply {
                setImageResource(iconFor(diagnosticsRow.labelStringResId))
                setColorFilter(context.getColor(R.color.icon_default))
            }
            row.findViewById<TextView>(R.id.txt_setting_title)
                .setText(diagnosticsRow.labelStringResId)
            row.findViewById<TextView>(R.id.txt_setting_subtitle)
                .setTextIfChanged(diagnosticsRow.value)
            row.findViewById<View>(R.id.switch_setting_action).visibility = View.GONE
            row.findViewById<View>(R.id.img_setting_chevron).visibility = View.GONE
        }
    }

    private fun iconFor(labelStringResId: Int): Int = when (labelStringResId) {
        R.string.control_diagnostics_connection_state,
        R.string.param_bluetooth_name,
        R.string.param_device_address -> R.drawable.ic_bluetooth_searching_24

        R.string.param_cell_count,
        R.string.param_ntc_count,
        R.string.param_balance_current -> R.drawable.ic_battery_4_bar_24

        R.string.detail_rated_charge_current,
        R.string.detail_rated_discharge_current,
        R.string.detail_rated_discharge_power -> R.drawable.ic_bolt_24

        R.string.detail_last_updated -> R.drawable.ic_refresh_24
        else -> R.drawable.ic_info_24
    }

    private companion object {
        const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
    }
}
