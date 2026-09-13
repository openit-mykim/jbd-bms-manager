package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.protocol.JbdFrame

/** Android-free boundary implemented by the BLE integration in a later round. */
interface SettingsGateway {
    suspend fun execute(
        frame: ByteArray,
        responseAddress: Int,
        timeoutMs: Long
    ): GatewayOutcome
}

sealed interface GatewayOutcome {
    data class Response(val frame: JbdFrame) : GatewayOutcome
    object NoResponse : GatewayOutcome
    data class TransportFailure(val message: String) : GatewayOutcome {
        init {
            require(message.isNotBlank()) { "Transport failure message must not be blank" }
        }
    }
}
