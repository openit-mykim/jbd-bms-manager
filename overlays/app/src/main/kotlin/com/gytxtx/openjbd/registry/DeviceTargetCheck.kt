package com.gytxtx.openjbd.registry

import com.gytxtx.openjbd.maintenance.DeviceIdentity

/**
 * D021: before any write or restore, the connected BMS must be the BMS the user explicitly
 * selected. Every result except [Result.MATCH] is fail-closed: no frame is sent.
 */
object DeviceTargetCheck {
    enum class Result {
        /** Connected device is the selected device (address, and model once learned). */
        MATCH,

        /** No registered device is selected. */
        NO_SELECTION,

        /** Connected identity (address / firmware) has not been read yet. */
        IDENTITY_UNREAD,

        /** A different BLE address than the selected device is connected. */
        ADDRESS_MISMATCH,

        /** The registered model is known but the connected device has not reported its model. */
        MODEL_UNREAD,

        /** Same address, different BMS model: a replaced board or address reuse. */
        MODEL_MISMATCH
    }

    fun check(selected: RegisteredBms?, connected: DeviceIdentity?): Result {
        if (selected == null) return Result.NO_SELECTION
        if (connected == null) return Result.IDENTITY_UNREAD
        if (DeviceKeys.normalize(connected.address) != selected.address) return Result.ADDRESS_MISMATCH
        val registeredModel = selected.bmsModel ?: return Result.MATCH
        if (connected.bmsModel == DeviceIdentity.UNKNOWN_MODEL) return Result.MODEL_UNREAD
        return if (connected.bmsModel == registeredModel) Result.MATCH else Result.MODEL_MISMATCH
    }

    /** Restore guard: a backup may only be restored onto the BMS it was exported from. */
    enum class BackupResult { MATCH, NO_SELECTION, NO_BACKUP_ADDRESS, ADDRESS_MISMATCH, MODEL_MISMATCH }

    fun checkBackup(selected: RegisteredBms?, backupAddress: String?, backupModel: String?): BackupResult {
        if (selected == null) return BackupResult.NO_SELECTION
        val key = DeviceKeys.normalize(backupAddress) ?: return BackupResult.NO_BACKUP_ADDRESS
        if (key != selected.address) return BackupResult.ADDRESS_MISMATCH
        val model = backupModel?.trim()?.takeIf { it.isNotEmpty() }
        val registered = selected.bmsModel
        return if (model != null && registered != null && model != registered) BackupResult.MODEL_MISMATCH
        else BackupResult.MATCH
    }
}
