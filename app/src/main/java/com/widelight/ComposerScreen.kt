package com.widelight

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(library: Library) {
    val draft = library.composition ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    var active by rememberSaveable { mutableIntStateOf(0) }
    var choose by rememberSaveable { mutableStateOf(draft.slots.isEmpty()) }
    var extras by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var dimensions by remember { mutableStateOf<Map<String, Pair<Int,Int>>>(emptyMap()) }
    var uncovered by remember { mutableStateOf(false) }
    var confirmBlank by remember { mutableStateOf(false) }
    val currentDraft by rememberUpdatedState(draft)
    val currentActive by rememberUpdatedState(active)
    var previewError by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(false) }
    val valid = runCatching { draft.validate() }.exceptionOrNull()?.let { context.failureText(it, R.string.could_not_render_preview) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { library.import(it) }
    BackHandler { if (!library.busy) discard = true }
    LaunchedEffect(draft.slots.map { it.photo.id to it.turns }) {
        dimensions = withContext(Dispatchers.IO) {
            draft.slots.mapNotNull { slot -> runCatching {
                val image = Photos.decode(slot.photo.edited(turns = slot.turns))
                try { "${slot.photo.id}:${slot.turns}" to (image.width to image.height) } finally { image.recycle() }
            }.getOrNull() }.toMap()
        }
    }
    LaunchedEffect(Unit) {
      snapshotFlow { currentDraft to library.printColor }.conflate().collect { (draft, color) ->
        rendering = true
        try {
            if (draft.slots.isEmpty()) { preview = null; previewError = null }
            else {
                val bitmap = withContext(Dispatchers.IO) { CompositionRenderer.render(draft, 630, 420, highlightBlank = true).also { color.apply(it) } }
                uncovered = withContext(Dispatchers.IO) { CompositionRenderer.hasBlankSpace(draft) }
                preview = bitmap
                previewError = null
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { preview = null; previewError = context.failureText(e, R.string.could_not_render_preview) }
        finally { rendering = currentDraft != draft }
      }
    }
    fun update(next: Composition) { library.updateComposition(next) }
    fun editSlot(change: (Slot) -> Slot) {
        val index = active.coerceIn(0, (draft.slots.size-1).coerceAtLeast(0))
        if (draft.slots.isNotEmpty()) update(draft.copy(slots = draft.slots.mapIndexed { i, slot -> if (i == index) change(slot) else slot }))
    }
    Scaffold(modifier = Modifier.imePadding(), topBar = { TopAppBar(title = { Text(stringResource(R.string.compose_a_print)) }, navigationIcon = {
        TextButton(enabled = !library.busy, onClick = { discard = true }) { Text(stringResource(R.string.back)) }
    }) }, bottomBar = {
        Surface(tonalElevation = 3.dp) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (library.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(library.message) }
            library.compositionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (valid != null && draft.slots.isNotEmpty()) Text(valid, color = MaterialTheme.colorScheme.error)
            Button(modifier = Modifier.fillMaxWidth(), enabled = !library.busy && valid == null && !rendering && preview != null,
                onClick = { if (uncovered) confirmBlank = true else library.saveComposition() }) { Text(stringResource(R.string.save_print_to_queue)) }
            Text(stringResource(R.string.creates_a_new_print_your_originals_stay), style = MaterialTheme.typography.bodySmall)
        } }
    }) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val previewHeight = minOf(maxWidth / 1.5f, maxHeight * .38f)
            Column(Modifier.fillMaxSize()) {
                if (draft.slots.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(previewHeight).background(Color(0xFF3D4544)).testTag("pinned-preview"), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it.asImageBitmap(), stringResource(R.string.collage_print_preview), Modifier.fillMaxSize()) }
                        Canvas(Modifier.fillMaxSize()
                            .pointerInput(library.busy) {
                                detectTapGestures { point ->
                                    if (!library.busy) {
                                        val w = minOf(size.width.toFloat(), size.height*1.5f)
                                        val h = w/1.5f
                                        val px = point.x-(size.width-w)/2
                                        val py = point.y-(size.height-h)/2
                                        CompositionRenderer.targets(currentDraft,w,h).indexOfFirst { it.contains(px,py) }
                                            .takeIf { it >= 0 }?.let { active = it }
                                    }
                                }
                            }
                            .pointerInput(library.busy) {
                                detectTransformGestures { focus, pan, zoom, _ ->
                                    val design = currentDraft
                                    val index = currentActive.coerceIn(0, design.slots.lastIndex)
                                    if (!library.busy) {
                                        val w = minOf(size.width.toFloat(),size.height*1.5f)
                                        val rect = CompositionRenderer.targets(design,w,w/1.5f)[index]
                                        val slot = design.slots[index]
                                        dimensions["${slot.photo.id}:${slot.turns}"]?.let { (sw,sh) ->
                                            update(design.copy(slots = design.slots.mapIndexed { i, value ->
                                                if (i != index) value else transformCrop(slot,sw,sh,rect.width(),rect.height(),
                                                    focus.x-(size.width-w)/2-rect.left,
                                                    focus.y-(size.height-w/1.5f)/2-rect.top,pan.x,pan.y,zoom)
                                            }))
                                        }
                                    }
                                }
                            }) {
                            val w = minOf(size.width,size.height*1.5f)
                            val h = w/1.5f
                            CompositionRenderer.targets(draft,w,h).forEachIndexed { i, rect ->
                                val left = rect.left+(size.width-w)/2
                                val top = rect.top+(size.height-h)/2
                                if (i == active.coerceIn(0,draft.slots.lastIndex)) {
                                    drawRect(Color(0xFFFFB300),Offset(left,top),Size(rect.width(),rect.height()),style=Stroke(3.dp.toPx()))
                                    for (line in 1..2) {
                                        drawLine(Color.White.copy(alpha=.6f),Offset(left+rect.width()*line/3,top),Offset(left+rect.width()*line/3,top+rect.height()))
                                        drawLine(Color.White.copy(alpha=.6f),Offset(left,top+rect.height()*line/3),Offset(left+rect.width(),top+rect.height()*line/3))
                                    }
                                }
                            }
                        }

                    }
                    Text(stringResource(R.string.crop_hint), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    if (uncovered) {
                        Text(stringResource(R.string.blank_warning), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                        TextButton(enabled = !library.busy, onClick = { update(draft.copy(slots = draft.slots.map { it.copy(fill = true) })) }) { Text(stringResource(R.string.fill_all)) }
                    }
                    if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth())
                    previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("composer-controls"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (draft.slots.isEmpty()) Text(stringResource(R.string.pick_up_to_six_photos_to_start), Modifier.padding(vertical = 20.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !library.busy, onClick = { choose = !choose }) { Text(stringResource(R.string.selection_count, draft.slots.size)) }
                    OutlinedButton(enabled = !library.busy && draft.slots.size < 6, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.add_photos)) }
                }
            }
            if (choose) {
                item { Text(stringResource(R.string.tap_to_select_or_remove_selection_order), style = MaterialTheme.typography.bodySmall) }
                items(library.photos, key = { "choose-${it.id}" }) { photo ->
                    val index = draft.slots.indexOfFirst { it.photo.id == photo.id }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhotoThumbnail(photo, Modifier.size(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text(photo.localizedTitle())
                            OutlinedButton(enabled = !library.busy && (index >= 0 || draft.slots.size < 6), onClick = {
                                update(draft.copy(slots = if (index >= 0) draft.slots.filterIndexed { i, _ -> i != index } else draft.slots + Slot(photo)))
                                active = 0
                            }) { Text(if (index >= 0) stringResource(R.string.selected_photo, index+1) else stringResource(R.string.select)) }
                        }
                    }
                }
                item { TextButton(onClick = { choose = false }) { Text(stringResource(R.string.done_choosing)) } }
            }
            if (draft.slots.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.layout), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Layout.entries) { layout -> FilterChip(selected = draft.layout == layout, enabled = !library.busy, onClick = { update(draft.copy(layout = layout)) }, label = { Text(stringResource(layout.label)) }, leadingIcon = {
                            Canvas(Modifier.size(30.dp,20.dp)) {
                                CollageLayout.cells(draft.slots.size,layout).forEach { cell ->
                                    drawRect(Color(0xFF236C61),Offset(cell.left*size.width+1,cell.top*size.height+1),
                                        Size((cell.right-cell.left)*size.width-2,(cell.bottom-cell.top)*size.height-2))
                                }
                            }
                        }) }
                    }
                    Text(stringResource(R.string.edit_a_photo), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(draft.slots.size) { index -> FilterChip(selected = active.coerceAtMost(draft.slots.lastIndex) == index, enabled = !library.busy, onClick = { active = index }, label = { Text(stringResource(R.string.photo_number, index+1)) }, leadingIcon = { PhotoThumbnail(draft.slots[index].photo,Modifier.size(42.dp,28.dp)) }) }
                    }
                    val index = active.coerceIn(0, draft.slots.lastIndex)
                    val slot = draft.slots[index]
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !library.busy && index > 0, onClick = {
                            val list = draft.slots.toMutableList(); java.util.Collections.swap(list, index, index-1)
                            update(draft.copy(slots = list)); active = index-1
                        }) { Text(stringResource(R.string.earlier)) }
                        TextButton(enabled = !library.busy && index < draft.slots.lastIndex, onClick = {
                            val list = draft.slots.toMutableList(); java.util.Collections.swap(list, index, index+1)
                            update(draft.copy(slots = list)); active = index+1
                        }) { Text(stringResource(R.string.later)) }
                        TextButton(enabled = !library.busy, onClick = { editSlot { it.copy(turns = (it.turns+1)%4) } }) { Text(stringResource(R.string.rotate)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = slot.fill, enabled = !library.busy, onClick = { editSlot { it.copy(fill = true) } }, label = { Text(stringResource(R.string.fill_crop)) })
                        FilterChip(selected = !slot.fill, enabled = !library.busy, onClick = { editSlot { it.copy(fill = false) } }, label = { Text(stringResource(R.string.fit_whole_photo_text)) })
                    }
                    if (slot.fill) {
                        LabeledSlider(stringResource(R.string.zoom), slot.zoom, 1f..3f, !library.busy) { value -> editSlot { it.copy(zoom = value) } }
                        LabeledSlider(stringResource(R.string.horizontal_position), slot.x, 0f..1f, !library.busy) { value -> editSlot { it.copy(x = value) } }
                        LabeledSlider(stringResource(R.string.vertical_position), slot.y, 0f..1f, !library.busy) { value -> editSlot { it.copy(y = value) } }
                        TextButton(enabled = !library.busy, onClick = { editSlot { it.copy(fill = true, zoom = 1.03f, x = .5f, y = .5f) } }) { Text(stringResource(R.string.reset_crop)) }
                    }
                }
                item {
                    OutlinedButton(enabled = !library.busy, onClick = { extras = !extras }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (extras) R.string.hide_extra_options else R.string.more_options))
                    }
                }
                if (extras) {
                item {
                    Text(stringResource(R.string.frame_spacing), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Frame.entries) { frame -> FilterChip(selected = draft.frame == frame, enabled = !library.busy, onClick = { update(draft.copy(frame = frame)) }, label = { Text(stringResource(frame.label)) }) }
                    }
                    if (draft.frame != Frame.NONE) LabeledSlider(stringResource(R.string.border), draft.border, 0f..0.08f, !library.busy) { update(draft.copy(border = it)) }
                    if (draft.slots.size > 1) LabeledSlider(stringResource(R.string.photo_spacing), draft.gap, 0f..0.04f, !library.busy) { update(draft.copy(gap = it)) }
                    PrintColorControls(library)
                    Text(stringResource(R.string.color_look), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Look.entries) { look -> FilterChip(selected = draft.look == look, enabled = !library.busy, onClick = { update(draft.copy(look = look)) }, label = { Text(stringResource(look.label)) }) }
                    }
                }
                item {
                    OutlinedTextField(value = draft.caption, enabled = !library.busy, onValueChange = { if (it.length <= 80) update(draft.copy(caption = it)) },
                        label = { Text(stringResource(R.string.caption_optional)) }, supportingText = { Text(stringResource(R.string.caption_count, draft.caption.length)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = draft.qr, enabled = !library.busy, onValueChange = { update(draft.copy(qr = it)) },
                        label = { Text(stringResource(R.string.qr_link_or_message_optional)) }, supportingText = { Text(stringResource(R.string.link_to_a_website_audio_or_map)) },
                        isError = draft.qr.toByteArray(Charsets.UTF_8).size > 160, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.qr_codes_use_a_separate_white_panel), style = MaterialTheme.typography.bodySmall)
                }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
    if (confirmBlank) AlertDialog(onDismissRequest = { confirmBlank = false }, title = { Text(stringResource(R.string.blank_warning)) },
        confirmButton = { TextButton(onClick = { confirmBlank = false; library.saveComposition() }) { Text(stringResource(R.string.save_print_to_queue)) } },
        dismissButton = { TextButton(onClick = { confirmBlank = false }) { Text(stringResource(R.string.keep_editing)) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.leave_this_design)) }, text = { Text(stringResource(R.string.unsaved_layout_changes_will_be_discarded_original)) }, confirmButton = { TextButton(onClick = { library.closeComposition() }) { Text(stringResource(R.string.discard_design)) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } })
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
    bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.photo_thumbnail), modifier) } ?: Spacer(modifier)
}
