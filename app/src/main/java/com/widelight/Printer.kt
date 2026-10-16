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
        check(data.size >= 11 && data[0] == 0.toByte()) { "Invalid image capabilities" }
        val b = ByteBuffer.wrap(data)
        b.get()
        val w = b.short.toInt() and 65535
        val h = b.short.toInt() and 65535
        val formats = b.get().toInt()
        b.get()
        val max = b.int
        check(w == 1260 && h in 800..840 && formats and 2 != 0 && max in 10000..10000000) { "This printer is not a supported original Link WIDE" }
        return Capabilities(w, h, max)
    }
    fun status(): String {
        val d = command(2, byteArrayOf(2))
        check(d.size >= 9 && d[0] == 2.toByte()) { "Invalid printer status" }
        val film = d[1].toInt() and 15
        val flags = d[2].toInt() and 255
        check(flags and 1 == 0) { "Close the film door" }
        check(flags and 6 == 0) { "Printer is busy or has an error" }
        check(film > 0) { "Load film before printing" }
        return "$film sheets remaining"
    }
    fun print(jpeg: ByteArray, progress: (Float) -> Unit) {
        status()
        val start = command(0x1000, ByteBuffer.allocate(8).put(2).put(0).putShort(0).putInt(jpeg.size).array())
        check(start.size == 4) { "Invalid transfer response" }
        val frameSize = ByteBuffer.wrap(start).int
        check(frameSize in 1..65524) { "Invalid transfer frame size" }
        val count = (jpeg.size + frameSize - 1) / frameSize
        repeat(count) { index ->
            val frame = ByteArray(frameSize)
            val from = index * frameSize
            jpeg.copyInto(frame, 0, from, minOf(from + frameSize, jpeg.size))
            val ack = command(0x1001, ByteBuffer.allocate(frameSize + 4).putInt(index).put(frame).array())
            check(ack.size == 4 && ByteBuffer.wrap(ack).int == index) { "Unexpected frame acknowledgement" }
            progress((index + 1f) / count)
        }
        command(0x1002)
        // Never retry this command automatically: a lost reply may still consume film.
        command(0x1080)
    }
    override fun close() { runCatching { socket.close() }; timer.shutdownNow() }
}
