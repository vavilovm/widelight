package com.widelight

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Library(application: Application) : AndroidViewModel(application) {
    var photos by mutableStateOf(listOf<Photo>()); private set
    var composition by mutableStateOf<Composition?>(null); private set
    var compositionError by mutableStateOf<String?>(null); private set
    var message by mutableStateOf(application.getString(R.string.ready_when_you_are)); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    private val context get() = getApplication<Application>()
    private val colorPrefs = application.getSharedPreferences("print-color", 0)
    var printColor by mutableStateOf(PrintColor(
        colorPrefs.getInt("tint", 6).coerceIn(-30,30), colorPrefs.getInt("depth", 6).coerceIn(0,30)))
        private set
    fun updatePrintColor(value: PrintColor) {
        if (busy) return
        printColor = value
        colorPrefs.edit().putInt("tint",value.skinTint).putInt("depth",value.skinDepth).apply()
    }
    private val index get() = File(context.filesDir, "library.json")
    init {
        runCatching {
            val a = JSONArray(index.takeIf { it.exists() }?.readText() ?: "[]")
            photos = (0 until a.length()).map { a.getJSONObject(it) }.map {
                val id = it.getString("id")
                Photo(id, File(context.filesDir, "$id.photo"), it.optString("title", "Photo"), it.optBoolean("favorite"), it.optInt("turns"), it.optBoolean("fill"), it.optBoolean("uncovered"))
            }.filter { it.file.exists() }
        }.onFailure { message = context.getString(R.string.saved_library_could_not_be_restored) }
    }
    private fun save() {
        val data = JSONArray()
        photos.forEach { data.put(JSONObject().put("id", it.id).put("title", it.title).put("favorite", it.favorite).put("turns", it.turns).put("fill", it.fill).put("uncovered", it.uncovered)) }
        val temp = File(context.filesDir, "library.tmp")
        temp.writeText(data.toString())
        check(temp.renameTo(index)) { context.getString(R.string.cannot_save_library) }
    }
    fun compose(photo: Photo? = null) {
        if (!busy) { compositionError = null; composition = Composition(photo?.let { listOf(Slot(it, fill = it.fill)) }.orEmpty()) }
    }
    fun updateComposition(design: Composition) { if (!busy) { compositionError = null; composition = design } }
    fun closeComposition() { if (!busy) composition = null }
    fun saveComposition() {
        val design = composition ?: return
        if (busy) return
        busy = true; compositionError = null; message = context.getString(R.string.rendering_designed_print)
        viewModelScope.launch {
            try {
                val photo = withContext(Dispatchers.IO) { CompositionRenderer.save(design, context.filesDir) }
                try {
                    val previous = photos
                    photos = photos + photo
                    try { save() } catch (e: Exception) { photos = previous; throw e }
                    composition = null
                    message = context.getString(R.string.designed_print_added_to_queue)
                } catch (e: Exception) { photo.file.delete(); throw e }
            } catch (e: Exception) { compositionError = context.failureText(e, R.string.could_not_save_print); message = compositionError!! }
            finally { busy = false }
        }
    }
    fun edit(photo: Photo) { if (!busy) { photos = photos.map { if (it.id == photo.id) photo else it }; save() } }
    fun remove(photo: Photo) { if (!busy) { photos = photos.filter { it.id != photo.id }; save(); photo.file.delete() } }
    fun import(uris: List<android.net.Uri>) {
        if (busy || uris.isEmpty()) return
        busy = true
        viewModelScope.launch {
            var imported = 0
            var failed = 0
            uris.take(50).forEach { uri ->
                try {
                    val photo = withContext(Dispatchers.IO) { Photos.import(context, uri) }
                    photos = photos + photo
                    save()
                    composition?.let { draft ->
                        if (draft.slots.size < 6) composition = draft.copy(slots = draft.slots + Slot(photo))
                    }
                    imported++
                } catch (_: Exception) { failed++ }
            }
            message = context.resources.getQuantityString(R.plurals.imported, imported, imported) + if (failed > 0) context.resources.getQuantityString(R.plurals.import_failed, failed, failed) else ""
            busy = false
        }
    }
    var filmRemaining by mutableStateOf<Int?>(null); private set
    var filmAddress by mutableStateOf<String?>(null); private set
    fun refreshFilm(address: String) {
        if (busy) return
        busy = true; filmRemaining = null; filmAddress = address
        viewModelScope.launch {
            try {
                filmRemaining = withContext(Dispatchers.IO) {
                    val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                    Printer(adapter.getRemoteDevice(address)).use { it.connect(); it.capabilities(); it.status(requireFilm = false) }
                }
            } catch (e: Exception) { message = context.failureText(e, R.string.connection_failed) }
            finally { busy = false }
        }
    }
    fun print(address: String, photo: Photo) {
        if (busy) return
        val color = printColor
        busy = true; progress = 0f; message = context.getString(R.string.connecting_to_printer)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: error(context.getString(R.string.bluetooth_is_unavailable))
                    check(adapter.isEnabled) { context.getString(R.string.turn_on_bluetooth_first) }
                    Printer(adapter.getRemoteDevice(address)).use { printer ->
                        printer.connect()
                        val caps = printer.capabilities()
                        val status = printer.status()
                        withContext(Dispatchers.Main) { filmRemaining = status; filmAddress = address; message = context.getString(R.string.preparing, context.resources.getQuantityString(R.plurals.film_remaining, status, status)) }
                        val jpeg = Photos.jpeg(photo, caps, color)
                        withContext(Dispatchers.Main) { message = context.getString(R.string.sending_photo) }
                        printer.print(jpeg) { value -> viewModelScope.launch { progress = value } }
                    }
                }
                filmRemaining = filmRemaining?.let { (it - 1).coerceAtLeast(0) }
                message = context.getString(R.string.print_accepted_wait_for_the_film_to)
            } catch (e: Exception) {
                filmRemaining = null
                message = context.getString(R.string.transfer_error, context.failureText(e, R.string.connection_failed))
            } finally { busy = false }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val library: Library by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receive(intent)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF236C61), background = Color(0xFFF5F3ED))) { App(library) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); receive(intent) }
    @Suppress("DEPRECATION")
    private fun receive(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        library.import(uris)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App(library: Library) {
    if (library.composition != null) {
        ComposerScreen(library)
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var favorites by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var printers by remember { mutableStateOf(false) }
    var printBlank by remember { mutableStateOf(false) }
    var printPhoto by remember { mutableStateOf<Photo?>(null) }
    var address by remember { mutableStateOf(context.getSharedPreferences("printer", 0).getString("address", null)) }
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var permissionError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { library.import(it) }
    fun showPrinters() {
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            devices = adapter?.bondedDevices.orEmpty().filter { it.name?.startsWith("INSTAX", true) == true && it.name?.contains("IOS", true) != true }.map { (it.name ?: context.getString(R.string.instax_printer)) to it.address }
            printers = true
        } catch (_: SecurityException) { permissionError = true }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) showPrinters() else permissionError = true }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("WideLight") }, actions = { UpdateButton(); TextButton(onClick = { help = true }) { Text(stringResource(R.string.photo_help)) } })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(library.message, style = MaterialTheme.typography.bodySmall)
                if (library.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Button(enabled = !library.busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_photos)) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(R.string.good_photos_nreal_prints), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.your_photos_ready_for_instax_wide), modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(enabled = !library.busy, onClick = {
                    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) else showPrinters()
                }) { Text(if (address == null) stringResource(R.string.choose_printer) else stringResource(R.string.printer_selected_change)) }
                address?.let { selected ->
                    Text(if (library.filmAddress == selected && library.filmRemaining != null)
                        stringResource(R.string.film_count, library.filmRemaining!!) else stringResource(R.string.film_unknown))
                    TextButton(enabled = !library.busy, onClick = { library.refreshFilm(selected) }) { Text(stringResource(R.string.refresh_film)) }
                }
                Button(enabled = !library.busy, onClick = { library.compose() }) { Text(stringResource(R.string.create_collage_designed_print)) }
                PrintColorControls(library)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !favorites, onClick = { favorites = false }, label = { Text(stringResource(R.string.queue_count, library.photos.size)) })
                    FilterChip(selected = favorites, onClick = { favorites = true }, label = { Text(stringResource(R.string.saved_favorites)) })
                }
            }
            if (library.photos.isEmpty()) item {
                Card { Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.start_with_your_favorites), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.tap_add_photos_then_albums_favorites_enable), Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.you_can_also_select_photos_inside_any), Modifier.padding(top = 12.dp))
                } }
            }
            items(library.photos.filter { !favorites || it.favorite }, key = { it.id }) { photo ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(photo.localizedTitle(), style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = !library.busy, onClick = { library.compose(photo) }) { Text(stringResource(R.string.design_frame_caption_qr)) }
                        var bitmap by remember(photo.id, photo.turns, photo.fill, library.printColor) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        var previewBlank by remember(photo.id, photo.turns, photo.fill) { mutableStateOf(photo.uncovered || !photo.fill) }
                        var failed by remember(photo.id) { mutableStateOf(false) }
                        LaunchedEffect(photo.id, photo.turns, photo.fill, library.printColor) {
                            val color = library.printColor
                            runCatching { withContext(Dispatchers.IO) {
                                val blank = Photos.hasBlankSpace(photo)
                                val result = Photos.render(photo, 630, 420, color)
                                withContext(Dispatchers.Main) { previewBlank = blank }
                                result
                            } }.onSuccess { bitmap = it }.onFailure { failed = true }
                        }
                        bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.print_preview), Modifier.fillMaxWidth().aspectRatio(1.5f).background(Color.White)) }
                        if (failed) Text(stringResource(R.string.preview_unavailable_remove_this_photo_and_add))
                        if (previewBlank) Text(stringResource(R.string.blank_warning), color = MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !photo.fill, enabled = !library.busy, onClick = { library.edit(photo.edited(fill = !photo.fill)) }, label = { Text(if (photo.fill) stringResource(R.string.fill_crops_edges) else stringResource(R.string.fit_whole_photo)) })
                            TextButton(enabled = !library.busy, onClick = { library.edit(photo.edited(turns = (photo.turns + 1) % 4)) }) { Text(stringResource(R.string.rotate)) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !library.busy, onClick = { library.edit(photo.edited(favorite = !photo.favorite)) }) { Text(if (photo.favorite) stringResource(R.string.saved) else stringResource(R.string.save)) }
                            TextButton(enabled = !library.busy, onClick = { library.remove(photo) }) { Text(stringResource(R.string.remove)) }
                            Button(enabled = !library.busy && address != null && bitmap != null, onClick = { printBlank = previewBlank; printPhoto = photo }) { Text(stringResource(R.string.print)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.albums_favorites)) }, text = { Text(stringResource(R.string.in_the_android_photo_picker_open_the)) }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.got_it)) } })
    if (printers) AlertDialog(onDismissRequest = { printers = false }, title = { Text(stringResource(R.string.choose_your_link_wide)) }, text = { Column {
        Text(stringResource(R.string.pair_the_original_link_wide_in_android))
        devices.forEach { (name, mac) -> Text(name, Modifier.fillMaxWidth().clickable {
            address = mac; context.getSharedPreferences("printer", 0).edit().putString("address", mac).apply(); printers = false; library.refreshFilm(mac)
        }.padding(vertical = 16.dp)) }
        if (devices.isEmpty()) Text(stringResource(R.string.no_paired_instax_android_endpoint_found), Modifier.padding(top = 16.dp))
    } }, confirmButton = { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); printers = false }) { Text(stringResource(R.string.bluetooth_settings)) } }, dismissButton = { TextButton(onClick = { printers = false }) { Text(stringResource(R.string.close)) } })
    if (permissionError) AlertDialog(onDismissRequest = { permissionError = false }, title = { Text(stringResource(R.string.nearby_devices_access_needed)) }, text = { Text(stringResource(R.string.allow_nearby_devices_in_android_app_settings)) }, confirmButton = { TextButton(onClick = { permissionError = false }) { Text(stringResource(R.string.ok)) } })
    printPhoto?.let { photo -> AlertDialog(onDismissRequest = { printPhoto = null }, title = { Text(stringResource(R.string.print_one_photo)) }, text = { Column { Text(stringResource(R.string.this_uses_one_sheet_of_wide_film)); if (printBlank) Text(stringResource(R.string.blank_warning), color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton(onClick = { printPhoto = null; address?.let { library.print(it, photo) } }) { Text(stringResource(if (printBlank) R.string.confirm_blank_print else R.string.print_1_sheet)) } }, dismissButton = { TextButton(onClick = { printPhoto = null }) { Text(stringResource(R.string.cancel)) } }) }
}
