package com.gytxtx.openjbd.overview

enum class SocBand {
    NORMAL,
    CAUTION,
    DANGER
}

/**
 * App display guidance based on common lithium-pack operating practice: charge immediately at
 * 10% or below and consider charging at 25% or below. These bands are not BMS protection behavior.
 */
object SocBands {
    const val DANGER_MAX_PERCENT = 10
    const val CAUTION_MAX_PERCENT = 25

    fun bandOf(socPercent: Int): SocBand = when (socPercent.coerceIn(0, 100)) {
        in 0..DANGER_MAX_PERCENT -> SocBand.DANGER
        in (DANGER_MAX_PERCENT + 1)..CAUTION_MAX_PERCENT -> SocBand.CAUTION
        else -> SocBand.NORMAL
    }
}
