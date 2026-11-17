package com.gytxtx.openjbd.registry

import android.content.Context
import com.gytxtx.openjbd.AppSettings
import com.gytxtx.openjbd.data.BmsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide persistent registry of BMS devices (Phase B) plus the latest short-scan results
 * (Phase C). The selected device is the only one eligible for auto-connect, and the identity
 * that writes, restores, backups and history are scoped to (Phase D, D021).
 */
@Singleton
class BmsDeviceRegistry @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: BmsRepository
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = AppSettings.prefs(context)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<BmsRegistry> = _state.asStateFlow()

    val scanResults = ReachabilityScanResults()
    private val _scanVersion = MutableStateFlow(ScanStatus())
    val scanStatus: StateFlow<ScanStatus> = _scanVersion.asStateFlow()

    private val started = AtomicBoolean(false)
    private var lastConnectedKey: String? = null

    val selected: RegisteredBms? get() = _state.value.selected

    private fun load(): BmsRegistry {
        val stored = BmsRegistryCodec.decode(prefs.getString(KEY_REGISTRY, null))
        val registry = LegacyDeviceMigration.migrate(
            stored = stored,
            legacyAddress = legacyPrefs.getString(AppSettings.PREF_LAST_DEVICE_ADDRESS, null),
            legacyName = legacyPrefs.getString(AppSettings.PREF_LAST_DEVICE_NAME, null),
            nowMillis = System.currentTimeMillis()
        )
        if (stored == null) persist(registry)
        return registry
    }

    @Synchronized
    private fun edit(change: (BmsRegistry) -> RegistryEdit): RegistryEdit {
        val result = change(_state.value)
        if (result is RegistryEdit.Changed && result.registry != _state.value) {
            persist(result.registry)
            _state.value = result.registry
        }
        return result
    }

    private fun persist(registry: BmsRegistry) {
        prefs.edit().putString(KEY_REGISTRY, BmsRegistryCodec.encode(registry)).apply()
        // Keep the OpenJBD single-device keys pointing at the selected device so an older APK
        // installed over this one still reconnects to the device the user chose.
        val selected = registry.selected
        val legacy = legacyPrefs.edit()
        if (selected == null) {
            legacy.remove(AppSettings.PREF_LAST_DEVICE_ADDRESS).remove(AppSettings.PREF_LAST_DEVICE_NAME)
        } else {
            legacy.putString(AppSettings.PREF_LAST_DEVICE_ADDRESS, selected.address)
                .putString(AppSettings.PREF_LAST_DEVICE_NAME, selected.displayName)
        }
        legacy.apply()
    }

    /** Registers (if needed) and selects a device discovered by the user. */
    fun registerAndSelect(address: String, bleName: String?): RegistryEdit {
        val now = System.currentTimeMillis()
        return edit { registry ->
            when (val registered = registry.register(address, bleName, now)) {
                is RegistryEdit.Changed -> registered.registry.select(address)
                is RegistryEdit.Rejected -> registered
            }
        }
    }

    fun select(address: String): RegistryEdit = edit { it.select(address) }
    fun rename(address: String, alias: String): RegistryEdit = edit { it.rename(address, alias) }
    fun remove(address: String): RegistryEdit = edit { it.remove(address) }

    fun beginScan(nowMillis: Long) {
        scanResults.clear()
        _scanVersion.value = ScanStatus(scanning = true, startedAtMillis = nowMillis, version = _scanVersion.value.version + 1)
    }

    fun recordSighting(address: String, rssi: Int, nowMillis: Long) {
        if (_state.value.find(address) == null) return
        scanResults.record(address, rssi, nowMillis)
        _scanVersion.value = _scanVersion.value.copy(version = _scanVersion.value.version + 1)
    }

    fun endScan(failed: Boolean = false) {
        _scanVersion.value = _scanVersion.value.copy(
            scanning = false,
            failed = failed,
            version = _scanVersion.value.version + 1
        )
    }

    /** Learns connection time and identity of the connected registered device. */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            repository.uiState.collect { snapshot ->
                val key = DeviceKeys.normalize(snapshot.deviceAddress)
                if (!snapshot.connected || key == null) {
                    lastConnectedKey = null
                    return@collect
                }
                if (_state.value.find(key) == null) return@collect
                if (lastConnectedKey != key) {
                    lastConnectedKey = key
                    edit { it.recordConnected(key, System.currentTimeMillis()) }
                }
                val model = snapshot.deviceInfo?.bmsModel
                val firmware = snapshot.basicInfo?.softwareVersion
                if (!model.isNullOrBlank() || !firmware.isNullOrBlank()) {
                    edit { it.learnIdentity(key, model, firmware) }
                }
            }
        }
    }

    data class ScanStatus(
        val scanning: Boolean = false,
        val failed: Boolean = false,
        val startedAtMillis: Long? = null,
        val version: Long = 0
    )

    private companion object {
        const val PREFS_NAME = "bms_registry"
        const val KEY_REGISTRY = "registry_v1"
    }
}
