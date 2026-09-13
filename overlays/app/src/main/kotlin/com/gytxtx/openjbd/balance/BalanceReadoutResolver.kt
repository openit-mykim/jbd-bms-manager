package com.gytxtx.openjbd.balance

data class BalanceReadout(
    val activeBalancingCellCount: Int,
    val balanceCurrentA: Float?
)

object BalanceReadoutResolver {
    fun resolve(
        balanceStates: BooleanArray,
        visibleCellCount: Int,
        hasBalanceCurrent: Boolean,
        balanceCurrentA: Float
    ): BalanceReadout = BalanceReadout(
        activeBalancingCellCount = balanceStates
            .take(visibleCellCount.coerceAtLeast(0))
            .count { it },
        balanceCurrentA = balanceCurrentA.takeIf { hasBalanceCurrent }
    )
}
