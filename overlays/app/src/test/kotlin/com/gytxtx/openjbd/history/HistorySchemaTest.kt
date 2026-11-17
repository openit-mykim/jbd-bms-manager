package com.gytxtx.openjbd.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySchemaTest {
    @Test
    fun v1UpgradeWithLastDeviceMigratesToThatDevice() {
        assertEquals(
            HistorySchema.Plan.MigrateV1("A4:C1:38:00:00:01"),
            HistorySchema.plan(1, HistorySchema.VERSION, "A4:C1:38:00:00:01")
        )
    }

    @Test
    fun v1UpgradeWithoutLastDeviceRecreates() {
        assertEquals(HistorySchema.Plan.Recreate, HistorySchema.plan(1, HistorySchema.VERSION, null))
    }

    @Test
    fun unknownOrDowngradeVersionsRecreate() {
        assertEquals(HistorySchema.Plan.Recreate, HistorySchema.plan(0, HistorySchema.VERSION, "A4:C1:38:00:00:01"))
        assertEquals(HistorySchema.Plan.Recreate, HistorySchema.plan(7, HistorySchema.VERSION, "A4:C1:38:00:00:01"))
        assertEquals(HistorySchema.Plan.Recreate, HistorySchema.plan(1, 3, "A4:C1:38:00:00:01"))
    }

    @Test
    fun migrationCopiesEveryV1ColumnWithOneBoundDeviceKey() {
        val statements = HistorySchema.migrateV1Statements()
        val insert = statements.single { it.startsWith("INSERT") }
        listOf("ts", "soc", "pack_mv", "current_ma", "delta_mv").forEach { column ->
            assertTrue(column, insert.contains(column))
        }
        assertEquals(1, insert.count { it == '?' })
        assertEquals(1, statements.count { it.contains('?') })
        assertTrue(statements.first().startsWith("ALTER TABLE samples RENAME"))
        assertTrue(statements.last().startsWith("DROP TABLE ${HistorySchema.LEGACY_TABLE}"))
    }

    @Test
    fun newSchemaKeysSamplesByDeviceAndTime() {
        assertTrue(HistorySchema.createStatements.first().contains("PRIMARY KEY(device, ts)"))
        assertTrue(HistorySchema.dropStatements.any { it.contains(HistorySchema.TABLE) })
    }
}
