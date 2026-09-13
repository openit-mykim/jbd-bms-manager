package com.gytxtx.openjbd.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceReadoutResolverTest {
    @Test
    fun resolveCountsOnlyBalancingCellsThatHaveVisibleVoltageRows() {
        val result = BalanceReadoutResolver.resolve(
            balanceStates = booleanArrayOf(true, false, true, true),
            visibleCellCount = 3,
            hasBalanceCurrent = false,
            balanceCurrentA = 1.25f
        )

        assertEquals(2, result.activeBalancingCellCount)
    }

    @Test
    fun resolveHidesBalanceCurrentWhenDeviceDoesNotSupportIt() {
        val result = BalanceReadoutResolver.resolve(
            balanceStates = booleanArrayOf(),
            visibleCellCount = 0,
            hasBalanceCurrent = false,
            balanceCurrentA = 1.25f
        )

        assertNull(result.balanceCurrentA)
    }

    @Test
    fun resolvePreservesSupportedZeroBalanceCurrent() {
        val result = BalanceReadoutResolver.resolve(
            balanceStates = booleanArrayOf(false),
            visibleCellCount = 1,
            hasBalanceCurrent = true,
            balanceCurrentA = 0f
        )

        assertEquals(0f, result.balanceCurrentA ?: Float.NaN, 0f)
    }
}
