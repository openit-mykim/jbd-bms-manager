package com.gytxtx.openjbd.maintenance

import android.content.Context
import android.util.Log
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.registry.BmsDeviceRegistry
import com.gytxtx.openjbd.registry.DeviceTargetCheck
import com.gytxtx.openjbd.settings.SettingsWriteAuthorizer
import com.gytxtx.openjbd.settings.WriteSessionAudit
import com.gytxtx.openjbd.settings.WriteSessionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide owner of Maintenance Mode unlock state and device-bound experimental consent.
 * The unlock is in memory only (never persisted) and bound to the device it was granted for;
 * consent is persisted per [DeviceIdentity.fingerprint] until revoked.
 */
@Singleton
class WriteAuthorization @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: BmsRepository,
    private val registry: BmsDeviceRegistry
) : SettingsWriteAuthorizer {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val resolver = MaintenanceCapabilityResolver(LocalCompatibilityTable.EMPTY)
    val auditLog: MaintenanceAuditLog =
        FileMaintenanceAuditLog(File(context.filesDir, AUDIT_FILE_NAME))

    @Volatile private var unlocked = false
    @Volatile private var unlockedForAddress: String? = null

    /** Bumped on every unlock/consent change so UIs can re-render. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun unlock(address: String?) {
        unlocked = true
        unlockedForAddress = address
        _version.value += 1
    }

    fun relock() {
        unlocked = false
        unlockedForAddress = null
        _version.value += 1
    }

    fun isUnlockedFor(address: String?): Boolean = unlocked && unlockedForAddress == address

    fun currentIdentity(): DeviceIdentity? {
        val snapshot = repository.getSnapshot()
        return DeviceIdentity.from(
            snapshot.deviceAddress,
            snapshot.deviceInfo?.bmsModel,
            snapshot.basicInfo?.softwareVersion
        )
    }

    fun isVerifiedProfile(identity: DeviceIdentity): Boolean =
        resolver.resolve(
            DeviceCapabilityEvidence(
                exactBmsModel = identity.bmsModel,
                hardwareRevision = null,
                firmwareVersion = identity.firmwareVersion,
                protocolVariant = PROTOCOL_VARIANT
            )
        ).asSet().isNotEmpty()

    fun hasConsent(identity: DeviceIdentity): Boolean =
        prefs.contains(CONSENT_PREFIX + identity.fingerprint)

    fun grantConsent(identity: DeviceIdentity) {
        prefs.edit().putLong(CONSENT_PREFIX + identity.fingerprint, System.currentTimeMillis()).apply()
        audit(identity, CONSENT_GRANTED)
        _version.value += 1
    }

    fun revokeConsent(identity: DeviceIdentity) {
        prefs.edit().remove(CONSENT_PREFIX + identity.fingerprint).apply()
        audit(identity, CONSENT_REVOKED)
        _version.value += 1
    }

    /** D021: is the connected BMS the registered BMS the user explicitly selected? */
    fun targetCheck(identity: DeviceIdentity? = currentIdentity()): DeviceTargetCheck.Result =
        DeviceTargetCheck.check(registry.selected, identity)

    override fun authorize(): WriteDecision {
        val identity = currentIdentity()
        return WritePolicy.decide(
            unlockedForAddress = unlockedForAddress,
            unlocked = unlocked,
            identity = identity,
            verifiedProfile = identity != null && isVerifiedProfile(identity),
            consented = identity != null && hasConsent(identity),
            targetMatches = targetCheck(identity) == DeviceTargetCheck.Result.MATCH
        )
    }

    override fun recordSession(decision: WriteDecision.Allowed, result: WriteSessionResult) {
        runCatching { WriteSessionAudit.records(decision, result).forEach(auditLog::append) }
            .onFailure { Log.w(TAG, "Could not append maintenance audit entry", it) }
    }

    private fun audit(identity: DeviceIdentity, event: String) {
        runCatching {
            auditLog.append(
                MaintenanceAuditRecord(
                    deviceIdentifier = MaintenanceDeviceIdentifier(
                        address = identity.address,
                        fingerprint = identity.fingerprint
                    ),
                    bmsModel = identity.bmsModel,
                    firmwareVersion = identity.firmwareVersion,
                    fieldKey = event,
                    previousValue = "",
                    newValue = "",
                    outcome = MaintenanceAuditOutcome.SUCCESS_VERIFIED
                )
            )
        }.onFailure { Log.w(TAG, "Could not append maintenance audit entry", it) }
    }

    private companion object {
        const val TAG = "WriteAuthorization"
        const val PREFS_NAME = "maintenance_write_consent"
        const val CONSENT_PREFIX = "consent:"
        const val AUDIT_FILE_NAME = "maintenance_audit.log"
        const val PROTOCOL_VARIANT = "jbd-ble-a5-5a"
        const val CONSENT_GRANTED = "consent.experimentalWrite.granted"
        const val CONSENT_REVOKED = "consent.experimentalWrite.revoked"
    }
}
