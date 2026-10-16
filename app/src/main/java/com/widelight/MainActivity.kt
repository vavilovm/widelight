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
    var message by mutableStateOf("Ready when you are"); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    private val context get() = getApplication<Application>()
    private val index get() = File(context.filesDir, "library.json")
    init {
        runCatching {
            val a = JSONArray(index.takeIf { it.exists() }?.readText() ?: "[]")
            photos = (0 until a.length()).map { a.getJSONObject(it) }.map {
                val id = it.getString("id")
                Photo(id, File(context.filesDir, "$id.photo"), "Photo", it.optBoolean("favorite"), it.optInt("turns"), it.optBoolean("fill"))
            }.filter { it.file.exists() }
        }.onFailure { message = "Saved library could not be restored" }
    }
    private fun save() {
        val data = JSONArray()
        photos.forEach { data.put(JSONObject().put("id", it.id).put("favorite", it.favorite).put("turns", it.turns).put("fill", it.fill)) }
        val temp = File(context.filesDir, "library.tmp")
        temp.writeText(data.toString())
        check(temp.renameTo(index)) { "Cannot save library" }
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
                    imported++
                } catch (_: Exception) { failed++ }
            }
            message = "Added $imported photos" + if (failed > 0) "; $failed could not be opened" else ""
            busy = false
        }
    }
    fun print(address: String, photo: Photo) {
        if (busy) return
        busy = true; progress = 0f; message = "Connecting to printer…"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: error("Bluetooth is unavailable")
                    check(adapter.isEnabled) { "Turn on Bluetooth first" }
                    Printer(adapter.getRemoteDevice(address)).use { printer ->
                        printer.connect()
                        val caps = printer.capabilities()
                        val status = printer.status()
                        withContext(Dispatchers.Main) { message = "$status · Preparing photo…" }
                        val jpeg = Photos.jpeg(photo, caps)
                        withContext(Dispatchers.Main) { message = "Sending photo…" }
                        printer.print(jpeg) { value -> viewModelScope.launch { progress = value } }
                    }
                }
                message = "Print accepted. Wait for the film to eject before printing again."
            } catch (e: Exception) {
                message = "${e.message ?: "Connection failed"}. If transfer started, check the printer before retrying."
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(library: Library) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var favorites by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var printers by remember { mutableStateOf(false) }
    var printPhoto by remember { mutableStateOf<Photo?>(null) }
    var address by remember { mutableStateOf(context.getSharedPreferences("printer", 0).getString("address", null)) }
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var permissionError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { library.import(it) }
    fun showPrinters() {
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            devices = adapter?.bondedDevices.orEmpty().filter { it.name?.startsWith("INSTAX", true) == true && it.name?.contains("IOS", true) != true }.map { (it.name ?: "Instax printer") to it.address }
            printers = true
        } catch (_: SecurityException) { permissionError = true }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) showPrinters() else permissionError = true }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("WideLight") }, actions = { TextButton(onClick = { help = true }) { Text("Photo help") } })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(library.message, style = MaterialTheme.typography.bodySmall)
                if (library.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Button(enabled = !library.busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text("Add photos") }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Good photos.\nReal prints.", style = MaterialTheme.typography.headlineLarge)
                Text("Your photos, ready for Instax WIDE.", modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(enabled = !library.busy, onClick = {
                    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) else showPrinters()
                }) { Text(if (address == null) "Choose printer" else "Printer selected · Change") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !favorites, onClick = { favorites = false }, label = { Text("Print queue · ${library.photos.size}") })
                    FilterChip(selected = favorites, onClick = { favorites = true }, label = { Text("Saved favorites") })
                }
            }
            if (library.photos.isEmpty()) item {
                Card { Column(Modifier.padding(20.dp)) {
                    Text("Start with your favorites", style = MaterialTheme.typography.titleLarge)
                    Text("Tap Add photos, then Albums → Favorites. Enable Google Photos in the picker's cloud media settings to include backed-up photos.", Modifier.padding(top = 12.dp))
                    Text("You can also select photos inside any Google Photos album and share them to WideLight.", Modifier.padding(top = 12.dp))
                } }
            }
            items(library.photos.filter { !favorites || it.favorite }, key = { it.id }) { photo ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        var bitmap by remember(photo.id, photo.turns, photo.fill) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        var failed by remember(photo.id) { mutableStateOf(false) }
                        LaunchedEffect(photo.id, photo.turns, photo.fill) {
                            runCatching { withContext(Dispatchers.IO) { Photos.render(photo, 630, 420) } }.onSuccess { bitmap = it }.onFailure { failed = true }
                        }
                        bitmap?.let { Image(it.asImageBitmap(), "Print preview", Modifier.fillMaxWidth().aspectRatio(1.5f).background(Color.White)) }
                        if (failed) Text("Preview unavailable. Remove this photo and add it again.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !photo.fill, enabled = !library.busy, onClick = { library.edit(photo.edited(fill = !photo.fill)) }, label = { Text(if (photo.fill) "Fill · crops edges" else "Fit · whole photo") })
                            TextButton(enabled = !library.busy, onClick = { library.edit(photo.edited(turns = (photo.turns + 1) % 4)) }) { Text("Rotate") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !library.busy, onClick = { library.edit(photo.edited(favorite = !photo.favorite)) }) { Text(if (photo.favorite) "★ Saved" else "☆ Save") }
                            TextButton(enabled = !library.busy, onClick = { library.remove(photo) }) { Text("Remove") }
                            Spacer(Modifier.weight(1f))
                            Button(enabled = !library.busy && address != null && bitmap != null, onClick = { printPhoto = photo }) { Text("Print") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Albums & Favorites") }, text = { Text("In the Android photo picker, open the menu → Settings → Cloud media app → Google Photos. Allow photo picker access when prompted. Albums and Favorites depend on your Android version, Google Photos account, and provider support.\n\nFor the full Google Photos browsing and search experience, select photos there and use Share → WideLight.\n\nSaved favorites here are private bookmarks in WideLight; they do not change your Google Photos favorites.") }, confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } })
    if (printers) AlertDialog(onDismissRequest = { printers = false }, title = { Text("Choose your Link WIDE") }, text = { Column {
        Text("Pair the original Link WIDE in Android Bluetooth settings first. Close the Fujifilm app before connecting.")
        devices.forEach { (name, mac) -> Text(name, Modifier.fillMaxWidth().clickable {
            address = mac; context.getSharedPreferences("printer", 0).edit().putString("address", mac).apply(); printers = false
        }.padding(vertical = 16.dp)) }
        if (devices.isEmpty()) Text("No paired Instax Android endpoint found.", Modifier.padding(top = 16.dp))
    } }, confirmButton = { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); printers = false }) { Text("Bluetooth settings") } }, dismissButton = { TextButton(onClick = { printers = false }) { Text("Close") } })
    if (permissionError) AlertDialog(onDismissRequest = { permissionError = false }, title = { Text("Nearby devices access needed") }, text = { Text("Allow Nearby devices in Android app settings to connect to your printer.") }, confirmButton = { TextButton(onClick = { permissionError = false }) { Text("OK") } })
    printPhoto?.let { photo -> AlertDialog(onDismissRequest = { printPhoto = null }, title = { Text("Print one photo?") }, text = { Text("This uses one sheet of WIDE film. Physical film may trim the edges slightly. Keep WideLight open while sending.") }, confirmButton = { TextButton(onClick = { printPhoto = null; address?.let { library.print(it, photo) } }) { Text("Print 1 sheet") } }, dismissButton = { TextButton(onClick = { printPhoto = null }) { Text("Cancel") } }) }
}
