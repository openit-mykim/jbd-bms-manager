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
import com.gytxtx.openjbd.maintenance.DeviceIdentity
import com.gytxtx.openjbd.maintenance.WriteAuthorization
import com.google.android.material.button.MaterialButton
import com.gytxtx.openjbd.settings.BackupRestoreDialogFragment
import com.gytxtx.openjbd.settings.CalibrationDialogFragment
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
    @Inject lateinit var writeAuthorization: WriteAuthorization
    @Inject lateinit var deviceRegistry: com.gytxtx.openjbd.registry.BmsDeviceRegistry

    private lateinit var writePermissionStatus: TextView
    private lateinit var writePermissionButton: MaterialButton
    private lateinit var lockedContent: View
    private lateinit var unlockedContent: View
    private lateinit var targetName: TextView
    private lateinit var targetAddress: TextView
    private lateinit var targetStatus: TextView
    private var serviceRecordCount: Int? = null
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
        targetStatus = view.findViewById(R.id.control_target_status)
        writePermissionStatus = view.findViewById(R.id.control_write_permission_status)
        writePermissionButton = view.findViewById(R.id.control_write_permission_button)
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
            writeAuthorization.relock()
            renderGateState()
        }
        view.findViewById<View>(R.id.control_diagnostics_row).setOnClickListener {
            diagnosticsExpanded = !diagnosticsExpanded
            diagnosticsPanel.visibility = if (diagnosticsExpanded) View.VISIBLE else View.GONE
            if (diagnosticsExpanded) renderDiagnostics(repository.getSnapshot())
        }
        view.findViewById<View>(R.id.control_calibration_row).setOnClickListener {
            CalibrationDialogFragment.newInstance()
                .show(parentFragmentManager, "calibration")
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                writeAuthorization.version.collect {
                    refreshServiceRecordCount()
                    renderGateState()
                    renderWritePermission()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                deviceRegistry.state.collect {
                    refreshServiceRecordCount()
                    renderTarget(repository.getSnapshot())
                }
            }
        }
    }

    private fun showUnlockConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.control_unlock_dialog_title)
            .setMessage(R.string.control_unlock_dialog_message)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.control_unlock_confirm) { _, _ ->
                // The unlock is bound to the device connected right now.
                writeAuthorization.unlock(repository.getSnapshot().deviceAddress)
                renderGateState()
            }
            .show()
    }

    private fun isUnlocked(): Boolean =
        writeAuthorization.isUnlockedFor(repository.getSnapshot().deviceAddress)

    private fun renderGateState() {
        val unlocked = isUnlocked()
        if (!unlocked) {
            diagnosticsExpanded = false
            diagnosticsPanel.visibility = View.GONE
        }
        lockedContent.visibility = if (unlocked) View.GONE else View.VISIBLE
        unlockedContent.visibility = if (unlocked) View.VISIBLE else View.GONE
    }

    private fun renderState(snapshot: BmsUiState) {
        renderGateState()
        renderTarget(snapshot)
        renderWritePermission()
        renderDiagnostics(snapshot)
    }

    private fun renderWritePermission() {
        if (!::writePermissionStatus.isInitialized) return
        val identity = writeAuthorization.currentIdentity()
        when {
            identity == null -> {
                writePermissionStatus.setTextIfChanged(getString(R.string.control_write_permission_no_identity))
                writePermissionButton.visibility = View.GONE
            }
            writeAuthorization.isVerifiedProfile(identity) -> {
                writePermissionStatus.setTextIfChanged(getString(R.string.control_write_permission_verified))
                writePermissionButton.visibility = View.GONE
            }
            writeAuthorization.hasConsent(identity) -> {
                writePermissionStatus.setTextIfChanged(
                    getString(R.string.control_write_permission_experimental, identity.bmsModel, identity.firmwareVersion)
                )
                writePermissionButton.setText(R.string.control_write_permission_revoke)
                writePermissionButton.visibility = View.VISIBLE
                writePermissionButton.setOnClickListener { writeAuthorization.revokeConsent(identity) }
            }
            else -> {
                writePermissionStatus.setTextIfChanged(
                    getString(R.string.control_write_permission_read_only, identity.bmsModel, identity.firmwareVersion)
                )
                writePermissionButton.setText(R.string.control_write_permission_grant)
                writePermissionButton.visibility = View.VISIBLE
                writePermissionButton.setOnClickListener { showConsentConfirmation(identity) }
            }
        }
    }

    private fun showConsentConfirmation(identity: DeviceIdentity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.control_write_consent_title)
            .setMessage(
                getString(
                    R.string.control_write_consent_message,
                    identity.address,
                    identity.bmsModel,
                    identity.firmwareVersion
                )
            )
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.control_write_consent_confirm) { _, _ ->
                // Re-check: the connected device must still be the one shown in the dialog.
                if (writeAuthorization.currentIdentity() == identity) {
                    writeAuthorization.grantConsent(identity)
                }
            }
            .show()
    }

    /**
     * Target card: the explicitly selected registered BMS (alias + address). When a BMS is
     * connected but is not that device, or its identity differs from the registered one, the
     * card says so: writes and restores are refused at the repository layer (D022).
     */
    private fun renderTarget(snapshot: BmsUiState) {
        if (!::targetStatus.isInitialized) return
        val selected = deviceRegistry.selected
        targetName.setTextIfChanged(selected?.displayName ?: getString(R.string.bms_selector_disconnected))
        targetAddress.visibility = if (selected == null) View.GONE else View.VISIBLE
        if (selected != null) {
            val records = serviceRecordCount
            targetAddress.setTextIfChanged(
                if (records == null) selected.address
                else getString(R.string.control_target_address_records, selected.address, records)
            )
        }
        val statusRes = if (!snapshot.connected) null else when (writeAuthorization.targetCheck()) {
            com.gytxtx.openjbd.registry.DeviceTargetCheck.Result.MATCH,
            com.gytxtx.openjbd.registry.DeviceTargetCheck.Result.IDENTITY_UNREAD -> null
            com.gytxtx.openjbd.registry.DeviceTargetCheck.Result.NO_SELECTION -> R.string.control_target_no_selection
            com.gytxtx.openjbd.registry.DeviceTargetCheck.Result.ADDRESS_MISMATCH -> R.string.control_target_address_mismatch
            com.gytxtx.openjbd.registry.DeviceTargetCheck.Result.MODEL_UNREAD -> R.string.control_target_model_unread
            com.gytxtx.openjbd.registry.DeviceTargetCheck.Result.MODEL_MISMATCH -> R.string.control_target_model_mismatch
        }
        targetStatus.visibility = if (statusRes == null) View.GONE else View.VISIBLE
        if (statusRes != null) targetStatus.setTextIfChanged(getString(statusRes))
    }

    /** Phase D: calibration / service records (audit entries) of the selected BMS only. */
    private fun refreshServiceRecordCount() {
        val key = deviceRegistry.selected?.address
        val count = key?.let { selectedKey ->
            runCatching {
                com.gytxtx.openjbd.registry.DeviceServiceRecords
                    .forDevice(writeAuthorization.auditLog.entries(), selectedKey).size
            }.getOrNull()
        }
        if (count != serviceRecordCount) {
            serviceRecordCount = count
            if (::targetStatus.isInitialized) renderTarget(repository.getSnapshot())
        }
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
                DisplayFormats.dateTime(it)
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

}
