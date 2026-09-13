package com.gytxtx.openjbd.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceCapabilityResolverTest {
    @Test
    fun unknownEvidenceResolvesToEmptyCapabilities() {
        val capabilities = MaintenanceCapabilityResolver().resolve(
            DeviceCapabilityEvidence(null, null, null, null)
        )

        assertEquals(emptySet<MaintenanceCapability>(), capabilities.asSet())
        assertAllFlagsFalse(capabilities)
    }

    @Test
    fun exactFixtureEntryEnablesOnlyItsSpecificFlags() {
        val safeRead = SafeRegisterReadEvidence("basic-info", "v1-length-27")
        val entry = fixtureEntry(
            safeRead = safeRead,
            capabilities = setOf(
                MaintenanceCapability.FACTORY_MODE,
                MaintenanceCapability.CELL_VOLTAGE_CALIBRATION,
                MaintenanceCapability.BACKUP_RESTORE
            )
        )

        val capabilities = MaintenanceCapabilityResolver(
            LocalCompatibilityTable(listOf(entry))
        ).resolve(fullEvidence(safeRead))

        assertTrue(capabilities.supportsFactoryMode)
        assertTrue(capabilities.supportsCellVoltageCalibration)
        assertTrue(capabilities.supportsBackupRestore)
        assertFalse(capabilities.supportsPackVoltageCalibration)
        assertFalse(capabilities.supportsProtectionWrite)
        assertEquals(entry.capabilities, capabilities.asSet())
    }

    @Test
    fun partialIdentityOrMissingSafeReadStaysReadOnly() {
        val requiredRead = SafeRegisterReadEvidence("basic-info", "v1-length-27")
        val resolver = MaintenanceCapabilityResolver(
            LocalCompatibilityTable(
                listOf(fixtureEntry(requiredRead, setOf(MaintenanceCapability.MOS_CONTROL)))
            )
        )

        val partialIdentity = fullEvidence(requiredRead).copy(hardwareRevision = null)
        val missingRead = fullEvidence(requiredRead).copy(safeRegisterReads = emptySet())

        assertEquals(emptySet<MaintenanceCapability>(), resolver.resolve(partialIdentity).asSet())
        assertEquals(emptySet<MaintenanceCapability>(), resolver.resolve(missingRead).asSet())
    }

    @Test
    fun multipleMatchingProfilesAreAmbiguousAndStayReadOnly() {
        val safeRead = SafeRegisterReadEvidence("basic-info", "v1-length-27")
        val table = LocalCompatibilityTable(
            listOf(
                fixtureEntry(safeRead, setOf(MaintenanceCapability.MOS_CONTROL)),
                fixtureEntry(safeRead, setOf(MaintenanceCapability.CAPACITY_WRITE))
            )
        )

        val capabilities = MaintenanceCapabilityResolver(table).resolve(fullEvidence(safeRead))

        assertEquals(emptySet<MaintenanceCapability>(), capabilities.asSet())
        assertAllFlagsFalse(capabilities)
    }

    private fun fixtureEntry(
        safeRead: SafeRegisterReadEvidence,
        capabilities: Set<MaintenanceCapability>
    ) = LocalCompatibilityEntry(
        exactBmsModel = "SP14S004",
        hardwareRevision = "HW-3",
        firmwareVersion = "FW-2.1",
        protocolVariant = "JBD-V1",
        requiredSafeRegisterReads = setOf(safeRead),
        capabilities = capabilities
    )

    private fun fullEvidence(safeRead: SafeRegisterReadEvidence) = DeviceCapabilityEvidence(
        exactBmsModel = "SP14S004",
        hardwareRevision = "HW-3",
        firmwareVersion = "FW-2.1",
        protocolVariant = "JBD-V1",
        safeRegisterReads = setOf(safeRead)
    )

    private fun assertAllFlagsFalse(capabilities: MaintenanceCapabilities) {
        assertFalse(capabilities.supportsFactoryMode)
        assertFalse(capabilities.supportsPackVoltageCalibration)
        assertFalse(capabilities.supportsCellVoltageCalibration)
        assertFalse(capabilities.supportsCurrentCalibration)
        assertFalse(capabilities.supportsNtcCalibration)
        assertFalse(capabilities.supportsProtectionWrite)
        assertFalse(capabilities.supportsBalanceConfig)
        assertFalse(capabilities.supportsMosControl)
        assertFalse(capabilities.supportsCapacityWrite)
        assertFalse(capabilities.supportsBackupRestore)
    }
}
