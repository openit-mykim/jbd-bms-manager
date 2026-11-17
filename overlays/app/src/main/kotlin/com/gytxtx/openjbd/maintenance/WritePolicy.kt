package com.gytxtx.openjbd.maintenance

/**
 * Identity a write permission is bound to. The BLE address alone could be reused by a replaced
 * board, so the reported BMS model and firmware version are part of the fingerprint: a firmware
 * change voids an earlier experimental consent.
 */
data class DeviceIdentity(
    val address: String,
    val bmsModel: String,
    val firmwareVersion: String
) {
    val fingerprint: String get() = "$address|$bmsModel|$firmwareVersion"

    companion object {
        /** Null until the address and firmware version have been read from the device. */
        fun from(address: String?, bmsModel: String?, firmwareVersion: String?): DeviceIdentity? {
            val cleanAddress = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val cleanFirmware = firmwareVersion?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val cleanModel = bmsModel?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_MODEL
            return DeviceIdentity(cleanAddress, cleanModel, cleanFirmware)
        }

        const val UNKNOWN_MODEL = "unknown-model"
    }
}

sealed interface WriteDecision {
    enum class Basis {
        /** The device matched an entry of the local compatibility table (D005). */
        VERIFIED_PROFILE,

        /** Not in the table; a technician explicitly accepted experimental writes for it. */
        EXPERIMENTAL_CONSENT
    }

    enum class DenyReason {
        /** Maintenance Mode is locked, or was unlocked for a different device. */
        LOCKED,

        /** Address / firmware version not read yet, so no permission can be bound. */
        NO_DEVICE_IDENTITY,

        /** Unknown variant without consent: read-only by default (D005). */
        NOT_CONSENTED,

        /**
         * The connected BMS is not the registered BMS the user selected, or its identity differs
         * from the registered one (D022). Fail closed: no frame is sent.
         */
        TARGET_MISMATCH
    }

    data class Allowed(val basis: Basis, val identity: DeviceIdentity) : WriteDecision
    data class Denied(val reason: DenyReason) : WriteDecision
}

/**
 * D005 write policy: unknown devices are read-only unless (a) they match a verified local
 * compatibility profile or (b) a technician gave explicit, device-bound experimental consent.
 * Writes additionally require Maintenance Mode to be unlocked for that same device, and (D022)
 * the connected device must be the explicitly selected registered BMS.
 */
object WritePolicy {
    fun decide(
        unlockedForAddress: String?,
        unlocked: Boolean,
        identity: DeviceIdentity?,
        verifiedProfile: Boolean,
        consented: Boolean,
        targetMatches: Boolean
    ): WriteDecision {
        if (identity == null) {
            return WriteDecision.Denied(
                if (unlocked) WriteDecision.DenyReason.NO_DEVICE_IDENTITY
                else WriteDecision.DenyReason.LOCKED
            )
        }
        if (!targetMatches) {
            return WriteDecision.Denied(WriteDecision.DenyReason.TARGET_MISMATCH)
        }
        if (!unlocked || unlockedForAddress != identity.address) {
            return WriteDecision.Denied(WriteDecision.DenyReason.LOCKED)
        }
        return when {
            verifiedProfile -> WriteDecision.Allowed(WriteDecision.Basis.VERIFIED_PROFILE, identity)
            consented -> WriteDecision.Allowed(WriteDecision.Basis.EXPERIMENTAL_CONSENT, identity)
            else -> WriteDecision.Denied(WriteDecision.DenyReason.NOT_CONSENTED)
        }
    }
}
