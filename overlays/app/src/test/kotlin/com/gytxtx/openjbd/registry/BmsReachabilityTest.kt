package com.gytxtx.openjbd.registry

import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmsReachabilityTest {
    private val device = RegisteredBms("A4:C1:38:00:00:01", "Commuter", "SP14S004", 1L)
    private val other = "A4:C1:38:00:00:B1"

    private fun state(state: ConnectionState, connected: Boolean, address: String? = device.address) =
        BmsUiState.withConnectionState(state, connected, "x", address, null, null, null)

    private fun seen(rssi: Int, at: Long = 100L) = mapOf(device.address to ScanSighting(device.address, rssi, at))

    @Test
    fun connectedIsGreenEvenWhenNotAdvertising() {
        val signal = BmsReachability.resolve(device, state(ConnectionState.READY, true), emptyMap(), 50L)
        assertEquals(DeviceSignal(Reachability.CONNECTED, null), signal)
        assertEquals(BmsReachability.Color.GREEN, BmsReachability.color(signal.reachability))
    }

    @Test
    fun seenInLatestScanIsGreenWithRssi() {
        val signal = BmsReachability.resolve(device, null, seen(-61), 50L)
        assertEquals(DeviceSignal(Reachability.SEEN, -61), signal)
        assertEquals(BmsReachability.Color.GREEN, BmsReachability.color(signal.reachability))
    }

    @Test
    fun notSeenIsGrayNotRed() {
        val signal = BmsReachability.resolve(device, state(ConnectionState.DISCONNECTED, false), emptyMap(), 50L)
        assertEquals(DeviceSignal(Reachability.NOT_SEEN, null), signal)
        assertEquals(BmsReachability.Color.GRAY, BmsReachability.color(signal.reachability))
    }

    @Test
    fun sightingFromEarlierScanIsIgnored() {
        assertEquals(Reachability.NOT_SEEN, BmsReachability.resolve(device, null, seen(-70, at = 10L), 50L).reachability)
    }

    @Test
    fun explicitFailureOfThisDeviceIsRed() {
        BmsReachability.FAILURE_STATES.forEach { failure ->
            val signal = BmsReachability.resolve(device, state(failure, false), seen(-80), 50L)
            assertEquals(Reachability.FAILED, signal.reachability)
            assertEquals(-80, signal.rssi)
            assertEquals(BmsReachability.Color.RED, BmsReachability.color(signal.reachability))
        }
    }

    @Test
    fun failureOfAnotherDeviceDoesNotColorThisOne() {
        val signal = BmsReachability.resolve(device, state(ConnectionState.CONNECTION_FAILED, false, other), emptyMap(), 50L)
        assertEquals(Reachability.NOT_SEEN, signal.reachability)
    }

    @Test
    fun connectingIsGray() {
        listOf(ConnectionState.CONNECTING, ConnectionState.WAITING_RECONNECT).forEach {
            val signal = BmsReachability.resolve(device, state(it, false), emptyMap(), null)
            assertEquals(Reachability.CONNECTING, signal.reachability)
            assertEquals(BmsReachability.Color.GRAY, BmsReachability.color(signal.reachability))
        }
    }

    @Test
    fun outOfRangeStatesAreNotFailures() {
        assertTrue(ConnectionState.DISCONNECTED !in BmsReachability.FAILURE_STATES)
        assertTrue(ConnectionState.WAITING_RECONNECT !in BmsReachability.FAILURE_STATES)
        assertTrue(ConnectionState.BLUETOOTH_OFF !in BmsReachability.FAILURE_STATES)
    }

    @Test
    fun scanResultsNormalizeAndIgnoreInvalidAddresses() {
        val results = ReachabilityScanResults()
        results.record("a4:c1:38:00:00:01", -55, 5L)
        results.record("garbage", -40, 5L)
        assertEquals(mapOf(device.address to ScanSighting(device.address, -55, 5L)), results.snapshot())
        results.clear()
        assertTrue(results.snapshot().isEmpty())
    }
}
