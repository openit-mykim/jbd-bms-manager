package com.gytxtx.openjbd

import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState
import com.gytxtx.openjbd.protocol.JbdBasicInfo
import com.gytxtx.openjbd.protocol.JbdDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFieldResolverTest {
    @Test
    fun fullSnapshotBuildsUserOrientedSectionsAndOptionalRows() {
        val sections = requireNotNull(
            DetailFieldResolver.resolve(
                snapshot(
                    basicInfo = basicInfo(hasLearnCapacity = true, hasBalanceCurrent = true),
                    deviceInfo = JbdDeviceInfo(
                        serialNumber = "SN-42",
                        barcode = "BAR-42",
                        batteryModel = "Pack 100",
                        manufacturer = "JBD",
                        bmsModel = "SP04S020",
                        bmsAddress = "1",
                        ratedChargeCurrentA = 50f,
                        ratedDischargeCurrentA = 100f,
                        ratedDischargePowerW = 2400f
                    ),
                    updatedAtMillis = 123L
                ),
                formatter()
            )
        )

        assertEquals(DetailSectionType.values().toList(), sections.map { it.type })
        assertEquals("percent:82", sections.value(R.string.param_soc))
        assertEquals("capacity:95.0", sections.value(R.string.param_learn_capacity))
        assertEquals("SN-42", sections.value(R.string.param_serial_number))
        assertEquals("current:50.0", sections.value(R.string.detail_rated_charge_current))
        assertEquals("temperatures:20.0,30.0", sections.value(R.string.metric_temperature))
        assertEquals("current:1.25", sections.value(R.string.param_balance_current))
        assertEquals("updated:123", sections.value(R.string.detail_last_updated))

        val labels = sections.flatMap { it.rows }.map { it.labelStringResId }
        assertFalse(labels.contains(R.string.param_pack_voltage))
        assertFalse(labels.contains(R.string.param_pack_current))
        assertFalse(labels.contains(R.string.param_pack_power))
        assertFalse(labels.contains(R.string.param_extension_marker))
        assertFalse(labels.contains(R.string.param_alter))
    }

    @Test
    fun missingDeviceInfoUsesUnreadAndOmitsUnsupportedOptionalRows() {
        val sections = requireNotNull(
            DetailFieldResolver.resolve(
                snapshot(
                    basicInfo = basicInfo(
                        hasLearnCapacity = false,
                        hasBalanceCurrent = false,
                        softwareVersion = "",
                        productionDate = ""
                    ),
                    deviceInfo = null,
                    deviceName = "",
                    deviceAddress = null,
                    updatedAtMillis = 0L
                ),
                formatter()
            )
        )

        assertEquals("UNREAD", sections.value(R.string.param_bms_version))
        assertEquals("UNREAD", sections.value(R.string.param_manufacturing_date))
        assertEquals("UNREAD", sections.value(R.string.param_bms_model))
        assertEquals("UNREAD", sections.value(R.string.param_serial_number))
        assertEquals("UNREAD", sections.value(R.string.param_bluetooth_name))
        assertEquals("UNREAD", sections.value(R.string.param_device_address))
        assertEquals("UNREAD", sections.value(R.string.detail_last_updated))

        val labels = sections.flatMap { it.rows }.map { it.labelStringResId }
        assertFalse(labels.contains(R.string.param_learn_capacity))
        assertFalse(labels.contains(R.string.param_balance_current))
        assertFalse(labels.contains(R.string.detail_rated_charge_current))
        assertFalse(labels.contains(R.string.detail_rated_discharge_current))
        assertFalse(labels.contains(R.string.detail_rated_discharge_power))
    }

    @Test
    fun disconnectedOrMissingBasicInfoHasNoDetailContent() {
        val disconnected = snapshot(
            basicInfo = basicInfo(),
            connected = false
        )
        val missingBasicInfo = snapshot(basicInfo = null)

        assertNull(DetailFieldResolver.resolve(disconnected, formatter()))
        assertNull(DetailFieldResolver.resolve(missingBasicInfo, formatter()))
    }

    private fun snapshot(
        basicInfo: JbdBasicInfo?,
        deviceInfo: JbdDeviceInfo? = JbdDeviceInfo.EMPTY,
        connected: Boolean = true,
        deviceName: String? = "JBD-BMS",
        deviceAddress: String? = "AA:BB:CC:DD:EE:FF",
        updatedAtMillis: Long = 999L
    ) = BmsUiState(
        connected = connected,
        connectionState = if (connected) ConnectionState.READY else ConnectionState.DISCONNECTED,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        status = null,
        basicInfo = basicInfo,
        cellVoltages = null,
        deviceInfo = deviceInfo,
        updatedAtMillis = updatedAtMillis
    )

    private fun basicInfo(
        hasLearnCapacity: Boolean = false,
        hasBalanceCurrent: Boolean = false,
        softwareVersion: String = "2.1",
        productionDate: String = "2026-09-14"
    ) = JbdBasicInfo(
        totalVoltage = 52.1f,
        current = -5f,
        remainingAh = 80f,
        nominalAh = 100f,
        cycleCount = 42,
        productionDate = productionDate,
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
        hasLearnCapacity = hasLearnCapacity,
        learnCapacityAh = 95f,
        hasExtendedInfo = true,
        extensionMarker = 60,
        alter = 258,
        hasBalanceCurrent = hasBalanceCurrent,
        balanceCurrentA = 1.25f
    )

    private fun formatter() = DetailValueFormatter(
        unread = "UNREAD",
        capacity = { "capacity:$it" },
        current = { "current:$it" },
        power = { "power:$it" },
        percent = { "percent:$it" },
        integer = { "integer:$it" },
        temperatures = { "temperatures:${it.joinToString(separator = ",")}" },
        onOff = { if (it) "ON" else "OFF" },
        balance = { "balance:${it.balanceState}" },
        protection = { "protection:${it.protectionState}" },
        updatedAt = { "updated:$it" }
    )

    private fun List<DetailSection>.value(labelStringResId: Int): String {
        val matchingRows = flatMap { it.rows }.filter { it.labelStringResId == labelStringResId }
        assertTrue("Missing row for $labelStringResId", matchingRows.isNotEmpty())
        return matchingRows.single().value
    }
}
