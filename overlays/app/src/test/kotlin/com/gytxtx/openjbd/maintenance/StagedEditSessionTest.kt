package com.gytxtx.openjbd.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedEditSessionTest {
    @Test
    fun stageAddsValidatedChange() {
        val session = StagedEditSession()

        val change = session.stage("cell-ovp", "Cell OVP", "4.250 V", "4.200 V")

        assertEquals(1, session.size)
        assertEquals(ValidationResult.Valid, change.validation)
        assertEquals(listOf(change), session.stagedChanges())
    }

    @Test
    fun stagingSameKeyReplacesValueWithoutChangingOrder() {
        val session = StagedEditSession()
        session.stage("cell-ovp", "Cell OVP", "4.250 V", "4.200 V")
        session.stage("cell-uvp", "Cell UVP", "2.800 V", "3.000 V")

        session.stage("cell-ovp", "Cell OVP", "4.250 V", "4.150 V")

        assertEquals(listOf("cell-ovp", "cell-uvp"), session.reviewItems().map { it.fieldKey })
        assertEquals("4.150 V", session.reviewItems().first().proposedDisplayValue)
        assertEquals(2, session.size)
    }

    @Test
    fun removeReturnsChangeAndMakesSessionEmpty() {
        val session = StagedEditSession()
        session.stage("balance-delta", "Balance delta", "15 mV", "10 mV")

        val removed = session.remove("balance-delta")

        assertEquals("balance-delta", removed?.fieldKey)
        assertTrue(session.isEmpty)
        assertNull(session.remove("missing"))
    }

    @Test
    fun clearRemovesAllChanges() {
        val session = StagedEditSession()
        session.stage("one", "One", "1", "2")
        session.stage("two", "Two", "2", "3")

        session.clear()

        assertEquals(0, session.size)
        assertTrue(session.reviewItems().isEmpty())
    }

    @Test
    fun reviewItemsFollowStagingOrderAndUseStableSummaryFormat() {
        val session = StagedEditSession()
        session.stage("cell-ovp", "Cell OVP", "4.250 V", "4.200 V")
        session.stage("balance-delta", "Balance delta", "15 mV", "10 mV")

        val review = session.reviewItems()

        assertEquals(listOf("cell-ovp", "balance-delta"), review.map { it.fieldKey })
        assertEquals("Cell OVP 4.250 V → 4.200 V", review.first().summary())
        assertEquals("Balance delta 15 mV → 10 mV", review.last().summary())
    }

    @Test
    fun validatorStoresValidAndInvalidStatesPerChange() {
        val session = StagedEditSession { change ->
            if (change.proposedDisplayValue == "unsafe") {
                ValidationResult.Invalid("outside verified range")
            } else {
                ValidationResult.Valid
            }
        }

        session.stage("safe", "Safe", "1", "2")
        session.stage("unsafe", "Unsafe", "1", "unsafe")

        assertEquals(ValidationResult.Valid, session.reviewItems()[0].validation)
        assertEquals(
            ValidationResult.Invalid("outside verified range"),
            session.reviewItems()[1].validation
        )
        assertFalse(session.isEmpty)
    }
}
