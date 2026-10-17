package com.widelight

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Original Link WIDE Android endpoint uses Bluetooth Classic SPP. */
@SuppressLint("MissingPermission")
class Printer(device: BluetoothDevice) : Closeable {
    private val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
    private val timer = Executors.newSingleThreadScheduledExecutor()
    fun connect() = guarded { socket.connect() }
    private fun <T> guarded(block: () -> T): T {
        val timeout = timer.schedule({ runCatching { socket.close() } }, 20, TimeUnit.SECONDS)
        return try { block() } finally { timeout.cancel(false) }
    }
    private fun command(id: Int, bytes: ByteArray = byteArrayOf()): ByteArray = guarded {
        socket.outputStream.write(Protocol.packet(id, bytes))
        socket.outputStream.flush()
        Protocol.response(socket.inputStream, id)
    }
    data class Capabilities(val width: Int, val height: Int, val maxBytes: Int)
    fun capabilities(): Capabilities {
        command(0)
        val data = command(2, byteArrayOf(0))
        ensureState(data.size >= 11 && data[0] == 0.toByte(), R.string.invalid_image_capabilities)
        val b = ByteBuffer.wrap(data)
        b.get()
        val w = b.short.toInt() and 65535
        val h = b.short.toInt() and 65535
        val formats = b.get().toInt()
        b.get()
        val max = b.int
        ensureState(w == 1260 && h in 800..840 && formats and 2 != 0 && max in 10000..10000000, R.string.this_printer_is_not_a_supported_original)
        return Capabilities(w, h, max)
    }
    fun status(): Int {
        val d = command(2, byteArrayOf(2))
        ensureState(d.size >= 9 && d[0] == 2.toByte(), R.string.invalid_printer_status)
        val film = d[1].toInt() and 15
        val flags = d[2].toInt() and 255
        ensureState(flags and 1 == 0, R.string.close_the_film_door_text)
        ensureState(flags and 6 == 0, R.string.printer_is_busy_or_has_an_error)
        ensureState(film > 0, R.string.load_film_before_printing)
        return film
    }
    fun print(jpeg: ByteArray, progress: (Float) -> Unit) {
        status()
        val start = command(0x1000, ByteBuffer.allocate(8).put(2).put(0).putShort(0).putInt(jpeg.size).array())
        ensureState(start.size == 4, R.string.invalid_transfer_response)
        val frameSize = ByteBuffer.wrap(start).int
        ensureState(frameSize in 1..65524, R.string.invalid_transfer_frame_size)
        val count = (jpeg.size + frameSize - 1) / frameSize
        repeat(count) { index ->
            val frame = ByteArray(frameSize)
            val from = index * frameSize
            jpeg.copyInto(frame, 0, from, minOf(from + frameSize, jpeg.size))
            val ack = command(0x1001, ByteBuffer.allocate(frameSize + 4).putInt(index).put(frame).array())
            ensureState(ack.size == 4 && ByteBuffer.wrap(ack).int == index, R.string.unexpected_frame_acknowledgement)
            progress((index + 1f) / count)
        }
        command(0x1002)
        // Never retry this command automatically: a lost reply may still consume film.
        command(0x1080)
    }
    override fun close() { runCatching { socket.close() }; timer.shutdownNow() }
}
