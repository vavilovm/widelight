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
                ensureState(count > 0, R.string.printer_disconnected)
                offset += count
            }
            return bytes
        }
        val header = read(4)
        ensureState(header[0] == 0x61.toByte() && header[1] == 0x42.toByte(), R.string.invalid_printer_reply)
        val size = ByteBuffer.wrap(header, 2, 2).short.toInt() and 65535
        ensureState(size in 8..65535, R.string.invalid_reply_length)
        val packet = header + read(size - 4)
        ensureState(packet.sumOf { it.toInt() and 255 } and 255 == 255, R.string.printer_reply_checksum_failed)
        ensureState(ByteBuffer.wrap(packet, 4, 2).short.toInt() and 65535 == command, R.string.unexpected_printer_reply)
        val result = packet[6].toInt() and 255
        if (result != 0) throw UserStateFailure(when (result) {
            1, 194 -> R.string.printer_is_busy_wait_before_trying_again
            144 -> R.string.printer_battery_is_too_low
            176 -> R.string.close_the_film_door
            177 -> R.string.printer_is_out_of_film
            else -> R.string.printer_error
        }, result.toString(16))
        return packet.copyOfRange(7, packet.lastIndex)
    }
}
