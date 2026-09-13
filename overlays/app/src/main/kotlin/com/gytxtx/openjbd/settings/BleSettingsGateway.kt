package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.BmsConnectionManager
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine

/** Bridges the manager's callback queue to cancellation-safe settings coroutines. */
class BleSettingsGateway internal constructor(
    private val host: SettingsCommandHost
) : SettingsGateway {
    @Inject
    constructor(manager: BmsConnectionManager) : this(manager as SettingsCommandHost)

    private val executeMutex = Mutex()
    private val sessionMutex = Mutex()

    internal fun isConnected(): Boolean = host.isConnected()

    override suspend fun execute(
        frame: ByteArray,
        responseAddress: Int,
        timeoutMs: Long
    ): GatewayOutcome = executeMutex.withLock {
        if (!host.isConnected()) {
            return@withLock GatewayOutcome.TransportFailure("not connected")
        }

        suspendCancellableCoroutine<GatewayOutcome> { continuation ->
            val completed = AtomicBoolean(false)
            continuation.invokeOnCancellation { completed.set(true) }
            host.enqueueSettingsCommand(frame, responseAddress, timeoutMs) { outcome ->
                if (completed.compareAndSet(false, true)) {
                    continuation.resume(outcome) { _, _, _ -> }
                }
            }
        }
    }

    suspend fun <T> withSession(block: suspend () -> T): T = sessionMutex.withLock {
        check(host.isConnected()) { "not connected" }
        host.beginSettingsSession()
        try {
            block()
        } finally {
            host.endSettingsSession()
        }
    }
}
