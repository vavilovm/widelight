package com.widelight

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/** Output correction, applied once after composition, never baked into saved originals. */
data class PrintColor(val skinTint: Int = 6, val skinDepth: Int = 6) {
    init {
        require(skinTint in -30..30 && skinDepth in 0..30)
    }
    companion object { val NEUTRAL = PrintColor(0, 0) }

    fun pixel(color: Int): Int {
        if (this == NEUTRAL) return color
        val r = Color.red(color).toFloat()
        val g = Color.green(color).toFloat()
        val b = Color.blue(color).toFloat()
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        val chroma = high - low
        val luma = .2126f*r + .7152f*g + .0722f*b
        // Smooth warm-hue mask, not face detection. Reds/oranges can also be affected.
        // Green/cyan/blue, neutral whites/grays and deep blacks receive no skin correction.
        val hue = if (chroma == 0f) 180f else when (high) {
            r -> 60f*((g-b)/chroma).let { if (it < 0) it+6 else it }
            g -> 60f*((b-r)/chroma+2)
            else -> 60f*((r-g)/chroma+4)
        }
        val warmHue = if (hue > 180) hue-360 else hue
        fun ramp(v: Float, start: Float, end: Float): Float {
            val t = ((v-start)/(end-start)).coerceIn(0f,1f)
            return t*t*(3-2*t)
        }
        val saturation = chroma/maxOf(high,1f)
        val skin = ramp(warmHue,-15f,5f)*(1-ramp(warmHue,35f,55f)) *
            ramp(saturation,.025f,.15f) * (1-ramp(saturation,.65f,.95f)) * ramp(luma,20f,65f) * (1-ramp(luma,235f,255f))
        val depth = 1-skin*skinDepth/100f
        val tint = skin*skinTint
        val channels = floatArrayOf(r*depth+tint*.5f, g*depth-tint*.5f, b*depth+tint*.25f)
        fun channel(i: Int) = channels[i].roundToInt().coerceIn(0,255)
        return Color.argb(Color.alpha(color),channel(0),channel(1),channel(2))
    }

    fun apply(bitmap: Bitmap) {
        if (this == NEUTRAL) return
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row,0,bitmap.width,0,y,bitmap.width,1)
            for (x in row.indices) row[x] = pixel(row[x])
            bitmap.setPixels(row,0,bitmap.width,0,y,bitmap.width,1)
        }
    }
}
