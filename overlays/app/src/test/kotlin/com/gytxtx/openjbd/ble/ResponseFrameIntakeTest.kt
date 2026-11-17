package com.gytxtx.openjbd.ble

import com.gytxtx.openjbd.protocol.JbdCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseFrameIntakeTest {
    private var now = 1_000L
    private val intake = ResponseFrameIntake({ now })

    @Test
    fun frameAssembledBeforeResetIsNotCurrentAfterReset() {
        // Binder thread assembled a frame of the old connection; main thread then reset for a
        // new connection before processing it. It must not complete a new-connection command.
        val batch = intake.append(frame(0x24, byteArrayOf(0x10, 0x68)))
        assertEquals(1, batch.frames.size)
        assertTrue(intake.isCurrent(batch))

        intake.reset()

        assertFalse(intake.isCurrent(batch))
        assertTrue(intake.isCurrent(intake.append(frame(0x24, byteArrayOf(0x10, 0x68)))))
    }

    @Test
    fun resetDropsHalfAssembledFrameOfPreviousConnection() {
        val old = frame(0x03, byteArrayOf(1, 2, 3, 4))
        intake.append(old.copyOfRange(0, 6))

        intake.reset()

        assertTrue(intake.append(old.copyOfRange(6, old.size)).frames.isEmpty())
    }

    @Test
    fun chunkFromClosedLinkIsRejected() {
        val batch = intake.append(frame(0x24, byteArrayOf(0x10, 0x68))) { false }

        assertTrue(batch.frames.isEmpty())
    }

    @Test
    fun quarantineDiscardsLateFramesAndInvalidatesInFlightBatches() {
        val inFlight = intake.append(frame(0x24, byteArrayOf(0x10, 0x68)))

        intake.quarantine(300)

        assertFalse(intake.isCurrent(inFlight))
        assertEquals(300L, intake.quarantineRemainingMs())
        now += 299
        assertTrue(intake.append(frame(0x24, byteArrayOf(0x10, 0x68))).frames.isEmpty())
        assertEquals(1L, intake.quarantineRemainingMs())

        now += 1
        assertEquals(0L, intake.quarantineRemainingMs())
        val fresh = intake.append(frame(0x24, byteArrayOf()))
        assertEquals(1, fresh.frames.size)
        assertTrue(intake.isCurrent(fresh))
    }

    @Test
    fun lateFrameSplitAcrossQuarantineEndIsNotReassembled() {
        val late = frame(0x24, byteArrayOf(0x10, 0x68))
        intake.quarantine(300)
        intake.append(late.copyOfRange(0, 4))

        now += 300

        assertTrue(intake.append(late.copyOfRange(4, late.size)).frames.isEmpty())
    }

    @Test
    fun resetClearsQuarantine() {
        intake.quarantine(300)

        intake.reset()

        assertEquals(0L, intake.quarantineRemainingMs())
        assertEquals(1, intake.append(frame(0x01, byteArrayOf())).frames.size)
    }

    private fun frame(command: Int, payload: ByteArray): ByteArray {
        val checksum = JbdCommands.checksum(0, payload.size, payload)
        return byteArrayOf(0xDD.toByte(), command.toByte(), 0, payload.size.toByte()) +
            payload +
            byteArrayOf((checksum shr 8).toByte(), checksum.toByte(), 0x77)
    }
}
