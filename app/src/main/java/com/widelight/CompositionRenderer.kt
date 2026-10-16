package com.widelight

import android.graphics.*
import java.io.File
import java.util.UUID

object CompositionRenderer {
    fun render(design: Composition, width: Int, height: Int): Bitmap {
        design.validate()
        require(width > 0 && height > 0)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            canvas.drawColor(design.frame.color)
            val unit = minOf(width, height).toFloat()
            val margin = if (design.frame == Frame.NONE) 0f else unit * design.border
            val qrWidth = if (design.qr.isNotBlank()) width * .25f else 0f
            val captionHeight = if (design.caption.isNotBlank()) height * .12f else 0f
            val area = RectF(margin, margin, width-margin-qrWidth, height-margin-captionHeight)
            val cells = CollageLayout.cells(design.slots.size, design.layout)
            design.slots.zip(cells).forEach { (slot, cell) ->
                val gap = if (design.slots.size == 1) 0f else unit * design.gap / 2
                val target = RectF(area.left + cell.left * area.width() + gap, area.top + cell.top * area.height() + gap,
                    area.left + cell.right * area.width() - gap, area.top + cell.bottom * area.height() - gap)
                val source = Photos.decode(slot.photo.edited(turns = slot.turns))
                try {
                    val scale = if (slot.fill) maxOf(target.width()/source.width, target.height()/source.height)*slot.zoom else minOf(target.width()/source.width, target.height()/source.height)
                    val w = source.width * scale
                    val h = source.height * scale
                    val x = target.left + (target.width()-w) * if (slot.fill) slot.x else .5f
                    val y = target.top + (target.height()-h) * if (slot.fill) slot.y else .5f
                    canvas.save()
                    canvas.clipRect(target)
                    // Fit preserves the complete photo on a neutral background in every frame color.
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(source, null, RectF(x, y, x+w, y+h), Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = colorFilter(design.look) })
                    canvas.restore()
                } finally { source.recycle() }
            }
            val foreground = if (design.frame == Frame.INK || design.frame == Frame.TEAL) Color.WHITE else Color.BLACK
            if (captionHeight > 0) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textSize = height * .045f }
                val text = design.caption.replace('\n', ' ')
                val available = width - 2 * maxOf(margin, unit * .025f)
                if (paint.measureText(text) > available) paint.textSize *= available / paint.measureText(text)
                val center = height - margin - captionHeight/2
                canvas.drawText(text, width/2f, center - (paint.ascent()+paint.descent())/2, paint)
            }
            if (qrWidth > 0) {
                val matrix = QrCode.matrix(design.qr)
                val maxSize = minOf(qrWidth - unit*.04f, area.height()*.8f)
                val module = (maxSize/matrix.width).toInt().coerceAtLeast(1)
                val size = module * matrix.width
                val left = (width-margin-qrWidth/2-size/2).toInt()
                val top = (area.centerY()-size/2).toInt()
                val paint = Paint().apply { color = Color.WHITE }
                canvas.drawRect(left.toFloat(), top.toFloat(), (left+size).toFloat(), (top+size).toFloat(), paint)
                paint.color = Color.BLACK
                for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x,y]) {
                    canvas.drawRect((left+x*module).toFloat(), (top+y*module).toFloat(), (left+(x+1)*module).toFloat(), (top+(y+1)*module).toFloat(), paint)
                }
            }
            return result
        } catch (e: Exception) { result.recycle(); throw e }
    }
    private fun colorFilter(look: Look): ColorFilter? = when (look) {
        Look.NATURAL -> null
        Look.MONO -> ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        Look.POP -> ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.3f) })
        Look.WARM -> ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1.06f,0f,0f,0f,3f, 0f,1f,0f,0f,1f, 0f,0f,.92f,0f,0f, 0f,0f,0f,1f,0f)))
    }
    /** Flatten to a new lossless photo: originals remain untouched and can be removed later. */
    fun save(design: Composition, directory: File): Photo {
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.photo")
        val bitmap = render(design, 1260, 840)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return Photo(id, file, if (design.slots.size > 1) "Collage · ${design.slots.size} photos" else "Designed print")
        } catch (e: Exception) { file.delete(); throw e } finally { bitmap.recycle() }
    }
}
