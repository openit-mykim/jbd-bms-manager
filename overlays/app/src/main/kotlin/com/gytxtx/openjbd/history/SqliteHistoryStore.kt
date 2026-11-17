package com.gytxtx.openjbd.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.gytxtx.openjbd.AppSettings
import com.gytxtx.openjbd.registry.DeviceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SqliteHistoryStore @Inject constructor(
    @ApplicationContext context: Context
) : HistoryStore {
    private val appContext = context.applicationContext
    @Volatile private var helper = HistoryDatabaseHelper(appContext)

    /**
     * History is a disposable local cache (7-day retention). Mobile dev standard 5.4: a read
     * failure discards the cache instead of crashing; the next sample recreates it.
     */
    private inline fun <T> recovering(fallback: T, block: () -> T): T = try {
        block()
    } catch (error: SQLiteException) {
        Log.w(TAG, "History database unreadable; discarding it", error)
        resetDatabase()
        fallback
    }

    @Synchronized
    private fun resetDatabase() {
        runCatching { helper.close() }
        appContext.deleteDatabase(DATABASE_NAME)
        helper = HistoryDatabaseHelper(appContext)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            recovering(Unit) { helper.writableDatabase.delete(HistorySchema.TABLE, null, null) }
        }
    }

    override suspend fun clearDevice(deviceKey: String) {
        withContext(Dispatchers.IO) {
            recovering(Unit) {
                helper.writableDatabase.delete(HistorySchema.TABLE, "$COLUMN_DEVICE = ?", arrayOf(deviceKey))
            }
        }
    }

    override suspend fun append(deviceKey: String, sample: HistorySample) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COLUMN_DEVICE, deviceKey)
                put(COLUMN_TIMESTAMP, sample.timestampMillis)
                put(COLUMN_SOC, sample.socPercent)
                put(COLUMN_PACK_MILLIVOLTS, sample.packMillivolts)
                put(COLUMN_CURRENT_MILLIAMPS, sample.currentMilliamps)
                if (sample.cellDeltaMillivolts == null) {
                    putNull(COLUMN_DELTA_MILLIVOLTS)
                } else {
                    put(COLUMN_DELTA_MILLIVOLTS, sample.cellDeltaMillivolts)
                }
            }
            check(
                helper.writableDatabase.insertWithOnConflict(
                    HistorySchema.TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                ) != -1L
            ) { "Failed to append history sample" }
        }
    }

    override suspend fun query(deviceKey: String, fromMillis: Long, toMillis: Long): List<HistorySample> =
        withContext(Dispatchers.IO) { recovering(emptyList()) { queryUnsafe(deviceKey, fromMillis, toMillis) } }

    private fun queryUnsafe(deviceKey: String, fromMillis: Long, toMillis: Long): List<HistorySample> {
        val samples = mutableListOf<HistorySample>()
        helper.readableDatabase.query(
            HistorySchema.TABLE,
            ALL_COLUMNS,
            "$COLUMN_DEVICE = ? AND $COLUMN_TIMESTAMP BETWEEN ? AND ?",
            arrayOf(deviceKey, fromMillis.toString(), toMillis.toString()),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        ).use { cursor ->
            val timestampIndex = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
            val socIndex = cursor.getColumnIndexOrThrow(COLUMN_SOC)
            val packIndex = cursor.getColumnIndexOrThrow(COLUMN_PACK_MILLIVOLTS)
            val currentIndex = cursor.getColumnIndexOrThrow(COLUMN_CURRENT_MILLIAMPS)
            val deltaIndex = cursor.getColumnIndexOrThrow(COLUMN_DELTA_MILLIVOLTS)
            while (cursor.moveToNext()) {
                samples += HistorySample(
                    timestampMillis = cursor.getLong(timestampIndex),
                    socPercent = cursor.getInt(socIndex),
                    packMillivolts = cursor.getInt(packIndex),
                    currentMilliamps = cursor.getInt(currentIndex),
                    cellDeltaMillivolts = if (cursor.isNull(deltaIndex)) null else cursor.getInt(deltaIndex)
                )
            }
        }
        return samples
    }

    override suspend fun pruneBefore(cutoffMillis: Long): Int = withContext(Dispatchers.IO) {
        recovering(0) { pruneUnsafe(cutoffMillis) }
    }

    private fun pruneUnsafe(cutoffMillis: Long): Int {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            var deleted = database.delete(
                HistorySchema.TABLE,
                "$COLUMN_TIMESTAMP < ?",
                arrayOf(cutoffMillis.toString())
            )
            // The sample cap applies per BMS so one busy pack cannot evict another pack's history.
            for (device in devices(database)) {
                val excess = count(database, device) - HistoryPolicy.MAX_SAMPLES
                if (excess > 0L) {
                    deleted += database.delete(
                        HistorySchema.TABLE,
                        "$COLUMN_DEVICE = ? AND $COLUMN_TIMESTAMP IN (" +
                            "SELECT $COLUMN_TIMESTAMP FROM ${HistorySchema.TABLE} WHERE $COLUMN_DEVICE = ? " +
                            "ORDER BY $COLUMN_TIMESTAMP ASC LIMIT ?)",
                        arrayOf(device, device, excess.toString())
                    )
                }
            }
            database.setTransactionSuccessful()
            return deleted
        } finally {
            database.endTransaction()
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        recovering(0L) {
            helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${HistorySchema.TABLE}", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        }
    }

    override suspend fun latestTimestamp(): Long? = withContext(Dispatchers.IO) {
        recovering(null) {
            helper.readableDatabase.rawQuery(
                "SELECT MAX($COLUMN_TIMESTAMP) FROM ${HistorySchema.TABLE}",
                null
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }
    }

    private fun devices(database: SQLiteDatabase): List<String> =
        database.rawQuery("SELECT DISTINCT $COLUMN_DEVICE FROM ${HistorySchema.TABLE}", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun count(database: SQLiteDatabase, device: String): Long = database.rawQuery(
        "SELECT COUNT(*) FROM ${HistorySchema.TABLE} WHERE $COLUMN_DEVICE = ?",
        arrayOf(device)
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    private class HistoryDatabaseHelper(private val context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, HistorySchema.VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            HistorySchema.createStatements.forEach(database::execSQL)
        }

        /**
         * v1 (single device) → v2 (per device): the existing samples are assigned to the last
         * connected device (OpenJBD `last_device_address`). Any other version change, a missing
         * legacy device or a failure while migrating recreates the table (history is disposable;
         * a schema mismatch must never crash the app).
         */
        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            val legacyKey = DeviceKeys.normalize(
                AppSettings.prefs(context).getString(AppSettings.PREF_LAST_DEVICE_ADDRESS, null)
            )
            when (val plan = HistorySchema.plan(oldVersion, newVersion, legacyKey)) {
                is HistorySchema.Plan.MigrateV1 -> try {
                    HistorySchema.migrateV1Statements().forEach { statement ->
                        if (statement.contains('?')) database.execSQL(statement, arrayOf(plan.deviceKey))
                        else database.execSQL(statement)
                    }
                } catch (error: SQLiteException) {
                    Log.w(TAG, "History v1 migration failed; recreating", error)
                    recreate(database)
                }
                HistorySchema.Plan.Recreate -> recreate(database)
            }
        }

        override fun onDowngrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            recreate(database)

        private fun recreate(database: SQLiteDatabase) {
            HistorySchema.dropStatements.forEach(database::execSQL)
            onCreate(database)
        }
    }

    private companion object {
        const val TAG = "SqliteHistoryStore"
        const val DATABASE_NAME = "history.db"
        const val COLUMN_DEVICE = "device"
        const val COLUMN_TIMESTAMP = "ts"
        const val COLUMN_SOC = "soc"
        const val COLUMN_PACK_MILLIVOLTS = "pack_mv"
        const val COLUMN_CURRENT_MILLIAMPS = "current_ma"
        const val COLUMN_DELTA_MILLIVOLTS = "delta_mv"
        val ALL_COLUMNS = arrayOf(
            COLUMN_TIMESTAMP,
            COLUMN_SOC,
            COLUMN_PACK_MILLIVOLTS,
            COLUMN_CURRENT_MILLIAMPS,
            COLUMN_DELTA_MILLIVOLTS
        )
    }
}
