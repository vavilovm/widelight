package com.widelight

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(library: Library) {
    val draft = library.composition ?: return
    var active by rememberSaveable { mutableIntStateOf(0) }
    var choose by rememberSaveable { mutableStateOf(draft.slots.isEmpty()) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(false) }
    val valid = runCatching { draft.validate() }.exceptionOrNull()?.message
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { library.import(it) }
    BackHandler { if (!library.busy) discard = true }
    LaunchedEffect(draft) {
        rendering = true
        try {
            delay(120)
            if (draft.slots.isEmpty()) { preview = null; previewError = null }
            else {
                val bitmap = withContext(Dispatchers.IO) { CompositionRenderer.render(draft, 630, 420) }
                preview = bitmap
                previewError = null
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { preview = null; previewError = e.message ?: "Could not render preview" }
        finally { rendering = false }
    }
    fun update(next: Composition) { library.updateComposition(next) }
    fun editSlot(change: (Slot) -> Slot) {
        val index = active.coerceIn(0, (draft.slots.size-1).coerceAtLeast(0))
        if (draft.slots.isNotEmpty()) update(draft.copy(slots = draft.slots.mapIndexed { i, slot -> if (i == index) change(slot) else slot }))
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Compose a print") }, navigationIcon = {
        TextButton(enabled = !library.busy, onClick = { discard = true }) { Text("Back") }
    }) }, bottomBar = {
        Surface(tonalElevation = 3.dp) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (library.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(library.message) }
            library.compositionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (valid != null && draft.slots.isNotEmpty()) Text(valid, color = MaterialTheme.colorScheme.error)
            Button(modifier = Modifier.fillMaxWidth(), enabled = !library.busy && valid == null && !rendering && preview != null,
                onClick = { library.saveComposition() }) { Text("Save print to queue") }
            Text("Creates a new print. Your originals stay in the queue.", style = MaterialTheme.typography.bodySmall)
        } }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                preview?.let { Image(it.asImageBitmap(), "Collage print preview", Modifier.fillMaxWidth().aspectRatio(1.5f).background(Color.White)) }
                if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth())
                previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (draft.slots.isEmpty()) Text("Pick up to six photos to start. Choose one for a frame or QR print.", Modifier.padding(vertical = 20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !library.busy, onClick = { choose = !choose }) { Text("Queue photos · ${draft.slots.size}/6") }
                    OutlinedButton(enabled = !library.busy && draft.slots.size < 6, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Add photos") }
                }
            }
            if (choose) {
                item { Text("Tap to select or remove. Selection order becomes the layout order.", style = MaterialTheme.typography.bodySmall) }
                items(library.photos, key = { "choose-${it.id}" }) { photo ->
                    val index = draft.slots.indexOfFirst { it.photo.id == photo.id }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhotoThumbnail(photo, Modifier.size(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text(photo.title)
                            OutlinedButton(enabled = !library.busy && (index >= 0 || draft.slots.size < 6), onClick = {
                                update(draft.copy(slots = if (index >= 0) draft.slots.filterIndexed { i, _ -> i != index } else draft.slots + Slot(photo)))
                                active = 0
                            }) { Text(if (index >= 0) "✓ Photo ${index+1} · Remove" else "Select") }
                        }
                    }
                }
                item { TextButton(onClick = { choose = false }) { Text("Done choosing") } }
            }
            if (draft.slots.isNotEmpty()) {
                item {
                    Text("Layout", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Layout.entries) { layout -> FilterChip(selected = draft.layout == layout, enabled = !library.busy, onClick = { update(draft.copy(layout = layout)) }, label = { Text(layout.label) }) }
                    }
                    Text("Edit a photo", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(draft.slots.size) { index -> FilterChip(selected = active.coerceAtMost(draft.slots.lastIndex) == index, enabled = !library.busy, onClick = { active = index }, label = { Text("Photo ${index+1}") }) }
                    }
                    val index = active.coerceIn(0, draft.slots.lastIndex)
                    val slot = draft.slots[index]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !library.busy && index > 0, onClick = {
                            val list = draft.slots.toMutableList(); java.util.Collections.swap(list, index, index-1)
                            update(draft.copy(slots = list)); active = index-1
                        }) { Text("← Earlier") }
                        TextButton(enabled = !library.busy && index < draft.slots.lastIndex, onClick = {
                            val list = draft.slots.toMutableList(); java.util.Collections.swap(list, index, index+1)
                            update(draft.copy(slots = list)); active = index+1
                        }) { Text("Later →") }
                        TextButton(enabled = !library.busy, onClick = { editSlot { it.copy(turns = (it.turns+1)%4) } }) { Text("Rotate") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = slot.fill, enabled = !library.busy, onClick = { editSlot { it.copy(fill = true) } }, label = { Text("Fill & crop") })
                        FilterChip(selected = !slot.fill, enabled = !library.busy, onClick = { editSlot { it.copy(fill = false) } }, label = { Text("Fit whole photo") })
                    }
                    if (slot.fill) {
                        LabeledSlider("Zoom", slot.zoom, 1f..3f, !library.busy) { value -> editSlot { it.copy(zoom = value) } }
                        LabeledSlider("Horizontal position", slot.x, 0f..1f, !library.busy) { value -> editSlot { it.copy(x = value) } }
                        LabeledSlider("Vertical position", slot.y, 0f..1f, !library.busy) { value -> editSlot { it.copy(y = value) } }
                        TextButton(enabled = !library.busy, onClick = { editSlot { it.copy(zoom = 1f, x = .5f, y = .5f) } }) { Text("Reset crop") }
                    }
                }
                item {
                    Text("Frame & spacing", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Frame.entries) { frame -> FilterChip(selected = draft.frame == frame, enabled = !library.busy, onClick = { update(draft.copy(frame = frame)) }, label = { Text(frame.label) }) }
                    }
                    if (draft.frame != Frame.NONE) LabeledSlider("Border", draft.border, 0f..0.08f, !library.busy) { update(draft.copy(border = it)) }
                    if (draft.slots.size > 1) LabeledSlider("Photo spacing", draft.gap, 0f..0.04f, !library.busy) { update(draft.copy(gap = it)) }
                    Text("Color look", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Look.entries) { look -> FilterChip(selected = draft.look == look, enabled = !library.busy, onClick = { update(draft.copy(look = look)) }, label = { Text(look.label) }) }
                    }
                }
                item {
                    OutlinedTextField(value = draft.caption, enabled = !library.busy, onValueChange = { if (it.length <= 80) update(draft.copy(caption = it)) },
                        label = { Text("Caption (optional)") }, supportingText = { Text("${draft.caption.length}/80") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = draft.qr, enabled = !library.busy, onValueChange = { update(draft.copy(qr = it)) },
                        label = { Text("QR link or message (optional)") }, supportingText = { Text("Link to a website, audio or map, or write a short message. No uploads required.") },
                        isError = draft.qr.toByteArray(Charsets.UTF_8).size > 160, modifier = Modifier.fillMaxWidth())
                    Text("QR codes use a separate white panel to stay readable. Check a test print with your phone before making copies.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Leave this design?") }, text = { Text("Unsaved layout changes will be discarded. Original photos stay in your queue.") }, confirmButton = { TextButton(onClick = { library.closeComposition() }) { Text("Discard design") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onValueChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled)
}

@Composable
private fun PhotoThumbnail(photo: Photo, modifier: Modifier) {
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.id) {
        try { bitmap = withContext(Dispatchers.IO) { Photos.render(photo, 180, 120) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Photo thumbnail", modifier) } ?: Spacer(modifier)
}
