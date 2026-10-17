package com.widelight

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

class CompositionTest {
    @Test fun gesturesTrackFingerMovementAndKeepTheZoomAnchor() {
        val slot = Slot(Photo("test", File("test.photo"), "Test"), zoom = 2f)
        val moved = transformCrop(slot,600,400,600f,400f,300f,200f,60f,0f,1f)
        assertEquals(.4f,moved.x,.001f)
        assertEquals(.5f,moved.y,.001f)
        val zoomed = transformCrop(slot,600,400,600f,400f,0f,0f,0f,0f,1.5f)
        assertEquals(.375f,zoomed.x,.001f)
        assertEquals(3f,zoomed.zoom,.001f)
        val clamped = transformCrop(slot,600,400,600f,400f,0f,0f,10000f,-10000f,10f)
        assertEquals(0f,clamped.x,.001f)
        assertEquals(1f,clamped.y,.001f)
    }

    @Test fun everyLayoutTilesTheCanvasWithoutOverlap() {
        for (count in 1..6) for (layout in Layout.entries) {
            val cells = CollageLayout.cells(count, layout)
            assertEquals(count, cells.size)
            var area = 0f
            cells.forEach { cell ->
                assertTrue(cell.left >= 0 && cell.top >= 0 && cell.right <= 1 && cell.bottom <= 1)
                assertTrue(cell.left < cell.right && cell.top < cell.bottom)
                area += (cell.right-cell.left)*(cell.bottom-cell.top)
            }
            assertEquals(1f, area, .00001f)
            cells.forEachIndexed { i, a -> cells.drop(i+1).forEach { b ->
                val overlap = maxOf(0f, minOf(a.right,b.right)-maxOf(a.left,b.left)) * maxOf(0f, minOf(a.bottom,b.bottom)-maxOf(a.top,b.top))
                assertEquals(0f, overlap, .00001f)
            } }
        }
    }
    @Test fun validatesQrByUtf8BytesAndRejectsInvalidCrop() {
        val slot = Slot(Photo("test", File("test.photo"), "Test"))
        Composition(listOf(slot), qr = "é".repeat(80)).validate()
        assertThrows(IllegalArgumentException::class.java) { Composition(listOf(slot), qr = "é".repeat(81)).validate() }
        assertThrows(IllegalArgumentException::class.java) { Composition(listOf(slot.copy(zoom = .5f))).validate() }
        assertThrows(IllegalArgumentException::class.java) { Composition(emptyList()).validate() }
        assertThrows(IllegalArgumentException::class.java) { Composition(List(7) { slot }).validate() }
    }
    @Test fun qrCanBeDecodedIncludingUnicode() {
        val text = "https://example.com/album?name=été"
        val matrix = QrCode.matrix(text)
        val scale = 5
        val size = matrix.width * scale
        val pixels = IntArray(size*size) { index -> if (matrix[(index%size)/scale, (index/size)/scale]) 0xFF000000.toInt() else -1 }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))))
        assertEquals(text, decoded.text)
    }
}
