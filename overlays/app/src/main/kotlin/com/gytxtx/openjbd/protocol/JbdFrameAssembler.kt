package com.gytxtx.openjbd.protocol

import java.io.ByteArrayOutputStream

/**
 * Reassembles JBD frames from BLE notification chunks.
 *
 * Product overlay of the upstream assembler (issue #30):
 * - A candidate that fails to parse (bad end marker, checksum or a corrupted length byte) only
 *   advances the cursor by **one byte** and rescans for the next `0xDD`. Upstream skipped the
 *   whole declared length, so a noise `0xDD` or a corrupted length could swallow the valid frame
 *   that followed it.
 * - A start byte whose declared length runs past the buffered data is skipped only when a
 *   complete, valid frame already follows it, so a noise `0xDD` cannot stall later responses.
 * - [append] and [reset] are synchronized: notifications are assembled on a binder thread while
 *   the connection manager resets on the main thread.
 */
class JbdFrameAssembler {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun reset() {
        buffer.reset()
    }

    @Synchronized
    fun append(chunk: ByteArray): List<JbdFrame> {
        buffer.write(chunk, 0, chunk.size)
        val frames = mutableListOf<JbdFrame>()
        val data = buffer.toByteArray()
        var cursor = 0
        while (cursor < data.size) {
            while (cursor < data.size && (data[cursor].toInt() and 0xFF) != START_BYTE) {
                cursor++
            }
            if (data.size - cursor < HEADER_SIZE) {
                break
            }
            val length = data[cursor + 3].toInt() and 0xFF
            val frameLength = length + FRAME_OVERHEAD
            if (data.size - cursor < frameLength) {
                // Either a frame still in flight or a noise 0xDD with a bogus length. Only skip
                // it when a complete, checksum-valid frame already follows; otherwise wait.
                val next = nextValidFrameStart(data, cursor + 1)
                if (next < 0) break
                cursor = next
                continue
            }
            val rawFrame = data.copyOfRange(cursor, cursor + frameLength)
            try {
                frames.add(JbdFrame.parse(rawFrame))
                cursor += frameLength
            } catch (_: JbdParseException) {
                // This 0xDD was not a real frame start: resynchronize byte by byte.
                cursor += 1
            }
        }
        buffer.reset()
        if (cursor < data.size) {
            buffer.write(data, cursor, data.size - cursor)
        }
        return frames
    }

    private fun nextValidFrameStart(data: ByteArray, from: Int): Int {
        for (start in from until data.size) {
            if ((data[start].toInt() and 0xFF) != START_BYTE) continue
            if (data.size - start < HEADER_SIZE) return -1
            val frameLength = (data[start + 3].toInt() and 0xFF) + FRAME_OVERHEAD
            if (data.size - start < frameLength) continue
            try {
                JbdFrame.parse(data.copyOfRange(start, start + frameLength))
                return start
            } catch (_: JbdParseException) {
                // Not a frame start either; keep scanning.
            }
        }
        return -1
    }

    private companion object {
        const val START_BYTE = 0xDD
        const val HEADER_SIZE = 4
        const val FRAME_OVERHEAD = 7
    }
}
