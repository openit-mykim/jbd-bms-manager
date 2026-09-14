package com.gytxtx.openjbd.balance

import org.junit.Assert.assertEquals
import org.junit.Test

class CellBandTest {
    private val thresholds = CellThresholds(
        overVoltageMv = 4_250,
        underVoltageMv = 2_800,
        source = ThresholdSource.MEASURED
    )

    @Test
    fun overVoltageThresholdIsDanger() {
        assertEquals(CellSafetyBand.DANGER, bandOf(4_250, thresholds))
    }

    @Test
    fun underVoltageThresholdIsDanger() {
        assertEquals(CellSafetyBand.DANGER, bandOf(2_800, thresholds))
    }

    @Test
    fun oneMillivoltBeyondOverVoltageThresholdIsDanger() {
        assertEquals(CellSafetyBand.DANGER, bandOf(4_251, thresholds))
    }

    @Test
    fun oneMillivoltBeyondUnderVoltageThresholdIsDanger() {
        assertEquals(CellSafetyBand.DANGER, bandOf(2_799, thresholds))
    }

    @Test
    fun oneMillivoltInsideOverVoltageThresholdIsCaution() {
        assertEquals(CellSafetyBand.CAUTION, bandOf(4_249, thresholds))
    }

    @Test
    fun oneMillivoltInsideUnderVoltageThresholdIsCaution() {
        assertEquals(CellSafetyBand.CAUTION, bandOf(2_801, thresholds))
    }

    @Test
    fun exactUpperCautionMarginIsCaution() {
        assertEquals(CellSafetyBand.CAUTION, bandOf(4_200, thresholds))
    }

    @Test
    fun exactLowerCautionMarginIsCaution() {
        assertEquals(CellSafetyBand.CAUTION, bandOf(2_850, thresholds))
    }

    @Test
    fun oneMillivoltOutsideBothCautionMarginsIsNormal() {
        assertEquals(CellSafetyBand.NORMAL, bandOf(4_199, thresholds))
        assertEquals(CellSafetyBand.NORMAL, bandOf(2_851, thresholds))
    }

    @Test
    fun midpointIsNormal() {
        assertEquals(CellSafetyBand.NORMAL, bandOf(3_500, thresholds))
    }

    @Test
    fun nmcDefaultHasDocumentedValuesAndSource() {
        assertEquals(
            CellThresholds(4_250, 2_800, ThresholdSource.DEFAULT),
            CellThresholds.nmcDefault()
        )
    }

    @Test
    fun voltsConvertToMatchingWholeMillivolts() {
        assertEquals(3_438, voltsToMillivolts(3.438f))
    }

    @Test
    fun voltsToMillivoltsRoundsToNearestWholeMillivolt() {
        assertEquals(3_439, voltsToMillivolts(3.4385f))
        assertEquals(3_439, voltsToMillivolts(3.4386f))
        assertEquals(3_438, voltsToMillivolts(3.4384f))
    }
}
