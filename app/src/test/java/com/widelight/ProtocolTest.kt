package com.widelight

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolTest {
    @Test fun commandHasExpectedWireBytes() {
        assertArrayEquals(byteArrayOf(0x41, 0x62, 0, 8, 0, 2, 2, 0x50), Protocol.packet(2, byteArrayOf(2)))
    }
    private fun reply(command: Int = 2, result: Int = 0): ByteArray {
        val b = byteArrayOf(0x61, 0x42, 0, 9, (command shr 8).toByte(), command.toByte(), result.toByte(), 7, 0)
        b[8] = (255 - b.dropLast(1).sumOf { it.toInt() and 255 }).toByte()
        return b
    }
    @Test fun fragmentedReplyIsReadFully() {
        val stream = object : ByteArrayInputStream(reply()) { override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1)) }
        assertArrayEquals(byteArrayOf(7), Protocol.response(stream, 2))
    }
    @Test fun rejectsCorruptionWrongCommandErrorsAndTruncation() {
        listOf(reply().also { it[8] = 0 }, reply(3), reply(result = 177), reply().dropLast(1).toByteArray()).forEach {
            assertThrows(IllegalStateException::class.java) { Protocol.response(ByteArrayInputStream(it), 2) }
        }
    }
}
