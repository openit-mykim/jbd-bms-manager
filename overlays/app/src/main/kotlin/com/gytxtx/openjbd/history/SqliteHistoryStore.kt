package com.gytxtx.openjbd.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SqliteHistoryStore @Inject constructor(
    @ApplicationContext context: Context
) : HistoryStore {
    private val helper = HistoryDatabaseHelper(context.applicationContext)

    override suspend fun append(sample: HistorySample) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
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
                    TABLE_SAMPLES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                ) != -1L
            ) { "Failed to append history sample" }
        }
    }

    override suspend fun query(fromMillis: Long, toMillis: Long): List<HistorySample> =
        withContext(Dispatchers.IO) {
            val samples = mutableListOf<HistorySample>()
            helper.readableDatabase.query(
                TABLE_SAMPLES,
                ALL_COLUMNS,
                "$COLUMN_TIMESTAMP BETWEEN ? AND ?",
                arrayOf(fromMillis.toString(), toMillis.toString()),
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
                        cellDeltaMillivolts = if (cursor.isNull(deltaIndex)) {
                            null
                        } else {
                            cursor.getInt(deltaIndex)
                        }
                    )
                }
            }
            samples
        }

    override suspend fun pruneBefore(cutoffMillis: Long): Int = withContext(Dispatchers.IO) {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            var deleted = database.delete(
                TABLE_SAMPLES,
                "$COLUMN_TIMESTAMP < ?",
                arrayOf(cutoffMillis.toString())
            )
            val excess = count(database) - HistoryPolicy.MAX_SAMPLES
            if (excess > 0L) {
                deleted += database.delete(
                    TABLE_SAMPLES,
                    "$COLUMN_TIMESTAMP IN (" +
                        "SELECT $COLUMN_TIMESTAMP FROM $TABLE_SAMPLES " +
                        "ORDER BY $COLUMN_TIMESTAMP ASC LIMIT ?)",
                    arrayOf(excess.toString())
                )
            }
            database.setTransactionSuccessful()
            deleted
        } finally {
            database.endTransaction()
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        count(helper.readableDatabase)
    }

    override suspend fun latestTimestamp(): Long? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT MAX($COLUMN_TIMESTAMP) FROM $TABLE_SAMPLES",
            null
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    private fun count(database: SQLiteDatabase): Long = database.rawQuery(
        "SELECT COUNT(*) FROM $TABLE_SAMPLES",
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    private class HistoryDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE $TABLE_SAMPLES (" +
                    "$COLUMN_TIMESTAMP INTEGER PRIMARY KEY, " +
                    "$COLUMN_SOC INTEGER NOT NULL, " +
                    "$COLUMN_PACK_MILLIVOLTS INTEGER NOT NULL, " +
                    "$COLUMN_CURRENT_MILLIAMPS INTEGER NOT NULL, " +
                    "$COLUMN_DELTA_MILLIVOLTS INTEGER)"
            )
            database.execSQL(
                "CREATE INDEX $INDEX_TIMESTAMP ON $TABLE_SAMPLES($COLUMN_TIMESTAMP)"
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_SAMPLES = "samples"
        const val INDEX_TIMESTAMP = "idx_samples_ts"
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
