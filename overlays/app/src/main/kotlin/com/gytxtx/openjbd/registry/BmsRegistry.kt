package com.gytxtx.openjbd.registry

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Device keys are normalized BLE addresses ("A4:C1:38:00:00:01"); one key = one physical BMS. */
object DeviceKeys {
    private val MAC = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

    /** Uppercased, trimmed address, or null when it is not a BLE MAC address. */
    fun normalize(address: String?): String? {
        val clean = address?.trim()?.uppercase(Locale.US) ?: return null
        return clean.takeIf { MAC.matches(it) }
    }
}

/**
 * One registered BMS (docs/multi-bms-design.md "Device model").
 *
 * [address] is the registry key. [bmsModel] / [firmwareVersion] are learned from the device on
 * the first connection where they could be read and form the identity that writes and restores
 * are checked against (D021): the address alone could be reused by a replaced board.
 */
data class RegisteredBms(
    val address: String,
    val alias: String,
    val bleName: String,
    val registeredAtMillis: Long,
    val lastConnectedAtMillis: Long? = null,
    val bmsModel: String? = null,
    val firmwareVersion: String? = null
) {
    /** User-facing name: the alias, or the advertised name / address when no alias is set. */
    val displayName: String
        get() = alias.ifBlank { bleName.ifBlank { address } }
}

sealed interface RegistryEdit {
    data class Changed(val registry: BmsRegistry) : RegistryEdit
    data class Rejected(val reason: Reason) : RegistryEdit

    enum class Reason { INVALID_ADDRESS, UNKNOWN_DEVICE, ALIAS_TOO_LONG }
}

/**
 * Immutable registry state: the registered devices (in registration order) and the one the user
 * explicitly selected. Every operation returns a new instance; persistence lives elsewhere.
 */
data class BmsRegistry(
    val devices: List<RegisteredBms> = emptyList(),
    val selectedAddress: String? = null
) {
    val selected: RegisteredBms? get() = selectedAddress?.let(::find)

    fun find(address: String?): RegisteredBms? {
        val key = DeviceKeys.normalize(address) ?: return null
        return devices.firstOrNull { it.address == key }
    }

    /** Registers [address] (or refreshes its advertised name) without changing the selection. */
    fun register(address: String, bleName: String?, nowMillis: Long): RegistryEdit {
        val key = DeviceKeys.normalize(address) ?: return RegistryEdit.Rejected(RegistryEdit.Reason.INVALID_ADDRESS)
        val cleanName = bleName?.trim().orEmpty().take(MAX_ALIAS_LENGTH)
        val existing = find(key)
        val updated = if (existing == null) {
            devices + RegisteredBms(
                address = key,
                alias = cleanName.ifBlank { key },
                bleName = cleanName,
                registeredAtMillis = nowMillis
            )
        } else {
            devices.map { if (it.address == key && cleanName.isNotBlank()) it.copy(bleName = cleanName) else it }
        }
        return RegistryEdit.Changed(copy(devices = updated))
    }

    /** Explicit user selection; only a registered device can be selected. */
    fun select(address: String): RegistryEdit {
        val device = find(address) ?: return RegistryEdit.Rejected(RegistryEdit.Reason.UNKNOWN_DEVICE)
        return RegistryEdit.Changed(copy(selectedAddress = device.address))
    }

    /** Sets the alias; a blank alias falls back to the advertised name. Never silently truncates. */
    fun rename(address: String, alias: String): RegistryEdit {
        val device = find(address) ?: return RegistryEdit.Rejected(RegistryEdit.Reason.UNKNOWN_DEVICE)
        val clean = alias.trim()
        if (clean.length > MAX_ALIAS_LENGTH) return RegistryEdit.Rejected(RegistryEdit.Reason.ALIAS_TOO_LONG)
        val newAlias = clean.ifBlank { device.bleName.ifBlank { device.address } }
        return RegistryEdit.Changed(replace(device.copy(alias = newAlias)))
    }

    /** Removes the device; removing the selected device leaves nothing selected (no auto-switch). */
    fun remove(address: String): RegistryEdit {
        val device = find(address) ?: return RegistryEdit.Rejected(RegistryEdit.Reason.UNKNOWN_DEVICE)
        return RegistryEdit.Changed(
            copy(
                devices = devices.filterNot { it.address == device.address },
                selectedAddress = selectedAddress.takeIf { it != device.address }
            )
        )
    }

    fun recordConnected(address: String, nowMillis: Long): RegistryEdit {
        val device = find(address) ?: return RegistryEdit.Rejected(RegistryEdit.Reason.UNKNOWN_DEVICE)
        return RegistryEdit.Changed(replace(device.copy(lastConnectedAtMillis = nowMillis)))
    }

    /**
     * Records the identity read from the device. The model is learned only once (first use):
     * a later, different model is never overwritten here, so the mismatch stays visible to
     * [DeviceTargetCheck]. The firmware version is kept current for display and backups;
     * firmware-bound write consent is handled separately by D020.
     */
    fun learnIdentity(address: String, bmsModel: String?, firmwareVersion: String?): RegistryEdit {
        val device = find(address) ?: return RegistryEdit.Rejected(RegistryEdit.Reason.UNKNOWN_DEVICE)
        val model = bmsModel?.trim()?.takeIf { it.isNotEmpty() }
        val firmware = firmwareVersion?.trim()?.takeIf { it.isNotEmpty() }
        val learnedModel = device.bmsModel ?: model
        val newFirmware = if (device.bmsModel == null || device.bmsModel == model) firmware ?: device.firmwareVersion
        else device.firmwareVersion
        val updated = device.copy(bmsModel = learnedModel, firmwareVersion = newFirmware)
        return if (updated == device) RegistryEdit.Changed(this) else RegistryEdit.Changed(replace(updated))
    }

    private fun replace(device: RegisteredBms) =
        copy(devices = devices.map { if (it.address == device.address) device else it })

    companion object {
        const val MAX_ALIAS_LENGTH = 32
    }
}

/** Versioned JSON codec. Unreadable input yields null so the caller can fall back safely. */
object BmsRegistryCodec {
    const val FORMAT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(registry: BmsRegistry): String = buildJsonObject {
        put("v", FORMAT_VERSION)
        put("selected", registry.selectedAddress?.let(::JsonPrimitive) ?: JsonNull)
        put("devices", buildJsonArray {
            registry.devices.forEach { device ->
                add(buildJsonObject {
                    put("address", device.address)
                    put("alias", device.alias)
                    put("bleName", device.bleName)
                    put("registeredAt", device.registeredAtMillis)
                    device.lastConnectedAtMillis?.let { put("lastConnectedAt", it) }
                    device.bmsModel?.let { put("bmsModel", it) }
                    device.firmwareVersion?.let { put("firmware", it) }
                })
            }
        })
    }.toString()

    fun decode(text: String?): BmsRegistry? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            if ((root["v"] as? JsonPrimitive)?.intOrNull != FORMAT_VERSION) return null
            val devices = (root["devices"] as? JsonArray).orEmpty().mapNotNull { element ->
                decodeDevice(element)
            }.distinctBy { it.address }
            val selected = DeviceKeys.normalize(root.string("selected"))
                ?.takeIf { key -> devices.any { it.address == key } }
            BmsRegistry(devices, selected)
        }.getOrNull()
    }

    private fun decodeDevice(element: JsonElement): RegisteredBms? {
        val obj = element as? JsonObject ?: return null
        val address = DeviceKeys.normalize(obj.string("address")) ?: return null
        val bleName = obj.string("bleName").orEmpty()
        return RegisteredBms(
            address = address,
            alias = obj.string("alias").orEmpty().ifBlank { bleName.ifBlank { address } },
            bleName = bleName,
            registeredAtMillis = (obj["registeredAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
            lastConnectedAtMillis = (obj["lastConnectedAt"] as? JsonPrimitive)?.longOrNull,
            bmsModel = obj.string("bmsModel")?.takeIf { it.isNotBlank() },
            firmwareVersion = obj.string("firmware")?.takeIf { it.isNotBlank() }
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull
}

/**
 * One-time migration from the OpenJBD single-device preferences (`last_device_address/name`).
 * The last connected device becomes the first registered and selected BMS.
 */
object LegacyDeviceMigration {
    fun migrate(
        stored: BmsRegistry?,
        legacyAddress: String?,
        legacyName: String?,
        nowMillis: Long
    ): BmsRegistry {
        if (stored != null) return stored
        val key = DeviceKeys.normalize(legacyAddress) ?: return BmsRegistry()
        val name = legacyName?.trim()?.takeIf { it.isNotEmpty() && !it.equals(key, ignoreCase = true) }
        val registered = (BmsRegistry().register(key, name, nowMillis) as RegistryEdit.Changed).registry
        return (registered.select(key) as RegistryEdit.Changed).registry
    }
}
