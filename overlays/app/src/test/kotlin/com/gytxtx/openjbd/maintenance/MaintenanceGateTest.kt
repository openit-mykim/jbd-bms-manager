package com.gytxtx.openjbd.maintenance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceGateTest {
    @Test
    fun startsLocked() {
        assertFalse(MaintenanceGate().isUnlocked)
    }

    @Test
    fun unconfirmedRequestStaysLocked() {
        val gate = MaintenanceGate()

        gate.requestUnlock(confirmed = false)

        assertFalse(gate.isUnlocked)
    }

    @Test
    fun confirmedRequestUnlocks() {
        val gate = MaintenanceGate()

        gate.requestUnlock(confirmed = true)

        assertTrue(gate.isUnlocked)
    }

    @Test
    fun relockReturnsToLocked() {
        val gate = MaintenanceGate()
        gate.requestUnlock(confirmed = true)

        gate.relock()

        assertFalse(gate.isUnlocked)
    }
}
