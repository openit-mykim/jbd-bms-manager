package com.gytxtx.openjbd.settings

/** Callback-based settings command boundary implemented by the active BLE connection. */
interface SettingsCommandHost {
    fun isConnected(): Boolean
    fun beginSettingsSession()
    fun endSettingsSession()

    /**
     * Queues one settings frame. The returned handle cancels it *before it is transmitted*: a
     * cancelled command whose GATT write has not started is removed and never sent. A command
     * whose write already started cannot be recalled; the BMS may still act on it, and its
     * outcome is no longer delivered to the cancelled caller (issue #30).
     */
    fun enqueueSettingsCommand(
        frame: ByteArray,
        responseAddress: Int,
        timeoutMs: Long,
        callback: (GatewayOutcome) -> Unit
    ): SettingsCommandHandle
}

fun interface SettingsCommandHandle {
    fun cancel()

    companion object {
        /** For a command that was never queued (for example rejected while disconnected). */
        val NONE = SettingsCommandHandle { }
    }
}
