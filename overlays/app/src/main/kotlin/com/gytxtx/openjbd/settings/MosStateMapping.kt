package com.gytxtx.openjbd.settings

/** Mapping between register 0xE1 disable bits and basic-info FET conducting bits. */
object MosStateMapping {
    fun isDisabled(disableRaw: Int): Boolean {
        require(disableRaw in 0..1) { "MOS disable value must be 0 or 1" }
        return disableRaw == 1
    }

    fun disableRawForAllowed(allowed: Boolean): Int = if (allowed) 0 else 1

    fun isConducting(fetState: Int, bitIndex: Int): Boolean {
        require(bitIndex in 0..15) { "FET state bit index must be in 0..15" }
        return fetState and (1 shl bitIndex) != 0
    }
}
