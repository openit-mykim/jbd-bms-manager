package com.gytxtx.openjbd.protocol

/** Frame builders for candidate JBD EEPROM/configuration-register operations. */
object JbdConfigCommands {
    @JvmStatic
    fun readRegister(address: Int): ByteArray {
        require(address in 0x00..0xFF) { "Register address must fit in one byte" }
        return JbdCommands.readCommand(address.toByte())
    }

    @JvmStatic
    fun writeRegister(address: Int, data: ByteArray): ByteArray {
        require(address in 0x00..0xFF) { "Register address must fit in one byte" }
        require(data.size in 1..0xFF) { "Register write payload must contain 1..255 bytes" }
        return JbdCommands.writeCommand(address.toByte(), data)
    }

    /** Exits factory mode while committing EEPROM changes (`0x01 <- 0x28 0x28`). */
    @JvmStatic
    fun exitFactoryModeWithCommit(): ByteArray =
        JbdCommands.writeCommand(JbdCommands.CMD_CLOSE_FACTORY_MODE, byteArrayOf(0x28, 0x28))
}
