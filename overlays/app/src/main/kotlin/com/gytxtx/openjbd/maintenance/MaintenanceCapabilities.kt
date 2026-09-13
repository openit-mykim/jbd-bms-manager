package com.gytxtx.openjbd.maintenance

/** Granular write capabilities. No capability implies permission for another capability. */
enum class MaintenanceCapability {
    FACTORY_MODE,
    PACK_VOLTAGE_CALIBRATION,
    CELL_VOLTAGE_CALIBRATION,
    CURRENT_CALIBRATION,
    NTC_CALIBRATION,
    PROTECTION_WRITE,
    BALANCE_CONFIG,
    MOS_CONTROL,
    CAPACITY_WRITE,
    BACKUP_RESTORE
}

/**
 * Positive evidence collected from the connected BMS without performing a write.
 * Identity strings are kept exact because normalizing an unknown vendor naming scheme could
 * accidentally match a different hardware or firmware variant.
 */
data class DeviceCapabilityEvidence(
    val exactBmsModel: String?,
    val hardwareRevision: String?,
    val firmwareVersion: String?,
    val protocolVariant: String?,
    val safeRegisterReads: Set<SafeRegisterReadEvidence> = emptySet()
)

/** A successfully decoded, read-only register response used as compatibility evidence. */
data class SafeRegisterReadEvidence(
    val registerKey: String,
    val responseSignature: String
) {
    init {
        require(registerKey.isNotBlank()) { "registerKey must not be blank" }
        require(responseSignature.isNotBlank()) { "responseSignature must not be blank" }
    }
}

/**
 * One locally verified compatibility profile.
 *
 * All four identity fields and every required safe read must match exactly. This intentionally
 * rejects family-name, prefix, wildcard, and partial matches. Those broader matching strategies
 * are unsafe until supported by device observations.
 */
data class LocalCompatibilityEntry(
    val exactBmsModel: String,
    val hardwareRevision: String,
    val firmwareVersion: String,
    val protocolVariant: String,
    val requiredSafeRegisterReads: Set<SafeRegisterReadEvidence>,
    val capabilities: Set<MaintenanceCapability>
) {
    init {
        require(exactBmsModel.isNotBlank()) { "exactBmsModel must not be blank" }
        require(hardwareRevision.isNotBlank()) { "hardwareRevision must not be blank" }
        require(firmwareVersion.isNotBlank()) { "firmwareVersion must not be blank" }
        require(protocolVariant.isNotBlank()) { "protocolVariant must not be blank" }
        require(requiredSafeRegisterReads.isNotEmpty()) {
            "At least one safe register read is required"
        }
    }
}

/** Injectable, local-only compatibility knowledge. It ships empty until a device is verified. */
data class LocalCompatibilityTable(
    val entries: List<LocalCompatibilityEntry> = emptyList()
) {
    companion object {
        val EMPTY = LocalCompatibilityTable()
    }
}

/** Named flags exposed to feature code after conservative capability resolution. */
data class MaintenanceCapabilities internal constructor(
    private val supported: Set<MaintenanceCapability>
) {
    val supportsFactoryMode = MaintenanceCapability.FACTORY_MODE in supported
    val supportsPackVoltageCalibration =
        MaintenanceCapability.PACK_VOLTAGE_CALIBRATION in supported
    val supportsCellVoltageCalibration =
        MaintenanceCapability.CELL_VOLTAGE_CALIBRATION in supported
    val supportsCurrentCalibration = MaintenanceCapability.CURRENT_CALIBRATION in supported
    val supportsNtcCalibration = MaintenanceCapability.NTC_CALIBRATION in supported
    val supportsProtectionWrite = MaintenanceCapability.PROTECTION_WRITE in supported
    val supportsBalanceConfig = MaintenanceCapability.BALANCE_CONFIG in supported
    val supportsMosControl = MaintenanceCapability.MOS_CONTROL in supported
    val supportsCapacityWrite = MaintenanceCapability.CAPACITY_WRITE in supported
    val supportsBackupRestore = MaintenanceCapability.BACKUP_RESTORE in supported

    fun asSet(): Set<MaintenanceCapability> = supported.toSet()

    companion object {
        val EMPTY = MaintenanceCapabilities(emptySet())

        fun from(capabilities: Set<MaintenanceCapability>): MaintenanceCapabilities =
            MaintenanceCapabilities(capabilities.toSet())
    }
}

/**
 * Resolves evidence to capabilities using D005's read-only default.
 * Missing identity, no matching profile, or more than one matching profile is ambiguous and
 * therefore resolves to an empty capability set.
 */
class MaintenanceCapabilityResolver(
    private val compatibilityTable: LocalCompatibilityTable = LocalCompatibilityTable.EMPTY
) {
    fun resolve(evidence: DeviceCapabilityEvidence): MaintenanceCapabilities {
        val model = evidence.exactBmsModel.nonBlankOrNull() ?: return MaintenanceCapabilities.EMPTY
        val hardware = evidence.hardwareRevision.nonBlankOrNull()
            ?: return MaintenanceCapabilities.EMPTY
        val firmware = evidence.firmwareVersion.nonBlankOrNull()
            ?: return MaintenanceCapabilities.EMPTY
        val protocol = evidence.protocolVariant.nonBlankOrNull()
            ?: return MaintenanceCapabilities.EMPTY

        val matches = compatibilityTable.entries.filter { entry ->
            entry.exactBmsModel == model &&
                entry.hardwareRevision == hardware &&
                entry.firmwareVersion == firmware &&
                entry.protocolVariant == protocol &&
                evidence.safeRegisterReads.containsAll(entry.requiredSafeRegisterReads)
        }

        return if (matches.size == 1) {
            MaintenanceCapabilities.from(matches.single().capabilities)
        } else {
            MaintenanceCapabilities.EMPTY
        }
    }

    private fun String?.nonBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }
}
