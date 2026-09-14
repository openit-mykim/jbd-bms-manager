package com.gytxtx.openjbd.overview

import org.junit.Assert.assertEquals
import org.junit.Test

class SocBandTest {
    @Test
    fun dangerBoundaryIsDanger() {
        assertEquals(SocBand.DANGER, SocBands.bandOf(10))
    }

    @Test
    fun firstCautionValueIsCaution() {
        assertEquals(SocBand.CAUTION, SocBands.bandOf(11))
    }

    @Test
    fun cautionBoundaryIsCaution() {
        assertEquals(SocBand.CAUTION, SocBands.bandOf(25))
    }

    @Test
    fun firstNormalValueIsNormal() {
        assertEquals(SocBand.NORMAL, SocBands.bandOf(26))
    }

    @Test
    fun zeroIsDanger() {
        assertEquals(SocBand.DANGER, SocBands.bandOf(0))
    }

    @Test
    fun commonMidpointIsNormal() {
        assertEquals(SocBand.NORMAL, SocBands.bandOf(50))
    }

    @Test
    fun oneHundredIsNormal() {
        assertEquals(SocBand.NORMAL, SocBands.bandOf(100))
    }

    @Test
    fun belowRangeIsClampedToDanger() {
        assertEquals(SocBand.DANGER, SocBands.bandOf(-5))
    }

    @Test
    fun aboveRangeIsClampedToNormal() {
        assertEquals(SocBand.NORMAL, SocBands.bandOf(105))
    }

    @Test
    fun exposedThresholdsMatchBandBoundaries() {
        assertEquals(10, SocBands.DANGER_MAX_PERCENT)
        assertEquals(25, SocBands.CAUTION_MAX_PERCENT)
        assertEquals(
            SocBand.DANGER,
            SocBands.bandOf(SocBands.DANGER_MAX_PERCENT)
        )
        assertEquals(
            SocBand.CAUTION,
            SocBands.bandOf(SocBands.CAUTION_MAX_PERCENT)
        )
    }
}
