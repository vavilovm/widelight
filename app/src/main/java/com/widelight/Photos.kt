package com.widelight

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

class Photo(val id: String, val file: File, val title: String, val favorite: Boolean = false, val turns: Int = 0, val fill: Boolean = false) {
    fun edited(favorite: Boolean = this.favorite, turns: Int = this.turns, fill: Boolean = this.fill) = Photo(id, file, title, favorite, turns, fill)
}
object Photos {
    const val MAX_IMPORT = 30 * 1024 * 1024
    fun import(context: Context, uri: Uri): Photo {
        val id = java.util.UUID.randomUUID().toString()
        val file = File(context.filesDir, "$id.photo")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open photo" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        check(total <= MAX_IMPORT) { "Photo exceeds 30 MB" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            val photo = Photo(id, file, "Photo")
            decode(photo).recycle()
            return photo
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun decode(photo: Photo): Bitmap {
        require(photo.file.length() in 1..MAX_IMPORT.toLong()) { "Photo is empty or exceeds 30 MB" }
        val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(photo.file.readBytes()))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, 2000.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
        if (photo.turns % 4 == 0) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(photo.turns * 90f) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
    fun render(photo: Photo, width: Int, height: Int): Bitmap {
        val source = decode(photo)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val scale = if (photo.fill) maxOf(width.toFloat() / source.width, height.toFloat() / source.height) else minOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        canvas.drawBitmap(source, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return result
    }
    fun jpeg(photo: Photo, caps: Printer.Capabilities): ByteArray {
        val bitmap = render(photo, caps.width, caps.height)
        try {
            for (quality in 95 downTo 35 step 5) {
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                if (bytes.size <= caps.maxBytes) return bytes
            }
            error("Photo cannot fit the printer's transfer limit")
        } finally { bitmap.recycle() }
    }
}
