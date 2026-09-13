package com.gytxtx.openjbd

import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.protocol.JbdBasicInfo

internal enum class DetailSectionType {
    BATTERY,
    DEVICE,
    CONNECTION,
    STATUS,
    REFRESH
}

internal data class DetailRow(
    val labelStringResId: Int,
    val value: String
)

internal data class DetailSection(
    val type: DetailSectionType,
    val rows: List<DetailRow>
)

internal class DetailValueFormatter(
    val unread: String,
    val capacity: (Float) -> String,
    val current: (Float) -> String,
    val power: (Float) -> String,
    val percent: (Int) -> String,
    val integer: (Int) -> String,
    val temperatures: (List<Float>) -> String,
    val onOff: (Boolean) -> String,
    val balance: (JbdBasicInfo) -> String,
    val protection: (JbdBasicInfo) -> String,
    val updatedAt: (Long) -> String
)

internal object DetailFieldResolver {
    fun resolve(
        snapshot: BmsUiState,
        formatter: DetailValueFormatter
    ): List<DetailSection>? {
        val info = snapshot.basicInfo
        if (!snapshot.connected || info == null) return null

        val batteryRows = mutableListOf(
            DetailRow(R.string.param_soc, formatter.percent(info.soc)),
            DetailRow(R.string.param_remaining_capacity, formatter.capacity(info.remainingAh)),
            DetailRow(R.string.param_nominal_capacity, formatter.capacity(info.nominalAh))
        )
        if (info.hasLearnCapacity) {
            batteryRows += DetailRow(
                R.string.param_learn_capacity,
                formatter.capacity(info.learnCapacityAh)
            )
        }
        batteryRows += DetailRow(R.string.param_cycle_count, formatter.integer(info.cycleCount))

        val deviceInfo = snapshot.deviceInfo
        val deviceRows = mutableListOf(
            DetailRow(R.string.param_bms_version, valueOrUnread(info.softwareVersion, formatter)),
            DetailRow(
                R.string.param_manufacturing_date,
                valueOrUnread(info.productionDate, formatter)
            ),
            DetailRow(R.string.param_bms_model, valueOrUnread(deviceInfo?.bmsModel, formatter)),
            DetailRow(
                R.string.param_manufacturer,
                valueOrUnread(deviceInfo?.manufacturer, formatter)
            ),
            DetailRow(
                R.string.param_serial_number,
                valueOrUnread(deviceInfo?.serialNumber, formatter)
            ),
            DetailRow(R.string.param_barcode, valueOrUnread(deviceInfo?.barcode, formatter)),
            DetailRow(
                R.string.param_battery_model,
                valueOrUnread(deviceInfo?.batteryModel, formatter)
            ),
            DetailRow(R.string.param_cell_count, formatter.integer(info.cellCount)),
            DetailRow(R.string.param_ntc_count, formatter.integer(info.ntcCount))
        )
        if (deviceInfo != null) {
            if (deviceInfo.ratedChargeCurrentA > 0f) {
                deviceRows += DetailRow(
                    R.string.detail_rated_charge_current,
                    formatter.current(deviceInfo.ratedChargeCurrentA)
                )
            }
            if (deviceInfo.ratedDischargeCurrentA > 0f) {
                deviceRows += DetailRow(
                    R.string.detail_rated_discharge_current,
                    formatter.current(deviceInfo.ratedDischargeCurrentA)
                )
            }
            if (deviceInfo.ratedDischargePowerW > 0f) {
                deviceRows += DetailRow(
                    R.string.detail_rated_discharge_power,
                    formatter.power(deviceInfo.ratedDischargePowerW)
                )
            }
        }

        val connectionRows = listOf(
            DetailRow(
                R.string.param_bluetooth_name,
                valueOrUnread(snapshot.deviceName, formatter)
            ),
            DetailRow(
                R.string.param_device_address,
                valueOrUnread(snapshot.deviceAddress, formatter)
            )
        )

        val statusRows = mutableListOf(
            DetailRow(R.string.metric_temperature, formatter.temperatures(info.temperaturesC)),
            DetailRow(R.string.param_charge_mos, formatter.onOff(info.chargeEnabled)),
            DetailRow(R.string.param_discharge_mos, formatter.onOff(info.dischargeEnabled)),
            DetailRow(R.string.param_balance_state, formatter.balance(info))
        )
        if (info.hasBalanceCurrent) {
            statusRows += DetailRow(
                R.string.param_balance_current,
                formatter.current(info.balanceCurrentA)
            )
        }
        statusRows += DetailRow(R.string.param_protection_state, formatter.protection(info))

        val updatedAtValue = if (snapshot.updatedAtMillis > 0L) {
            formatter.updatedAt(snapshot.updatedAtMillis)
        } else {
            formatter.unread
        }

        return listOf(
            DetailSection(DetailSectionType.BATTERY, batteryRows),
            DetailSection(DetailSectionType.DEVICE, deviceRows),
            DetailSection(DetailSectionType.CONNECTION, connectionRows),
            DetailSection(DetailSectionType.STATUS, statusRows),
            DetailSection(
                DetailSectionType.REFRESH,
                listOf(DetailRow(R.string.detail_last_updated, updatedAtValue))
            )
        )
    }

    private fun valueOrUnread(value: String?, formatter: DetailValueFormatter): String =
        value?.takeIf { it.isNotBlank() } ?: formatter.unread
}
