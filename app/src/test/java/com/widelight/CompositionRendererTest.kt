package com.widelight

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
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
class CompositionRendererTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun photo(color: Int, second: Int = color): Photo {
        val id = java.util.UUID.randomUUID().toString()
        val file = File(app.cacheDir, "$id.photo")
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 200) bitmap.setPixel(x,y,if (x<100) color else second)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        return Photo(id, file, "Test")
    }
    @Test fun cellsAreClippedAndSourceOrderIsPreserved() {
        val red = photo(Color.RED)
        val blue = photo(Color.BLUE)
        val design = Composition(listOf(Slot(red),Slot(blue)), frame = Frame.NONE, gap = 0f)
        val rendered = CompositionRenderer.render(design, 600, 400)
        assertEquals(Color.RED, rendered.getPixel(150,200))
        assertEquals(Color.BLUE, rendered.getPixel(450,200))
        val swapped = CompositionRenderer.render(design.copy(slots = design.slots.reversed()),600,400)
        assertEquals(Color.BLUE, swapped.getPixel(150,200))
        rendered.recycle(); swapped.recycle()
    }
    @Test fun blankSpaceWarningsSurviveFlatteningAndPreviewMarksOnlyBlankAreas() {
        val source = photo(Color.RED)
        assertTrue(source.fill)
        val fit = Composition(listOf(Slot(source, fill = false)), frame = Frame.NONE)
        assertTrue(CompositionRenderer.hasBlankSpace(fit))
        assertFalse(CompositionRenderer.hasBlankSpace(fit.copy(slots = listOf(Slot(source)))))
        val saved = CompositionRenderer.save(fit, app.filesDir)
        assertTrue(saved.uncovered)
        assertTrue(Photos.hasBlankSpace(saved))
        val plain = CompositionRenderer.render(fit, 600, 400)
        val highlighted = CompositionRenderer.render(fit, 600, 400, highlightBlank = true)
        assertEquals(Color.WHITE, plain.getPixel(300, 10))
        assertEquals(Color.RED, highlighted.getPixel(300, 200))
        assertTrue((0 until 600).any { highlighted.getPixel(it, 10) != Color.WHITE })
        plain.recycle(); highlighted.recycle()
    }
    @Test fun cropPositionAndFitAreRespected() {
        val source = photo(Color.RED, Color.BLUE)
        val design = Composition(listOf(Slot(source, zoom = 3f, x = 0f)), frame = Frame.NONE)
        val left = CompositionRenderer.render(design,600,400)
        val right = CompositionRenderer.render(design.copy(slots = listOf(Slot(source,zoom=3f,x=1f))),600,400)
        assertEquals(Color.RED, left.getPixel(300,200))
        assertEquals(Color.BLUE, right.getPixel(300,200))
        val fit = CompositionRenderer.render(design.copy(slots = listOf(Slot(source,fill=false))),600,400)
        assertEquals(Color.WHITE,fit.getPixel(300,10))
        left.recycle(); right.recycle(); fit.recycle()
    }
    @Test fun savedCollageSurvivesSourceRemovalAndFinalJpegContainsReadableQr() {
        val sources = listOf(photo(Color.RED),photo(Color.BLUE),photo(Color.GREEN))
        val url = "https://example.com/my-album"
        val design = Composition(sources.map { Slot(it) }, layout = Layout.HERO, frame = Frame.CREAM, caption = "A day to remember", qr = url)
        val saved = CompositionRenderer.save(design, app.filesDir)
        sources.forEach { assertTrue(it.file.delete()) }
        val bytes = Photos.jpeg(saved, Printer.Capabilities(1260,840,337920))
        assertTrue(bytes.size <= 337920)
        val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(1260,bitmap.width)
        assertEquals(840,bitmap.height)
        val pixels = IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        assertEquals(url, MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text)
        val output = File("build/reports/composition-example.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle(); saved.file.delete()
    }
    @Test fun libraryLoadsOlderIndexAndPersistsDesignedPrintTitles() {
        val source = photo(Color.MAGENTA)
        val saved = CompositionRenderer.save(Composition(listOf(Slot(source))),app.filesDir)
        File(app.filesDir,"library.json").writeText("""[{"id":"${saved.id}","favorite":true,"turns":1,"fill":true}]""")
        val legacy = Library(app).photos.single()
        assertEquals("Photo",legacy.title)
        assertTrue(legacy.favorite)
        assertEquals(1,legacy.turns)
        assertTrue(legacy.fill)
        File(app.filesDir,"library.json").writeText("""[{"id":"${saved.id}","title":"Designed print"}]""")
        val library = Library(app)
        assertEquals("Designed print",library.photos.single().title)
        library.edit(library.photos.single().edited(favorite=true))
        assertTrue(Library(app).photos.single().favorite)
        library.remove(library.photos.single())
        assertFalse(saved.file.exists())
        assertTrue(Library(app).photos.isEmpty())
    }
}
