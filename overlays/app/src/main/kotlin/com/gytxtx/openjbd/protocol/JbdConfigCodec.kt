package com.gytxtx.openjbd.protocol

import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface RegisterResponseResult {
    data class Ok(val raw: ByteArray) : RegisterResponseResult
    data class ErrorStatus(val status: Int) : RegisterResponseResult
    data class Malformed(val reason: String) : RegisterResponseResult
}

data class ScaledRegisterValue(
    val raw: Int,
    val physicalValue: Double,
    val unit: String
)

/** Big-endian U16/S16 codec with explicit physical-unit metadata. */
data class ScaledInt16Codec(
    val signed: Boolean,
    val scale: Double,
    val unit: String
) {
    init {
        require(scale > 0.0 && scale.isFinite()) { "Scale must be finite and positive" }
        require(unit.isNotBlank()) { "Unit must not be blank" }
    }

    val rawRange: IntRange = if (signed) Short.MIN_VALUE..Short.MAX_VALUE else 0..0xFFFF

    fun decode(data: ByteArray): ScaledRegisterValue {
        require(data.size == 2) { "A 16-bit register must contain exactly 2 bytes" }
        val unsigned = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val raw = if (signed && unsigned >= 0x8000) unsigned - 0x10000 else unsigned
        return ScaledRegisterValue(raw, raw * scale, unit)
    }

    fun encodeRaw(raw: Int): ByteArray {
        require(raw in rawRange) { "Raw value $raw is outside $rawRange" }
        val encoded = raw and 0xFFFF
        return byteArrayOf((encoded ushr 8).toByte(), encoded.toByte())
    }

    fun encodePhysical(physicalValue: Double): ByteArray {
        require(physicalValue.isFinite()) { "Physical value must be finite" }
        val unrounded = physicalValue / scale
        val raw = unrounded.roundToInt()
        require(abs(unrounded - raw) < 1e-8) {
            "Physical value must align to the codec scale"
        }
        return encodeRaw(raw)
    }
}

sealed interface ErrorCountersResult {
    data class Ok(val counters: IntArray) : ErrorCountersResult
    data class ErrorStatus(val status: Int) : ErrorCountersResult
    data class Malformed(val reason: String) : ErrorCountersResult
}

object JbdConfigCodec {
    const val ERROR_COUNTERS_ADDRESS: Int = 0xAA

    @JvmStatic
    fun decodeRegisterResponse(
        frame: JbdFrame,
        expectedAddress: Int
    ): RegisterResponseResult {
        if (expectedAddress !in 0x00..0xFF) {
            return RegisterResponseResult.Malformed("Expected address must fit in one byte")
        }
        if (frame.command != expectedAddress) {
            return RegisterResponseResult.Malformed(
                "Address echo mismatch: expected 0x${expectedAddress.toString(16)}, " +
                    "received 0x${frame.command.toString(16)}"
            )
        }
        if (frame.status != 0) {
            return RegisterResponseResult.ErrorStatus(frame.status)
        }
        if (frame.payload.size != 2) {
            return RegisterResponseResult.Malformed(
                "Expected a 2-byte register payload, received ${frame.payload.size}"
            )
        }
        return RegisterResponseResult.Ok(frame.payload.copyOf())
    }

    @JvmStatic
    fun u16(scale: Double, unit: String): ScaledInt16Codec =
        ScaledInt16Codec(signed = false, scale = scale, unit = unit)

    @JvmStatic
    fun s16(scale: Double, unit: String): ScaledInt16Codec =
        ScaledInt16Codec(signed = true, scale = scale, unit = unit)

    @JvmStatic
    fun getU16Bit(value: Int, bitIndex: Int): Boolean {
        require(value in 0..0xFFFF) { "Value must be an unsigned 16-bit integer" }
        require(bitIndex in 0..15) { "Bit index must be in 0..15" }
        return value and (1 shl bitIndex) != 0
    }

    @JvmStatic
    fun setU16Bit(value: Int, bitIndex: Int, enabled: Boolean): Int {
        require(value in 0..0xFFFF) { "Value must be an unsigned 16-bit integer" }
        require(bitIndex in 0..15) { "Bit index must be in 0..15" }
        val mask = 1 shl bitIndex
        return if (enabled) value or mask else value and mask.inv() and 0xFFFF
    }

    @JvmStatic
    fun decodeErrorCounters(frame: JbdFrame): ErrorCountersResult {
        if (frame.command != ERROR_COUNTERS_ADDRESS) {
            return ErrorCountersResult.Malformed(
                "Address echo mismatch for error counters: 0x${frame.command.toString(16)}"
            )
        }
        if (frame.status != 0) {
            return ErrorCountersResult.ErrorStatus(frame.status)
        }
        if (frame.payload.size != 22) {
            return ErrorCountersResult.Malformed(
                "Error counters require 22 payload bytes, received ${frame.payload.size}"
            )
        }
        return ErrorCountersResult.Ok(
            IntArray(11) { index ->
                val offset = index * 2
                ((frame.payload[offset].toInt() and 0xFF) shl 8) or
                    (frame.payload[offset + 1].toInt() and 0xFF)
            }
        )
    }
}
