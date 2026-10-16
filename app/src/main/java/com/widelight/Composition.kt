package com.widelight

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Normalized rectangles keep the preview and full-resolution print in agreement. */
data class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)
enum class Layout(val label: String) { AUTO("Grid"), COLUMNS("Columns"), ROWS("Rows"), HERO("Spotlight") }
enum class Frame(val label: String, val color: Int) {
    NONE("None", -1), WHITE("White", -1), INK("Ink", 0xFF202827.toInt()),
    CREAM("Cream", 0xFFF4E9D3.toInt()), TEAL("Teal", 0xFF236C61.toInt())
}
enum class Look(val label: String) { NATURAL("Natural"), POP("Pop"), MONO("B&W"), WARM("Warm") }
data class Slot(val photo: Photo, val turns: Int = photo.turns, val fill: Boolean = true,
                val zoom: Float = 1f, val x: Float = .5f, val y: Float = .5f)
data class Composition(
    val slots: List<Slot>, val layout: Layout = Layout.AUTO, val frame: Frame = Frame.WHITE,
    val border: Float = .025f, val gap: Float = .012f, val caption: String = "",
    val qr: String = "", val look: Look = Look.NATURAL,
) {
    fun validate() {
        require(slots.size in 1..6) { "Choose 1 to 6 photos" }
        require(border in 0f..0.08f && gap in 0f..0.04f) { "Invalid spacing" }
        require(caption.length <= 80) { "Keep the caption under 80 characters" }
        require(qr.toByteArray(Charsets.UTF_8).size <= 160) { "Use a shorter QR link or message (160 bytes maximum)" }
        slots.forEach { require(it.zoom in 1f..3f && it.x in 0f..1f && it.y in 0f..1f) }
    }
}
object CollageLayout {
    fun cells(count: Int, layout: Layout): List<Cell> {
        require(count in 1..6)
        if (count == 1) return listOf(Cell(0f, 0f, 1f, 1f))
        fun row(n: Int, y: Float, h: Float) = (0 until n).map { i -> Cell(i.toFloat()/n, y, (i+1f)/n, y+h) }
        return when (layout) {
            Layout.COLUMNS -> (0 until count).map { Cell(it.toFloat()/count, 0f, (it+1f)/count, 1f) }
            Layout.ROWS -> (0 until count).map { Cell(0f, it.toFloat()/count, 1f, (it+1f)/count) }
            Layout.HERO -> listOf(Cell(0f, 0f, .6f, 1f)) + (0 until count-1).map {
                Cell(.6f, it.toFloat()/(count-1), 1f, (it+1f)/(count-1))
            }
            Layout.AUTO -> when (count) {
                2 -> cells(count, Layout.COLUMNS)
                3 -> cells(count, Layout.HERO)
                4 -> row(2, 0f, .5f) + row(2, .5f, .5f)
                5 -> row(3, 0f, .5f) + row(2, .5f, .5f)
                else -> row(3, 0f, .5f) + row(3, .5f, .5f)
            }
        }
    }
}
object QrCode {
    fun matrix(text: String) = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
}
