package com.gytxtx.openjbd.history

/**
 * Local trend history. Every sample belongs to exactly one BMS, identified by its registry
 * device key (normalized BLE address, see `registry.DeviceKeys`).
 */
interface HistoryStore {
    suspend fun append(deviceKey: String, sample: HistorySample)
    suspend fun query(deviceKey: String, fromMillis: Long, toMillis: Long): List<HistorySample>
    suspend fun pruneBefore(cutoffMillis: Long): Int
    suspend fun count(): Long
    suspend fun latestTimestamp(): Long?

    /** Deletes the samples of one BMS (user-initiated, or when the BMS is removed). */
    suspend fun clearDevice(deviceKey: String)

    /** Deletes every stored sample (user-initiated withdrawal of the local history). */
    suspend fun clear()
}

/**
 * Schema plan for `history.db`. Kept pure so the version decision is unit-tested without a
 * database. History stays a disposable cache: anything other than the one known upgrade path
 * (or a failure while running it) recreates the table instead of crashing.
 */
object HistorySchema {
    const val VERSION = 2
    const val TABLE = "samples"
    const val LEGACY_TABLE = "samples_v1"

    sealed interface Plan {
        /** v1 → v2: keep samples and assign them to the last connected device. */
        data class MigrateV1(val deviceKey: String) : Plan

        /** Drop and recreate (unknown version, downgrade, or no device to attribute v1 data to). */
        object Recreate : Plan
    }

    fun plan(oldVersion: Int, newVersion: Int, legacyDeviceKey: String?): Plan =
        if (oldVersion == 1 && newVersion == VERSION && legacyDeviceKey != null) Plan.MigrateV1(legacyDeviceKey)
        else Plan.Recreate

    val createStatements: List<String> = listOf(
        "CREATE TABLE $TABLE (" +
            "device TEXT NOT NULL, " +
            "ts INTEGER NOT NULL, " +
            "soc INTEGER NOT NULL, " +
            "pack_mv INTEGER NOT NULL, " +
            "current_ma INTEGER NOT NULL, " +
            "delta_mv INTEGER, " +
            "PRIMARY KEY(device, ts))",
        "CREATE INDEX idx_samples_device_ts ON $TABLE(device, ts)"
    )

    /** v1 table had `ts` as primary key and no device column. [deviceKey] is bound as `?`. */
    fun migrateV1Statements(): List<String> = listOf(
        "ALTER TABLE $TABLE RENAME TO $LEGACY_TABLE",
        "DROP INDEX IF EXISTS idx_samples_ts"
    ) + createStatements + listOf(
        "INSERT INTO $TABLE (device, ts, soc, pack_mv, current_ma, delta_mv) " +
            "SELECT ?, ts, soc, pack_mv, current_ma, delta_mv FROM $LEGACY_TABLE",
        "DROP TABLE $LEGACY_TABLE"
    )

    val dropStatements: List<String> = listOf(
        "DROP TABLE IF EXISTS $LEGACY_TABLE",
        "DROP TABLE IF EXISTS $TABLE"
    )
}
