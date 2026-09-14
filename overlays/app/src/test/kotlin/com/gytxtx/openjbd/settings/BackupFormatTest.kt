package com.gytxtx.openjbd.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    @Test
    fun serializeParseRoundTripPreservesAllGroupsAndKoreanEscapes() {
        val backup = completeBackup().copy(
            device = BackupDeviceIdentity(
                name = "작업용 \"배터리\"\nA\\B",
                address = "AA:BB:CC:DD:EE:FF",
                serial = "일련-001",
                model = "모델-가"
            )
        )

        val json = BackupFormat.serialize(backup)
        val parsed = BackupFormat.parse(json)

        assertEquals(backup, parsed.backup)
        assertTrue(parsed.issues.isEmpty())
        assertTrue(json.contains("작업용 \\\"배터리\\\"\\nA\\\\B"))
    }

    @Test
    fun parserDecodesUnicodeEscapes() {
        val json = BackupFormat.serialize(completeBackup())
            .replace("테스트 BMS", "\\uD14C\\uC2A4\\uD2B8 BMS")

        assertEquals("테스트 BMS", BackupFormat.parse(json).backup?.device?.name)
    }

    @Test
    fun unknownRootKeyIsSkippedAndReported() {
        val json = BackupFormat.serialize(completeBackup()).replaceFirst("{", "{\"future\":true,")
        val parsed = BackupFormat.parse(json)

        assertNotNull(parsed.backup)
        assertTrue(parsed.issues.any {
            it.kind == BackupIssueKind.UNKNOWN_KEY && it.path == "$.future"
        })
    }

    @Test
    fun unknownDeviceKeyIsSkippedAndReported() {
        val json = BackupFormat.serialize(completeBackup())
            .replace("\"device\":{", "\"device\":{\"firmware\":\"x\",")
        val parsed = BackupFormat.parse(json)

        assertNotNull(parsed.backup)
        assertTrue(parsed.issues.any { it.path == "$.device.firmware" })
    }

    @Test
    fun unknownSettingFieldIsExcludedAndReported() {
        val json = BackupFormat.serialize(completeBackup()).replace(
            "]}",
            ",{\"key\":\"futureField\",\"raw\":1,\"display\":\"1\",\"unit\":\"\"}]}"
        )
        val parsed = BackupFormat.parse(json)

        assertEquals(BackupFormat.configurationFields.size, parsed.backup?.fields?.size)
        assertTrue(parsed.issues.any { it.kind == BackupIssueKind.UNKNOWN_FIELD })
    }

    @Test
    fun missingGroupIsReportedWithoutRejectingOtherGroups() {
        val withoutCapacity = completeBackup().copy(
            fields = completeBackup().fields.filter {
                it.key != SettingField.DESIGN_CAPACITY.key
            }
        )
        val parsed = BackupFormat.parse(BackupFormat.serialize(withoutCapacity))

        assertNotNull(parsed.backup)
        assertEquals(setOf(SettingsGroup.CAPACITY), parsed.missingGroups)
        assertTrue(parsed.issues.any {
            it.kind == BackupIssueKind.MISSING_GROUP && it.detail == SettingsGroup.CAPACITY.name
        })
    }

    @Test
    fun missingFieldPropertyExcludesOnlyThatEntry() {
        val json = minimalJson(
            "{\"key\":\"designCapacity\",\"display\":\"100 Ah\",\"unit\":\"Ah\"}"
        )
        val parsed = BackupFormat.parse(json)

        assertNotNull(parsed.backup)
        assertTrue(parsed.backup!!.fields.isEmpty())
        assertTrue(parsed.issues.any {
            it.kind == BackupIssueKind.MISSING_VALUE && it.path.endsWith(".raw")
        })
    }

    @Test
    fun nonIntegerRawIsInvalidAndExcluded() {
        val parsed = BackupFormat.parse(minimalJson(
            "{\"key\":\"designCapacity\",\"raw\":1.5,\"display\":\"x\",\"unit\":\"Ah\"}"
        ))

        assertTrue(parsed.backup!!.fields.isEmpty())
        assertTrue(parsed.issues.any { it.kind == BackupIssueKind.INVALID_VALUE })
    }

    @Test
    fun malformedJsonReturnsNoBackup() {
        val parsed = BackupFormat.parse("{\"formatVersion\":1")

        assertNull(parsed.backup)
        assertEquals(BackupIssueKind.MALFORMED_JSON, parsed.issues.single().kind)
    }

    @Test
    fun unsupportedVersionIsParsedButReported() {
        val json = BackupFormat.serialize(completeBackup().copy(formatVersion = 2))
        val parsed = BackupFormat.parse(json)

        assertNotNull(parsed.backup)
        assertTrue(parsed.issues.any { it.kind == BackupIssueKind.UNSUPPORTED_FORMAT })
    }

    @Test
    fun diffSkipsEqualAndIncludesChangedValues() {
        val backup = completeBackup()
        val current = currentValues().toMutableMap().apply {
            this[SettingField.BAL_WINDOW] = 20
        }

        val diff = BackupFormat.buildRestoreDiff(
            BackupFormat.parse(BackupFormat.serialize(backup)),
            current
        )

        assertEquals(
            listOf(RestoreFieldDiff(SettingField.BAL_WINDOW, 20, 10)),
            diff.changes
        )
    }

    @Test
    fun outOfRangeValueIsExcludedAtDiffTime() {
        val backup = completeBackup().withRaw(SettingField.BAL_WINDOW, 1)
        val diff = BackupFormat.buildRestoreDiff(
            BackupFormat.parse(BackupFormat.serialize(backup)),
            currentValues() + (SettingField.BAL_WINDOW to 20)
        )

        assertFalse(diff.changes.any { it.field == SettingField.BAL_WINDOW })
        assertTrue(diff.issues.any {
            it.kind == BackupIssueKind.INVALID_VALUE && it.path.contains("balWindow")
        })
    }

    @Test
    fun missingCurrentValueExcludesChangeAndReportsIssue() {
        val current = currentValues() - SettingField.DESIGN_CAPACITY
        val diff = BackupFormat.buildRestoreDiff(
            BackupFormat.parse(BackupFormat.serialize(completeBackup())),
            current
        )

        assertFalse(diff.changes.any { it.field == SettingField.DESIGN_CAPACITY })
        assertTrue(diff.issues.any { it.kind == BackupIssueKind.CURRENT_VALUE_UNAVAILABLE })
    }

    @Test
    fun invalidRelationalValueIsExcludedAtDiffTime() {
        val backup = completeBackup().withRaw(SettingField.COVP_RELEASE, 4_300)
        val diff = BackupFormat.buildRestoreDiff(
            BackupFormat.parse(BackupFormat.serialize(backup)),
            currentValues() + (SettingField.COVP_RELEASE to 4_100)
        )

        assertFalse(diff.changes.any { it.field == SettingField.COVP_RELEASE })
        assertTrue(diff.issues.any { it.path.contains("covpRelease") })
    }

    @Test
    fun duplicateFieldKeepsFirstAndReportsDuplicate() {
        val backup = completeBackup().let { original ->
            original.copy(fields = original.fields + original.fields.first().copy(raw = 99))
        }
        val parsed = BackupFormat.parse(BackupFormat.serialize(backup))

        assertEquals(BackupFormat.configurationFields.size, parsed.backup!!.fields.size)
        assertTrue(parsed.issues.any { it.kind == BackupIssueKind.DUPLICATE_FIELD })
    }

    private fun completeBackup(): ConfigurationBackup = ConfigurationBackup(
        exportedAtUtc = "2026-09-14T12:34:56Z",
        appVersion = "0.1.0-test",
        device = BackupDeviceIdentity("테스트 BMS", "AA:BB:CC:DD:EE:FF", "S-1", "M-1"),
        fields = BackupFormat.configurationFields.map { field ->
            BackupFieldEntry(field.key, raw(field), "display-${field.key}", field.register.unit)
        }
    )

    private fun currentValues(): Map<SettingField, Int> =
        BackupFormat.configurationFields.associateWith(::raw)

    private fun raw(field: SettingField): Int = when (field) {
        SettingField.BAL_START -> 3_500
        SettingField.BAL_WINDOW -> 10
        SettingField.BALANCE_ENABLE,
        SettingField.CHARGE_BALANCE_ENABLE -> 1
        SettingField.COVP -> 4_200
        SettingField.COVP_RELEASE -> 4_100
        SettingField.CUVP -> 2_800
        SettingField.CUVP_RELEASE -> 3_000
        SettingField.POVP -> 5_880
        SettingField.POVP_RELEASE -> 5_760
        SettingField.PUVP -> 4_000
        SettingField.PUVP_RELEASE -> 4_200
        SettingField.CHG_OVERCURRENT -> 10_000
        SettingField.DSG_OVERCURRENT -> -10_000
        SettingField.CHG_OVER_TEMP -> 3_233
        SettingField.CHG_OVER_TEMP_RELEASE -> 3_133
        SettingField.CHG_UNDER_TEMP -> 2_633
        SettingField.CHG_UNDER_TEMP_RELEASE -> 2_733
        SettingField.DSG_OVER_TEMP -> 3_333
        SettingField.DSG_OVER_TEMP_RELEASE -> 3_233
        SettingField.DSG_UNDER_TEMP -> 2_533
        SettingField.DSG_UNDER_TEMP_RELEASE -> 2_633
        SettingField.DESIGN_CAPACITY -> 10_000
        SettingField.MOS_CHARGE_DISABLE,
        SettingField.MOS_DISCHARGE_DISABLE -> error("MOS is not part of backup v1")
    }

    private fun ConfigurationBackup.withRaw(
        field: SettingField,
        raw: Int
    ): ConfigurationBackup = copy(fields = fields.map {
        if (it.key == field.key) it.copy(raw = raw) else it
    })

    private fun minimalJson(field: String): String =
        """{"formatVersion":1,"exportedAtUtc":"2026-09-14T00:00:00Z","appVersion":"x","device":{"name":"n","address":"a"},"fields":[$field]}"""
}
