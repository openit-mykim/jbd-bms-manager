package com.gytxtx.openjbd

import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState
import com.gytxtx.openjbd.protocol.JbdBasicInfo
import com.gytxtx.openjbd.protocol.JbdDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlDiagnosticsResolverTest {
    @Test
    fun fullSnapshotBuildsAllReadOnlyDiagnosticSections() {
        val content = ControlDiagnosticsResolver.resolve(
            snapshot(
                basicInfo = basicInfo(hasExtendedInfo = true, hasBalanceCurrent = true),
                deviceInfo = deviceInfo(),
                status = "Ready",
                updatedAtMillis = 123L
            ),
            formatter()
        )

        assertTrue(content.hasDeviceData)
        assertEquals(ControlDiagnosticsSectionType.values().toList(), content.sections.map { it.type })
        assertEquals("state:READY:true:Ready", content.value(R.string.control_diagnostics_connection_state))
        assertEquals("JBD-BMS", content.value(R.string.param_bluetooth_name))
        assertEquals("AA:BB:CC:DD:EE:FF", content.value(R.string.param_device_address))
        assertEquals("2.1", content.value(R.string.param_bms_version))
        assertEquals("SP04S020", content.value(R.string.param_bms_model))
        assertEquals("JBD", content.value(R.string.param_manufacturer))
        assertEquals("SN-42", content.value(R.string.param_serial_number))
        assertEquals("BAR-42", content.value(R.string.param_barcode))
        assertEquals("Pack 100", content.value(R.string.param_battery_model))
        assertEquals("integer:16", content.value(R.string.param_cell_count))
        assertEquals("integer:2", content.value(R.string.param_ntc_count))
        assertEquals("current:50.0", content.value(R.string.detail_rated_charge_current))
        assertEquals("current:100.0", content.value(R.string.detail_rated_discharge_current))
        assertEquals("power:2400.0", content.value(R.string.detail_rated_discharge_power))
        assertEquals("integer:60", content.value(R.string.param_extension_marker))
        assertEquals("integer:258", content.value(R.string.param_alter))
        assertEquals("current:1.25", content.value(R.string.param_balance_current))
        assertEquals("updated:123", content.value(R.string.detail_last_updated))
    }

    @Test
    fun missingDeviceInfoUsesUnreadAndOmitsRatedRows() {
        val content = ControlDiagnosticsResolver.resolve(
            snapshot(
                basicInfo = basicInfo(softwareVersion = ""),
                deviceInfo = null
            ),
            formatter()
        )

        assertTrue(content.hasDeviceData)
        assertEquals("UNREAD", content.value(R.string.param_bms_version))
        assertEquals("UNREAD", content.value(R.string.param_bms_model))
        assertEquals("UNREAD", content.value(R.string.param_manufacturer))
        assertEquals("UNREAD", content.value(R.string.param_serial_number))
        assertEquals("UNREAD", content.value(R.string.param_barcode))
        assertEquals("UNREAD", content.value(R.string.param_battery_model))

        val labels = content.labels()
        assertFalse(labels.contains(R.string.detail_rated_charge_current))
        assertFalse(labels.contains(R.string.detail_rated_discharge_current))
        assertFalse(labels.contains(R.string.detail_rated_discharge_power))
    }

    @Test
    fun disconnectedSnapshotShowsEmptyStateAndDoesNotExposeStaleDeviceData() {
        val content = ControlDiagnosticsResolver.resolve(
            snapshot(
                connected = false,
                connectionState = ConnectionState.DISCONNECTED,
                basicInfo = basicInfo(hasExtendedInfo = true, hasBalanceCurrent = true),
                deviceInfo = deviceInfo(),
                deviceName = null,
                deviceAddress = "",
                status = null
            ),
            formatter()
        )

        assertFalse(content.hasDeviceData)
        assertEquals("state:DISCONNECTED:false:none", content.value(R.string.control_diagnostics_connection_state))
        assertEquals("DISCONNECTED", content.value(R.string.param_bluetooth_name))
        assertEquals("DISCONNECTED", content.value(R.string.param_device_address))
        assertEquals("UNREAD", content.value(R.string.param_bms_version))
        assertEquals("UNREAD", content.value(R.string.param_bms_model))
        assertEquals("UNREAD", content.value(R.string.param_cell_count))
        assertFalse(content.sections.any { it.type == ControlDiagnosticsSectionType.EXTENSION })
        assertFalse(content.labels().contains(R.string.detail_rated_charge_current))
    }

    @Test
    fun connectedWithoutPayloadUsesUnreadMarkers() {
        val content = ControlDiagnosticsResolver.resolve(
            snapshot(
                basicInfo = null,
                deviceInfo = null,
                deviceName = "",
                deviceAddress = null,
                updatedAtMillis = 0L
            ),
            formatter()
        )

        assertFalse(content.hasDeviceData)
        assertEquals("UNREAD", content.value(R.string.param_bluetooth_name))
        assertEquals("UNREAD", content.value(R.string.param_device_address))
        assertEquals("UNREAD", content.value(R.string.param_bms_version))
        assertEquals("UNREAD", content.value(R.string.param_ntc_count))
        assertEquals("UNREAD", content.value(R.string.detail_last_updated))
    }

    @Test
    fun optionalRowsFollowIndependentCapabilityAndPositiveRatingFlags() {
        val balanceOnly = ControlDiagnosticsResolver.resolve(
            snapshot(
                basicInfo = basicInfo(hasExtendedInfo = false, hasBalanceCurrent = true),
                deviceInfo = deviceInfo(
                    ratedChargeCurrentA = 0f,
                    ratedDischargeCurrentA = -1f,
                    ratedDischargePowerW = 0f
                )
            ),
            formatter()
        )

        assertTrue(balanceOnly.labels().contains(R.string.param_balance_current))
        assertFalse(balanceOnly.labels().contains(R.string.param_extension_marker))
        assertFalse(balanceOnly.labels().contains(R.string.param_alter))
        assertFalse(balanceOnly.labels().contains(R.string.detail_rated_charge_current))
        assertFalse(balanceOnly.labels().contains(R.string.detail_rated_discharge_current))
        assertFalse(balanceOnly.labels().contains(R.string.detail_rated_discharge_power))

        val extensionOnly = ControlDiagnosticsResolver.resolve(
            snapshot(
                basicInfo = basicInfo(hasExtendedInfo = true, hasBalanceCurrent = false),
                deviceInfo = JbdDeviceInfo.EMPTY
            ),
            formatter()
        )

        assertTrue(extensionOnly.labels().contains(R.string.param_extension_marker))
        assertTrue(extensionOnly.labels().contains(R.string.param_alter))
        assertFalse(extensionOnly.labels().contains(R.string.param_balance_current))
    }

    private fun snapshot(
        basicInfo: JbdBasicInfo?,
        deviceInfo: JbdDeviceInfo? = JbdDeviceInfo.EMPTY,
        connected: Boolean = true,
        connectionState: ConnectionState = if (connected) {
            ConnectionState.READY
        } else {
            ConnectionState.DISCONNECTED
        },
        deviceName: String? = "JBD-BMS",
        deviceAddress: String? = "AA:BB:CC:DD:EE:FF",
        status: String? = null,
        updatedAtMillis: Long = 999L
    ) = BmsUiState(
        connected = connected,
        connectionState = connectionState,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        status = status,
        basicInfo = basicInfo,
        cellVoltages = null,
        deviceInfo = deviceInfo,
        updatedAtMillis = updatedAtMillis
    )

    private fun basicInfo(
        hasExtendedInfo: Boolean = false,
        hasBalanceCurrent: Boolean = false,
        softwareVersion: String = "2.1"
    ) = JbdBasicInfo(
        totalVoltage = 52.1f,
        current = -5f,
        remainingAh = 80f,
        nominalAh = 100f,
        cycleCount = 42,
        productionDate = "2026-09-14",
        soc = 82,
        balanceState = 2,
        balanceStates = booleanArrayOf(false, true),
        protectionState = 0,
        protectionStates = BooleanArray(13),
        chargeEnabled = true,
        dischargeEnabled = false,
        cellCount = 16,
        ntcCount = 2,
        softwareVersion = softwareVersion,
        temperaturesC = listOf(20f, 30f),
        hasLearnCapacity = false,
        learnCapacityAh = 0f,
        hasExtendedInfo = hasExtendedInfo,
        extensionMarker = 60,
        alter = 258,
        hasBalanceCurrent = hasBalanceCurrent,
        balanceCurrentA = 1.25f
    )

    private fun deviceInfo(
        ratedChargeCurrentA: Float = 50f,
        ratedDischargeCurrentA: Float = 100f,
        ratedDischargePowerW: Float = 2400f
    ) = JbdDeviceInfo(
        serialNumber = "SN-42",
        barcode = "BAR-42",
        batteryModel = "Pack 100",
        manufacturer = "JBD",
        bmsModel = "SP04S020",
        bmsAddress = "1",
        ratedChargeCurrentA = ratedChargeCurrentA,
        ratedDischargeCurrentA = ratedDischargeCurrentA,
        ratedDischargePowerW = ratedDischargePowerW
    )

    private fun formatter() = ControlDiagnosticsValueFormatter(
        unread = "UNREAD",
        disconnected = "DISCONNECTED",
        current = { "current:$it" },
        power = { "power:$it" },
        integer = { "integer:$it" },
        connectionState = { state, connected, status ->
            "state:${state.name}:$connected:${status ?: "none"}"
        },
        updatedAt = { "updated:$it" }
    )

    private fun ControlDiagnosticsContent.labels(): List<Int> =
        sections.flatMap { it.rows }.map { it.labelStringResId }

    private fun ControlDiagnosticsContent.value(labelStringResId: Int): String {
        val matchingRows = sections.flatMap { it.rows }
            .filter { it.labelStringResId == labelStringResId }
        assertTrue("Missing row for $labelStringResId", matchingRows.isNotEmpty())
        return matchingRows.single().value
    }
}
