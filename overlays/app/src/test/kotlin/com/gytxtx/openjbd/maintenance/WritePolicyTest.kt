package com.gytxtx.openjbd.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WritePolicyTest {
    private val identity = DeviceIdentity("AA:BB", "SP14S004", "4.2")

    @Test
    fun unknownDeviceIsReadOnlyWithoutConsent() {
        assertEquals(
            WriteDecision.Denied(WriteDecision.DenyReason.NOT_CONSENTED),
            WritePolicy.decide("AA:BB", true, identity, verifiedProfile = false, consented = false)
        )
    }

    @Test
    fun consentOrVerifiedProfileAllowsWhenUnlockedForSameDevice() {
        assertEquals(
            WriteDecision.Allowed(WriteDecision.Basis.EXPERIMENTAL_CONSENT, identity),
            WritePolicy.decide("AA:BB", true, identity, verifiedProfile = false, consented = true)
        )
        assertEquals(
            WriteDecision.Allowed(WriteDecision.Basis.VERIFIED_PROFILE, identity),
            WritePolicy.decide("AA:BB", true, identity, verifiedProfile = true, consented = false)
        )
    }

    @Test
    fun lockedOrUnlockedForAnotherDeviceIsDenied() {
        val locked = WriteDecision.Denied(WriteDecision.DenyReason.LOCKED)
        assertEquals(locked, WritePolicy.decide(null, false, identity, true, true))
        assertEquals(locked, WritePolicy.decide("CC:DD", true, identity, true, true))
        assertEquals(locked, WritePolicy.decide(null, true, identity, true, true))
    }

    @Test
    fun missingIdentityIsDenied() {
        assertEquals(
            WriteDecision.Denied(WriteDecision.DenyReason.NO_DEVICE_IDENTITY),
            WritePolicy.decide("AA:BB", true, null, false, true)
        )
    }

    @Test
    fun identityNeedsAddressAndFirmwareAndFingerprintIncludesFirmware() {
        assertNull(DeviceIdentity.from("AA:BB", "m", " "))
        assertNull(DeviceIdentity.from(null, "m", "1"))
        assertEquals(DeviceIdentity.UNKNOWN_MODEL, DeviceIdentity.from("AA:BB", "", "1")!!.bmsModel)
        assertEquals("AA:BB|m|1", DeviceIdentity.from("AA:BB", "m", "1")!!.fingerprint)
    }
}
