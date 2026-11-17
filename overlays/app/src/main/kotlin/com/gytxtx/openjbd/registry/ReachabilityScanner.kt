package com.gytxtx.openjbd.registry

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.gytxtx.openjbd.ble.BleConstants

/**
 * Phase C short scan: refreshes reachability / RSSI of registered devices while the BMS
 * management sheet is open. It never runs in the background, never connects, and stops after
 * [SCAN_MS] or when [stop] is called (sheet closed).
 */
class ReachabilityScanner(
    private val context: Context,
    private val registry: BmsDeviceRegistry
) {
    enum class StartResult { STARTED, BLUETOOTH_OFF, PERMISSION_REQUIRED, UNAVAILABLE }

    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private val stopRunnable = Runnable { stop() }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)
        override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::record)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.removeCallbacks(stopRunnable)
            registry.endScan(failed = true)
        }
    }

    private fun record(result: ScanResult) {
        registry.recordSighting(result.device.address, result.rssi, System.currentTimeMillis())
    }

    @SuppressLint("MissingPermission")
    fun start(): StartResult {
        if (scanning) return StartResult.STARTED
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return StartResult.UNAVAILABLE
        if (!adapter.isEnabled) return StartResult.BLUETOOTH_OFF
        if (!hasScanPermission()) return StartResult.PERMISSION_REQUIRED
        val leScanner = adapter.bluetoothLeScanner ?: return StartResult.UNAVAILABLE
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID)).build())
        registry.beginScan(System.currentTimeMillis())
        return try {
            leScanner.startScan(filters, settings, callback)
            scanner = leScanner
            scanning = true
            handler.postDelayed(stopRunnable, SCAN_MS)
            StartResult.STARTED
        } catch (_: SecurityException) {
            registry.endScan(failed = true)
            StartResult.PERMISSION_REQUIRED
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(stopRunnable)
        if (!scanning) return
        scanning = false
        runCatching { if (hasScanPermission()) scanner?.stopScan(callback) }
        scanner = null
        registry.endScan()
    }

    private fun hasScanPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    companion object {
        const val SCAN_MS = 4_000L
    }
}
