package com.gytxtx.openjbd.settings

/** Live basic-info state needed to build a full 0xE1 command. */
data class MosLiveState(val fetState: Int, val protectionActive: Boolean)

sealed interface MosCommand {
    data class Write(val raw: Int) : MosCommand
    data class Rejected(val reason: String) : MosCommand
}

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

    const val REASON_LIVE_STATE_UNAVAILABLE = "mos live FET state unavailable"
    const val REASON_OTHER_FET_PROTECTED = "mos other FET is off while protection is active"
    const val REASON_MIXED_SESSION = "mos commands cannot share a session with settings"

    /**
     * Builds the full 0xE1 value (bit 0 = charge disable, bit 1 = discharge disable). Staged bits
     * win; an unstaged bit keeps the FET's live state. If an unstaged FET is off while a
     * protection is active, its off-state may be the BMS's own protection rather than a user
     * disable; writing "disable" would latch it after the protection clears, so refuse.
     */
    fun commandRaw(stagedDisableBits: Map<Int, Int>, live: MosLiveState?): MosCommand {
        if (live == null) return MosCommand.Rejected(REASON_LIVE_STATE_UNAVAILABLE)
        var raw = 0
        for (bit in 0..1) {
            val disable = stagedDisableBits[bit] ?: when {
                isConducting(live.fetState, bit) -> 0
                live.protectionActive -> return MosCommand.Rejected(REASON_OTHER_FET_PROTECTED)
                else -> 1
            }
            require(disable in 0..1) { "MOS disable value must be 0 or 1" }
            if (disable == 1) raw = raw or (1 shl bit)
        }
        return MosCommand.Write(raw)
    }
}
