package com.gytxtx.openjbd.settings

/** Callback-based settings command boundary implemented by the active BLE connection. */
interface SettingsCommandHost {
    fun isConnected(): Boolean
    fun beginSettingsSession()
    fun endSettingsSession()
    fun enqueueSettingsCommand(
        frame: ByteArray,
        responseAddress: Int,
        timeoutMs: Long,
        callback: (GatewayOutcome) -> Unit
    )
}
