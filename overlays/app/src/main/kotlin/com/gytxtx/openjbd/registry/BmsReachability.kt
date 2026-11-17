package com.gytxtx.openjbd.registry

import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState

/** One advertisement seen during a short reachability scan. */
data class ScanSighting(val address: String, val rssi: Int, val seenAtMillis: Long)

/**
 * Phase C signal semantics (docs/multi-bms-design.md "Signal semantics"):
 * green = connected or seen in the latest scan, gray = registered but not seen,
 * red = explicit connection failure / device error. Out of range is never red.
 */
enum class Reachability { CONNECTED, CONNECTING, SEEN, NOT_SEEN, FAILED }

data class DeviceSignal(val reachability: Reachability, val rssi: Int?)

/** In-memory result of the latest short scan; never persisted (no fake RSSI after restart). */
class ReachabilityScanResults {
    private val sightings = LinkedHashMap<String, ScanSighting>()

    @Synchronized
    fun record(address: String, rssi: Int, nowMillis: Long) {
        val key = DeviceKeys.normalize(address) ?: return
        sightings[key] = ScanSighting(key, rssi, nowMillis)
    }

    @Synchronized
    fun clear() = sightings.clear()

    @Synchronized
    fun snapshot(): Map<String, ScanSighting> = LinkedHashMap(sightings)
}

object BmsReachability {
    /** Connection states that mean an explicit failure of the device we tried to reach. */
    val FAILURE_STATES: Set<ConnectionState> = setOf(
        ConnectionState.INVALID_DEVICE,
        ConnectionState.CONNECTION_FAILED,
        ConnectionState.SERVICE_DISCOVERY_FAILED,
        ConnectionState.SERVICE_NOT_FOUND,
        ConnectionState.CHARACTERISTICS_NOT_FOUND,
        ConnectionState.NOTIFICATIONS_FAILED,
        ConnectionState.ERROR,
        ConnectionState.PARSE_ERROR
    )

    private val IN_PROGRESS_STATES: Set<ConnectionState> = setOf(
        ConnectionState.CONNECTING,
        ConnectionState.DISCOVERING_SERVICES,
        ConnectionState.ENABLING_NOTIFICATIONS,
        ConnectionState.WAITING_RECONNECT
    )

    /**
     * Resolves the signal of one registered device. A sighting older than [scanStartedAtMillis]
     * belongs to an earlier scan and is ignored, so a device that left range turns gray.
     */
    fun resolve(
        device: RegisteredBms,
        connection: BmsUiState?,
        sightings: Map<String, ScanSighting>,
        scanStartedAtMillis: Long?
    ): DeviceSignal {
        val sighting = sightings[device.address]
            ?.takeIf { scanStartedAtMillis == null || it.seenAtMillis >= scanStartedAtMillis }
        val rssi = sighting?.rssi
        val isThisDevice = connection != null && DeviceKeys.normalize(connection.deviceAddress) == device.address
        if (isThisDevice) {
            val state = connection!!.connectionState
            when {
                connection.connected -> return DeviceSignal(Reachability.CONNECTED, rssi)
                state in FAILURE_STATES -> return DeviceSignal(Reachability.FAILED, rssi)
                state in IN_PROGRESS_STATES -> return DeviceSignal(Reachability.CONNECTING, rssi)
            }
        }
        return if (sighting != null) DeviceSignal(Reachability.SEEN, rssi)
        else DeviceSignal(Reachability.NOT_SEEN, null)
    }

    /** Green / gray / red mapping used by the device list dot. */
    enum class Color { GREEN, GRAY, RED }

    fun color(reachability: Reachability): Color = when (reachability) {
        Reachability.CONNECTED, Reachability.SEEN -> Color.GREEN
        Reachability.FAILED -> Color.RED
        Reachability.CONNECTING, Reachability.NOT_SEEN -> Color.GRAY
    }
}
