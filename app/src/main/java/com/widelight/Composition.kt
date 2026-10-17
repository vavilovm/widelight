package com.widelight

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Normalized rectangles keep the preview and full-resolution print in agreement. */
data class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)
enum class Layout(val label: Int) { AUTO(R.string.grid), COLUMNS(R.string.columns), ROWS(R.string.rows), HERO(R.string.spotlight) }
enum class Frame(val label: Int, val color: Int) {
    NONE(R.string.none, -1), WHITE(R.string.white, -1), INK(R.string.ink, 0xFF202827.toInt()),
    CREAM(R.string.cream, 0xFFF4E9D3.toInt()), TEAL(R.string.teal, 0xFF236C61.toInt())
}
enum class Look(val label: Int) { NATURAL(R.string.natural), POP(R.string.pop), MONO(R.string.b_w), WARM(R.string.warm) }
data class Slot(val photo: Photo, val turns: Int = photo.turns, val fill: Boolean = true,
                val zoom: Float = 1.03f, val x: Float = .5f, val y: Float = .5f)
data class Composition(
    val slots: List<Slot>, val layout: Layout = Layout.AUTO, val frame: Frame = Frame.WHITE,
    val border: Float = .025f, val gap: Float = .012f, val caption: String = "",
    val qr: String = "", val look: Look = Look.NATURAL,
) {
    fun validate() {
        ensureInput(slots.size in 1..6, R.string.choose_1_to_6_photos)
        ensureInput(border in 0f..0.08f && gap in 0f..0.04f, R.string.invalid_spacing)
        ensureInput(caption.length <= 80, R.string.keep_the_caption_under_80_characters)
        ensureInput(qr.toByteArray(Charsets.UTF_8).size <= 160, R.string.use_a_shorter_qr_link_or_message)
        slots.forEach { ensureInput(it.zoom in 1f..3f && it.x in 0f..1f && it.y in 0f..1f, R.string.invalid_crop) }
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

/** Keep the point under the fingers stationary while zooming, then apply the drag. */
fun transformCrop(slot: Slot, sourceWidth: Int, sourceHeight: Int, width: Float, height: Float,
                  focusX: Float, focusY: Float, panX: Float, panY: Float, zoomChange: Float): Slot {
    val base = maxOf(width/sourceWidth, height/sourceHeight)
    val oldZoom = if (slot.fill) slot.zoom else 1f
    val zoom = (oldZoom*zoomChange).coerceIn(1f,3f)
    fun position(size: Float, sourceSize: Int, position: Float, focus: Float, pan: Float): Float {
        val oldSize = sourceSize*base*oldZoom
        val newSize = sourceSize*base*zoom
        if (newSize-size < .01f) return .5f
        val oldStart = (size-oldSize)*position
        val newStart = focus-(focus-oldStart)*(newSize/oldSize)+pan
        return (newStart/(size-newSize)).coerceIn(0f,1f)
    }
    return slot.copy(fill = true, zoom = zoom,
        x = position(width,sourceWidth,slot.x,focusX,panX),
        y = position(height,sourceHeight,slot.y,focusY,panY))
}
