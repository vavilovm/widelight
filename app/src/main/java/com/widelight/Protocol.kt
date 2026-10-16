package com.widelight

import java.io.InputStream
import java.nio.ByteBuffer

/** Link packet framing. Commands are serialized; a reply must match its request. */
object Protocol {
    fun packet(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(payload.size <= 65528)
        val b = ByteBuffer.allocate(payload.size + 7)
            .put(0x41).put(0x62).putShort((payload.size + 7).toShort())
            .putShort(command.toShort()).put(payload).array()
        b[b.lastIndex] = (255 - b.dropLast(1).sumOf { it.toInt() and 255 }).toByte()
        return b
    }
    fun response(input: InputStream, command: Int): ByteArray {
        fun read(n: Int): ByteArray {
            val bytes = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val count = input.read(bytes, offset, n - offset)
                check(count > 0) { "Printer disconnected" }
                offset += count
            }
            return bytes
        }
        val header = read(4)
        check(header[0] == 0x61.toByte() && header[1] == 0x42.toByte()) { "Invalid printer reply" }
        val size = ByteBuffer.wrap(header, 2, 2).short.toInt() and 65535
        check(size in 8..65535) { "Invalid reply length" }
        val packet = header + read(size - 4)
        check(packet.sumOf { it.toInt() and 255 } and 255 == 255) { "Printer reply checksum failed" }
        check(ByteBuffer.wrap(packet, 4, 2).short.toInt() and 65535 == command) { "Unexpected printer reply" }
        val result = packet[6].toInt() and 255
        check(result == 0) { when (result) {
            1, 194 -> "Printer is busy. Wait before trying again."
            144 -> "Printer battery is too low."
            176 -> "Close the film door."
            177 -> "Printer is out of film."
            else -> "Printer returned error 0x${result.toString(16)}"
        } }
        return packet.copyOfRange(7, packet.lastIndex)
    }
}
