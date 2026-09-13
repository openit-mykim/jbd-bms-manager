package com.gytxtx.openjbd.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JbdConfigCommandsTest {
    @Test
    fun readRegisterBuildsExactCommunityVector() {
        assertArrayEquals(
            hex("DD A5 24 00 FF DC 77"),
            JbdConfigCommands.readRegister(0x24)
        )
    }

    @Test
    fun writeRegisterBuildsExactCommunityVector() {
        assertArrayEquals(
            hex("DD 5A 18 02 0C 1C FF BE 77"),
            JbdConfigCommands.writeRegister(0x18, hex("0C 1C"))
        )
    }

    @Test
    fun commitExitBuildsExactCommunityVector() {
        assertArrayEquals(
            hex("DD 5A 01 02 28 28 FF AD 77"),
            JbdConfigCommands.exitFactoryModeWithCommit()
        )
    }

    @Test
    fun secondIndependentChecksumVectorIsExact() {
        assertArrayEquals(
            hex("DD 5A 2A 02 0C E4 FE E4 77"),
            JbdConfigCommands.writeRegister(0x2A, hex("0C E4"))
        )
    }

    @Test
    fun writePayloadLengthFailsFastOutsideOneByteRange() {
        assertThrows(IllegalArgumentException::class.java) {
            JbdConfigCommands.writeRegister(0x24, byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            JbdConfigCommands.writeRegister(0x24, ByteArray(256))
        }
    }

    private fun hex(value: String): ByteArray = value.split(' ')
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
