package com.widelight

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun previewStaysPinnedWhileControlsScrollAndExtrasStartHidden() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val library = Library(app)
        library.compose(Photo("preview", File(app.filesDir,"missing.photo"), "Photo"))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(360.dp,640.dp)) { ComposerScreen(library) } } }
        val before = compose.onNodeWithTag("pinned-preview").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(app.getString(R.string.qr_link_or_message_optional)).assertDoesNotExist()
        compose.onNodeWithTag("composer-controls").performScrollToIndex(2)
        assertEquals(before,compose.onNodeWithTag("pinned-preview").fetchSemanticsNode().boundsInRoot)
        assertTrue(before.height > 0)
        compose.onNodeWithText(app.getString(R.string.more_options)).performClick()
        compose.onNodeWithTag("composer-controls").performScrollToIndex(4)
        compose.onNodeWithText(app.getString(R.string.qr_link_or_message_optional)).assertExists()
        assertEquals(before,compose.onNodeWithTag("pinned-preview").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun skinControlsShowNumbersAndRestoreThePreviousOutput() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val library = Library(app)
        library.updatePrintColor(PrintColor())
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { PrintColorControls(library) } } }
        compose.onNodeWithText(app.getString(R.string.print_colors)).performClick()
        compose.onNodeWithText(app.getString(R.string.skin_tint)+": 6").assertExists()
        compose.onNodeWithText(app.getString(R.string.skin_depth)+": 6").assertExists()
        compose.onNodeWithText(app.getString(R.string.color_unadjusted)).performClick()
        compose.runOnIdle { assertEquals(PrintColor.NEUTRAL,library.printColor) }
        compose.onNodeWithText(app.getString(R.string.skin_tint)+": 0").assertExists()
        compose.onNodeWithText(app.getString(R.string.color_defaults)).performClick()
        compose.runOnIdle { assertEquals(PrintColor(),library.printColor) }
    }
    @Test @Config(qualifiers = "ru") fun russianResourcesAndLandscapeControlsAreAvailable() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("Добавить фото",app.getString(R.string.add_photos))
        val library = Library(app)
        library.compose(Photo("preview",File(app.filesDir,"missing.photo"),"Photo"))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(640.dp,360.dp)) { ComposerScreen(library) } } }
        val before = compose.onNodeWithTag("pinned-preview").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("composer-controls").performScrollToIndex(2)
        compose.onNodeWithText("Дополнительно").assertExists()
        assertEquals(before,compose.onNodeWithTag("pinned-preview").fetchSemanticsNode().boundsInRoot)
    }
}
