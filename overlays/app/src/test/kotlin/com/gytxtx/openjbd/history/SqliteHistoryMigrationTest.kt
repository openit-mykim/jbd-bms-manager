package com.gytxtx.openjbd.history

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gytxtx.openjbd.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs the real SQLite upgrade path (issue #24, D022) on Robolectric: a history.db written by
 * v0.1.2 (schema v1, no device column) must open under schema v2 without crashing, and its
 * samples must belong to the last connected device.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class SqliteHistoryMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() {
        context.deleteDatabase(DB)
        AppSettings.prefs(context).edit().clear().commit()
    }

    @After fun tearDown() {
        context.deleteDatabase(DB)
    }

    /** The exact v0.1.2 schema (SqliteHistoryStore DATABASE_VERSION = 1). */
    private class V1Helper(context: Context, private val version: Int = 1) :
        SQLiteOpenHelper(context, DB, null, version) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE samples (ts INTEGER PRIMARY KEY, soc INTEGER NOT NULL, " +
                    "pack_mv INTEGER NOT NULL, current_ma INTEGER NOT NULL, delta_mv INTEGER)"
            )
            db.execSQL("CREATE INDEX idx_samples_ts ON samples(ts)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun writeV1(rows: Int, version: Int = 1) {
        V1Helper(context, version).writableDatabase.use { db ->
            repeat(rows) { i ->
                db.execSQL(
                    "INSERT INTO samples (ts, soc, pack_mv, current_ma, delta_mv) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(1_000L + i, 50 + i, 52_000 + i, -1_200, if (i == 0) null else 5)
                )
            }
        }
    }

    @Test
    fun v1SamplesMoveToTheLastConnectedDevice() = runBlocking {
        writeV1(rows = 3)
        AppSettings.prefs(context).edit().putString(AppSettings.PREF_LAST_DEVICE_ADDRESS, "a4:c1:38:00:11:22").commit()

        val store = SqliteHistoryStore(context)
        val migrated = store.query("A4:C1:38:00:11:22", 0, Long.MAX_VALUE)

        assertEquals(3, migrated.size)
        assertEquals(listOf(1_000L, 1_001L, 1_002L), migrated.map { it.timestampMillis })
        assertNull(migrated.first().cellDeltaMillivolts)
        assertEquals(51, migrated[1].socPercent)
        assertTrue(store.query("11:22:33:44:55:66", 0, Long.MAX_VALUE).isEmpty())
        // New samples for the same device keep working after the migration.
        store.append("A4:C1:38:00:11:22", HistorySample(2_000L, 70, 53_000, 0, 4))
        assertEquals(4, store.query("A4:C1:38:00:11:22", 0, Long.MAX_VALUE).size)
    }

    @Test
    fun v1WithoutKnownDeviceIsDiscardedNotCrashed() = runBlocking {
        writeV1(rows = 2)

        val store = SqliteHistoryStore(context)

        assertEquals(0L, store.count())
        store.append("A4:C1:38:00:11:22", HistorySample(2_000L, 70, 53_000, 0, 4))
        assertEquals(1L, store.count())
    }

    @Test
    fun newerSchemaIsRecreatedOnDowngrade() = runBlocking {
        writeV1(rows = 2, version = 99)

        val store = SqliteHistoryStore(context)

        assertEquals(0L, store.count())
        store.append("A4:C1:38:00:11:22", HistorySample(2_000L, 70, 53_000, 0, 4))
        assertEquals(1L, store.count())
    }

    private companion object {
        const val DB = "history.db"
    }
}
