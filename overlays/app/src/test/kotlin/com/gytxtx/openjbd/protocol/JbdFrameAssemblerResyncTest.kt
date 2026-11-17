package com.gytxtx.openjbd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #30: byte-by-byte resynchronization of the product overlay assembler. */
class JbdFrameAssemblerResyncTest {
    @Test
    fun corruptedFrameSkipsOnlyOneByteSoTheNextFrameSurvives() {
        // A noise 0xDD whose "length" byte (0x00) makes a 7-byte candidate that swallows the
        // start of the real frame. Upstream skipped all 7 bytes and lost the real frame.
        val real = frame(0x24, 0, byteArrayOf(0x10, 0x68))
        val data = byteArrayOf(0xDD.toByte(), 0x24, 0x00, 0x00) + real

        val frames = JbdFrameAssembler().append(data)

        assertEquals(1, frames.size)
        assertEquals(0x24, frames[0].command)
        assertEquals(2, frames[0].payload.size)
    }

    @Test
    fun badChecksumFrameIsDroppedAndFollowingFrameParsed() {
        val broken = frame(0x03, 0, byteArrayOf(0x01, 0x02)).also { it[it.size - 2] = (it[it.size - 2] + 1).toByte() }
        val good = frame(0x04, 0, byteArrayOf(0x0C, 0xE4.toByte()))

        val frames = JbdFrameAssembler().append(broken + good)

        assertEquals(listOf(0x04), frames.map { it.command })
    }

    @Test
    fun noiseStartByteWithHugeLengthDoesNotStallACompleteFollowingFrame() {
        val good = frame(0x01, 0, byteArrayOf())
        // 0xDD with declared length 0xFF would wait for 262 bytes; a complete valid frame follows.
        val data = byteArrayOf(0xDD.toByte(), 0x77, 0x00, 0xFF.toByte()) + good

        val frames = JbdFrameAssembler().append(data)

        assertEquals(listOf(0x01), frames.map { it.command })
    }

    @Test
    fun genuineFrameStillInFlightIsKeptUntilComplete() {
        val assembler = JbdFrameAssembler()
        val good = frame(0x03, 0, ByteArray(30) { it.toByte() })

        assertTrue(assembler.append(good.copyOfRange(0, 10)).isEmpty())
        assertTrue(assembler.append(good.copyOfRange(10, 20)).isEmpty())
        val frames = assembler.append(good.copyOfRange(20, good.size))

        assertEquals(1, frames.size)
        assertEquals(30, frames[0].payload.size)
    }

    @Test
    fun resetDropsPartialFrame() {
        val assembler = JbdFrameAssembler()
        val good = frame(0x03, 0, byteArrayOf(1, 2, 3))

        assembler.append(good.copyOfRange(0, 5))
        assembler.reset()

        assertTrue(assembler.append(good.copyOfRange(5, good.size)).isEmpty())
        assertEquals(1, assembler.append(good).size)
    }

    companion object {
        fun frame(command: Int, status: Int, payload: ByteArray): ByteArray {
            val checksum = JbdCommands.checksum(status.toByte(), payload.size, payload)
            return byteArrayOf(0xDD.toByte(), command.toByte(), status.toByte(), payload.size.toByte()) +
                payload +
                byteArrayOf((checksum shr 8).toByte(), checksum.toByte(), 0x77)
        }
    }
}
