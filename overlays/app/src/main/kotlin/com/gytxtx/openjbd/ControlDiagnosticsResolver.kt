package com.gytxtx.openjbd

import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState

internal enum class ControlDiagnosticsSectionType {
    CONNECTION,
    DEVICE,
    EXTENSION,
    REFRESH
}

internal data class ControlDiagnosticsRow(
    val labelStringResId: Int,
    val value: String
)

internal data class ControlDiagnosticsSection(
    val type: ControlDiagnosticsSectionType,
    val rows: List<ControlDiagnosticsRow>
)

internal data class ControlDiagnosticsContent(
    val hasDeviceData: Boolean,
    val sections: List<ControlDiagnosticsSection>
)

internal class ControlDiagnosticsValueFormatter(
    val unread: String,
    val disconnected: String,
    val current: (Float) -> String,
    val power: (Float) -> String,
    val integer: (Int) -> String,
    val connectionState: (ConnectionState, Boolean, String?) -> String,
    val updatedAt: (Long) -> String
)

internal object ControlDiagnosticsResolver {
    fun resolve(
        snapshot: BmsUiState,
        formatter: ControlDiagnosticsValueFormatter
    ): ControlDiagnosticsContent {
        // Discard any stale device payload after a disconnect. Connection identity is retained
        // separately because it still helps identify a reconnect target.
        val basicInfo = snapshot.basicInfo.takeIf { snapshot.connected }
        val deviceInfo = snapshot.deviceInfo.takeIf { snapshot.connected }

        val connectionRows = listOf(
            ControlDiagnosticsRow(
                R.string.control_diagnostics_connection_state,
                formatter.connectionState(
                    snapshot.connectionState,
                    snapshot.connected,
                    snapshot.status
                )
            ),
            ControlDiagnosticsRow(
                R.string.param_bluetooth_name,
                connectionIdentity(snapshot.deviceName, snapshot.connected, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_device_address,
                connectionIdentity(snapshot.deviceAddress, snapshot.connected, formatter)
            )
        )

        val deviceRows = mutableListOf(
            ControlDiagnosticsRow(
                R.string.param_bms_version,
                valueOrUnread(basicInfo?.softwareVersion, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_bms_model,
                valueOrUnread(deviceInfo?.bmsModel, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_manufacturer,
                valueOrUnread(deviceInfo?.manufacturer, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_serial_number,
                valueOrUnread(deviceInfo?.serialNumber, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_barcode,
                valueOrUnread(deviceInfo?.barcode, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_battery_model,
                valueOrUnread(deviceInfo?.batteryModel, formatter)
            ),
            ControlDiagnosticsRow(
                R.string.param_cell_count,
                basicInfo?.let { formatter.integer(it.cellCount) } ?: formatter.unread
            ),
            ControlDiagnosticsRow(
                R.string.param_ntc_count,
                basicInfo?.let { formatter.integer(it.ntcCount) } ?: formatter.unread
            )
        )
        if (deviceInfo != null) {
            if (deviceInfo.ratedChargeCurrentA > 0f) {
                deviceRows += ControlDiagnosticsRow(
                    R.string.detail_rated_charge_current,
                    formatter.current(deviceInfo.ratedChargeCurrentA)
                )
            }
            if (deviceInfo.ratedDischargeCurrentA > 0f) {
                deviceRows += ControlDiagnosticsRow(
                    R.string.detail_rated_discharge_current,
                    formatter.current(deviceInfo.ratedDischargeCurrentA)
                )
            }
            if (deviceInfo.ratedDischargePowerW > 0f) {
                deviceRows += ControlDiagnosticsRow(
                    R.string.detail_rated_discharge_power,
                    formatter.power(deviceInfo.ratedDischargePowerW)
                )
            }
        }

        val extensionRows = mutableListOf<ControlDiagnosticsRow>()
        if (basicInfo?.hasExtendedInfo == true) {
            extensionRows += ControlDiagnosticsRow(
                R.string.param_extension_marker,
                formatter.integer(basicInfo.extensionMarker)
            )
            extensionRows += ControlDiagnosticsRow(
                R.string.param_alter,
                formatter.integer(basicInfo.alter)
            )
        }
        if (basicInfo?.hasBalanceCurrent == true) {
            extensionRows += ControlDiagnosticsRow(
                R.string.param_balance_current,
                formatter.current(basicInfo.balanceCurrentA)
            )
        }

        val updatedAtValue = if (snapshot.updatedAtMillis > 0L) {
            formatter.updatedAt(snapshot.updatedAtMillis)
        } else {
            formatter.unread
        }

        val sections = mutableListOf(
            ControlDiagnosticsSection(ControlDiagnosticsSectionType.CONNECTION, connectionRows),
            ControlDiagnosticsSection(ControlDiagnosticsSectionType.DEVICE, deviceRows)
        )
        if (extensionRows.isNotEmpty()) {
            sections += ControlDiagnosticsSection(
                ControlDiagnosticsSectionType.EXTENSION,
                extensionRows
            )
        }
        sections += ControlDiagnosticsSection(
            ControlDiagnosticsSectionType.REFRESH,
            listOf(
                ControlDiagnosticsRow(R.string.detail_last_updated, updatedAtValue)
            )
        )

        return ControlDiagnosticsContent(
            hasDeviceData = basicInfo != null || deviceInfo?.hasAnyField() == true,
            sections = sections
        )
    }

    private fun connectionIdentity(
        value: String?,
        connected: Boolean,
        formatter: ControlDiagnosticsValueFormatter
    ): String = value?.takeIf { it.isNotBlank() }
        ?: if (connected) formatter.unread else formatter.disconnected

    private fun valueOrUnread(
        value: String?,
        formatter: ControlDiagnosticsValueFormatter
    ): String = value?.takeIf { it.isNotBlank() } ?: formatter.unread
}
