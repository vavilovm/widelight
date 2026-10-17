package com.widelight

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintColorTest {
    @Test fun defaultKeepsGreensAndNeutralTonesExactly() {
        val tuning = PrintColor()
        for (color in listOf(Color.RED,Color.YELLOW,Color.MAGENTA)) {
            assertEquals(color,tuning.pixel(color))
        }
        // Entire green hue family, including pale and dark foliage, plus blues and neutrals.
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            if (g >= r && g >= b || b >= r && b >= g || r == g && g == b) {
                val c = Color.rgb(r,g,b)
                assertEquals("$r/$g/$b",c,tuning.pixel(c))
                assertEquals(c,PrintColor(30,30).pixel(c))
            }
        }
    }
    @Test fun skinGetsDeeperAndPinkerAcrossLightAndDarkSamples() {
        for (c in listOf(Color.rgb(238,206,185),Color.rgb(190,140,110),Color.rgb(115,76,57))) {
            val corrected = PrintColor().pixel(c)
            assertTrue(Color.green(corrected) < Color.green(c))
            assertTrue(Color.red(corrected)-Color.green(corrected) > Color.red(c)-Color.green(c))
            val deeper = PrintColor(skinDepth = 15).pixel(c)
            assertTrue(Color.green(deeper) < Color.green(corrected))
            val pinker = PrintColor(skinTint = 15).pixel(c)
            assertTrue(Color.red(pinker)-Color.green(pinker) > Color.red(corrected)-Color.green(corrected))
        }
    }
    @Test fun neutralIsExactAndPixelsKeepAlpha() {
        for (c in listOf(Color.WHITE,Color.BLACK,Color.rgb(238,206,185),0x80776655.toInt())) {
            assertEquals(c,PrintColor.NEUTRAL.pixel(c))
            assertEquals(Color.alpha(c),Color.alpha(PrintColor(30,30).pixel(c)))
        }
    }
    @Test fun previewAndTransferredJpegUseSameCorrectionWithoutBakingIntoSources() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir,"skin-test.png")
        val source = Bitmap.createBitmap(60,40,Bitmap.Config.ARGB_8888)
        val skin = Color.rgb(238,206,185)
        source.eraseColor(skin)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG,100,it) }
        source.recycle()
        val photo = Photo("skin-test",file,"test")
        val preview = Photos.render(photo,60,40,PrintColor())
        assertEquals(PrintColor().pixel(skin),preview.getPixel(30,20))
        val bytes = Photos.jpeg(photo,Printer.Capabilities(60,40,100000),PrintColor())
        val jpeg = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        val expected = preview.getPixel(30,20)
        val actual = jpeg.getPixel(30,20)
        assertTrue(kotlin.math.abs(Color.green(expected)-Color.green(actual)) <= 3)
        val original = Photos.render(photo,60,40)
        assertEquals(skin,original.getPixel(30,20))
        val saved = CompositionRenderer.save(Composition(listOf(Slot(photo)),frame = Frame.NONE),app.cacheDir)
        val once = Photos.render(saved,60,40,PrintColor())
        assertEquals(expected,once.getPixel(30,20))
        preview.recycle(); jpeg.recycle(); original.recycle(); once.recycle()
        file.delete(); saved.file.delete()
    }
    @Test fun settingsPersistAcrossLibraryRecreation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val first = Library(app)
        first.updatePrintColor(PrintColor(12,10))
        assertEquals(PrintColor(12,10),Library(app).printColor)
        first.updatePrintColor(PrintColor())
    }
}
