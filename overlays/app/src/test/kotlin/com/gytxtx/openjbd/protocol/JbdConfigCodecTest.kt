package com.gytxtx.openjbd.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JbdConfigCodecTest {
    @Test
    fun registerResponseReturnsDefensiveRawCopy() {
        val frame = response(0x24, 0x00, byteArrayOf(0x10, 0x68))
        val decoded = JbdConfigCodec.decodeRegisterResponse(frame, 0x24)

        assertTrue(decoded is RegisterResponseResult.Ok)
        decoded as RegisterResponseResult.Ok
        assertArrayEquals(byteArrayOf(0x10, 0x68), decoded.raw)
        assertFalse(decoded.raw === frame.payload)
    }

    @Test
    fun registerResponseRejectsAddressMismatch() {
        val decoded = JbdConfigCodec.decodeRegisterResponse(
            response(0x25, 0x00, byteArrayOf(0x10, 0x68)),
            0x24
        )

        assertTrue(decoded is RegisterResponseResult.Malformed)
    }

    @Test
    fun errorStatusNeverDecodesAsRegisterData() {
        val decoded = JbdConfigCodec.decodeRegisterResponse(
            response(0x24, 0x80, byteArrayOf()),
            0x24
        )

        assertEquals(RegisterResponseResult.ErrorStatus(0x80), decoded)
    }

    @Test
    fun registerResponseRejectsNonU16Length() {
        assertTrue(
            JbdConfigCodec.decodeRegisterResponse(
                response(0x24, 0x00, byteArrayOf(0x10)),
                0x24
            ) is RegisterResponseResult.Malformed
        )
    }

    @Test
    fun unsignedScaledCodecRoundTripsRawAndPhysicalValue() {
        val codec = JbdConfigCodec.u16(10.0, "mV")

        val bytes = codec.encodePhysical(42_000.0)
        assertArrayEquals(byteArrayOf(0x10, 0x68), bytes)
        assertEquals(4_200, codec.decode(bytes).raw)
        assertEquals(42_000.0, codec.decode(bytes).physicalValue, 0.0)
        assertEquals("mV", codec.unit)
    }

    @Test
    fun signedScaledCodecRoundTripsNegativeValue() {
        val codec = JbdConfigCodec.s16(10.0, "mA")

        val bytes = codec.encodeRaw(-2_500)
        assertArrayEquals(byteArrayOf(0xF6.toByte(), 0x3C), bytes)
        assertEquals(-2_500, codec.decode(bytes).raw)
        assertEquals(-25_000.0, codec.decode(bytes).physicalValue, 0.0)
    }

    @Test
    fun bitSetAndClearPreserveEveryOtherBit() {
        val original = 0b1010_0010_0101_0001
        val set = JbdConfigCodec.setU16Bit(original, 3, true)
        val cleared = JbdConfigCodec.setU16Bit(set, 6, false)

        assertEquals(original or (1 shl 3), set)
        assertEquals(set and (1 shl 6).inv() and 0xFFFF, cleared)
        assertTrue(JbdConfigCodec.getU16Bit(set, 3))
        assertFalse(JbdConfigCodec.getU16Bit(cleared, 6))
    }

    @Test
    fun errorCountersDecodeElevenBigEndianValues() {
        val payload = ByteArray(22)
        for (index in 0 until 11) {
            payload[index * 2] = (index ushr 8).toByte()
            payload[index * 2 + 1] = index.toByte()
        }

        val decoded = JbdConfigCodec.decodeErrorCounters(response(0xAA, 0x00, payload))

        assertTrue(decoded is ErrorCountersResult.Ok)
        assertArrayEquals(IntArray(11) { it }, (decoded as ErrorCountersResult.Ok).counters)
    }

    @Test
    fun errorCountersRejectWrongLengthAndStatus() {
        assertTrue(
            JbdConfigCodec.decodeErrorCounters(
                response(0xAA, 0x00, ByteArray(20))
            ) is ErrorCountersResult.Malformed
        )
        assertEquals(
            ErrorCountersResult.ErrorStatus(0x80),
            JbdConfigCodec.decodeErrorCounters(response(0xAA, 0x80, byteArrayOf()))
        )
    }

    private fun response(address: Int, status: Int, payload: ByteArray): JbdFrame {
        val length = payload.size
        val checksum = (-(status + length + payload.sumOf { it.toInt() and 0xFF })) and 0xFFFF
        return JbdFrame.parse(
            byteArrayOf(0xDD.toByte(), address.toByte(), status.toByte(), length.toByte()) +
                payload +
                byteArrayOf((checksum ushr 8).toByte(), checksum.toByte(), 0x77)
        )
    }
}
