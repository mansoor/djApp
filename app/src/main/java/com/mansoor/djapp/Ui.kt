@file:OptIn(ExperimentalMaterial3Api::class)

package com.mansoor.djapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.security.MessageDigest

val Green = Color(0xFF1ED760)
val Purple = Color(0xFFB388FF)

private val DjColors = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    secondary = Purple,
    background = Color(0xFF0F0B18),
    surface = Color(0xFF0F0B18),
    surfaceVariant = Color(0xFF1D1629),
)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun DjAppUi(activity: ComponentActivity) {
    val loggedIn by AppState.loggedIn.collectAsState()
    val report by AppState.problemReport.collectAsState()
    MaterialTheme(colorScheme = DjColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!loggedIn) SetupScreen() else MainScreen(activity)
        }
        report?.let { ProblemDialog(it) }
    }
}

@Composable
private fun ProblemDialog(report: String) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { AppState.problemReport.value = null },
        title = { Text("Problem report") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Please copy this and send it to the developer.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = { AppState.problemReport.value = null }) { Text("Close") }
        },
    )
}

// ------------------------------------------------------------------ setup

private fun signingSha1(ctx: Context): String = try {
    val pm = ctx.packageManager
    val sigs = if (Build.VERSION.SDK_INT >= 28) {
        pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val sig = sigs?.firstOrNull() ?: throw IllegalStateException()
    MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
} catch (e: Exception) {
    "unknown"
}

@Composable
private fun SetupScreen() {
    val ctx = LocalContext.current
    val message by AppState.message.collectAsState()
    var clientId by rememberSaveable { mutableStateOf(Prefs.clientId) }
    val sha1 = remember { signingSha1(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DJ Mix", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Green)
        Text("Continuous, DJ-style mixing of your Spotify playlists.", color = Color.LightGray)
        Spacer(Modifier.height(4.dp))
        Text("One-time setup", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Open developer.spotify.com/dashboard and create an app.\n" +
                "2. Tick \"Web API\" and \"Android\".\n" +
                "3. Add the redirect URI, package name and SHA1 below.\n" +
                "4. Paste the app's Client ID here and tap Connect.\n" +
                "The Spotify app must be installed and logged in (Premium).",
            style = MaterialTheme.typography.bodyMedium,
        )
        CopyRow("Redirect URI", SpotifyAuth.REDIRECT_URI)
        CopyRow("Package name", ctx.packageName)
        CopyRow("SHA1 fingerprint", sha1)
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it.trim() },
            label = { Text("Spotify Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                Prefs.clientId = clientId
                ctx.startActivity(Intent(Intent.ACTION_VIEW, SpotifyAuth.authUri()))
            },
            enabled = clientId.length >= 16,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect Spotify") }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
            }
        }
    }
}

// ------------------------------------------------------------------ navigation

@Composable
private fun MainScreen(activity: ComponentActivity) {
    var stack by remember { mutableStateOf(listOf("library")) }
    val screen = stack.last()
    val push: (String) -> Unit = { s -> stack = stack + s }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    val engine by DjEngine.state.collectAsState()
    val playlists by AppState.playlists.collectAsState()
    val message by AppState.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (AppState.playlists.value.isEmpty()) AppState.refreshPlaylists()
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            AppState.message.value = null
        }
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    val startMix: () -> Unit = {
        val sel = AppState.selected.value
        DjEngine.start(AppState.selectablePlaylists().filter { it.id in sel }, AppState.customQueue.value)
        stack = listOf("library", "player")
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startMix() }
    val requestStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMix()
        }
    }

    when {
        screen == "settings" -> SettingsScreen(onBack = pop)
        screen == "player" -> PlayerScreen(engine, onBack = pop, onSettings = { push("settings") }, onQueue = { push("queue") })
        screen == "queue" -> QueueScreen(engine, onBack = pop, onBrowse = { stack = listOf("library") })
        screen == "search" -> SearchScreen(engine, onBack = pop, onOpenPlaylist = { push("tracks:${it.id}") })
        screen.startsWith("tracks:") -> {
            val p = AppState.findPlaylist(screen.removePrefix("tracks:"))
            if (p == null) pop() else TracksScreen(p, engine, onBack = pop)
        }
        else -> LibraryScreen(
            activity, engine, snackbar, requestStart,
            onOpenPlayer = { push("player") },
            onSettings = { push("settings") },
            onOpenPlaylist = { push("tracks:${it.id}") },
            onQueue = { push("queue") },
            onSearch = { q ->
                AppState.search(q)
                push("search")
            },
        )
    }
}

// ------------------------------------------------------------------ library

@Composable
private fun LibraryScreen(
    activity: ComponentActivity,
    engine: DjEngine.State,
    snackbar: SnackbarHostState,
    onStart: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSettings: () -> Unit,
    onOpenPlaylist: (PlaylistInfo) -> Unit,
    onQueue: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val playlists by AppState.playlists.collectAsState()
    val extra by AppState.extraPlaylists.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = if (query.isBlank()) playlists else playlists.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val loading by AppState.loadingPlaylists.collectAsState()
    val selected by AppState.selected.collectAsState()
    val settings by AppState.settings.collectAsState()
    val custom by AppState.customQueue.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("DJ Mix", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        AppState.problemReport.value = Diagnostics.lastReport(activity) ?: "No problem recorded yet."
                    }) { Icon(Icons.Default.BugReport, "Last problem report") }
                    IconButton(onClick = { AppState.refreshPlaylists() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { AppState.logout() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out") }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                if (engine.running) {
                    MiniPlayer(engine, onOpenPlayer)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = settings.mode == PlayMode.SHUFFLE,
                        onClick = { AppState.updateSettings(settings.copy(mode = PlayMode.SHUFFLE)) },
                        label = { Text("Shuffle") },
                    )
                    FilterChip(
                        selected = settings.mode == PlayMode.SEQUENCE,
                        onClick = { AppState.updateSettings(settings.copy(mode = PlayMode.SEQUENCE)) },
                        label = { Text("In order") },
                    )
                    FilterChip(
                        selected = settings.mode == PlayMode.CUSTOM,
                        onClick = { AppState.updateSettings(settings.copy(mode = PlayMode.CUSTOM)) },
                        label = { Text("Custom (${custom.size})") },
                    )
                }
                if (settings.mode == PlayMode.CUSTOM) {
                    Text(
                        "Custom plays your hand-picked songs first (open a playlist to add songs), then " +
                            "carries on with the selected playlists.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.mode == PlayMode.CUSTOM) {
                        TextButton(onClick = onQueue) { Text("Edit custom queue") }
                    }
                    Spacer(Modifier.weight(1f))
                    val count = (playlists + extra).count { it.id in selected }
                    val canStart = (count > 0 || (settings.mode == PlayMode.CUSTOM && custom.isNotEmpty())) &&
                        engine.connected && !engine.loading
                    Button(onClick = onStart, enabled = canStart) {
                        if (engine.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Text(if (engine.running) "Restart mix" else "Start mix")
                        }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { ConnectionBanner(activity, engine) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    },
                    placeholder = { Text("Search your playlists or Spotify") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (query.isNotBlank()) {
                item {
                    TextButton(
                        onClick = { onSearch(query) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Search Spotify for songs and playlists: \"${query.trim()}\"") }
                }
            } else {
                item {
                    Text(
                        "Tick playlists to mix. Tap a playlist to see its songs and add or play any of them.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (query.isBlank() && extra.isNotEmpty()) {
                item {
                    Text(
                        "Added from search",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                items(extra, key = { "extra:${it.id}" }) { p ->
                    PlaylistRow(p, p.id in selected, onToggle = { AppState.toggleSelect(p) }, onOpen = { onOpenPlaylist(p) })
                }
                item {
                    Text(
                        "Your playlists",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (loading && playlists.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            items(shown, key = { it.id }) { p ->
                PlaylistRow(p, p.id in selected, onToggle = { AppState.toggleSelect(p) }, onOpen = { onOpenPlaylist(p) })
            }
            if (query.isNotBlank() && shown.isEmpty()) {
                item {
                    Text(
                        "None of your playlists match.",
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(activity: ComponentActivity, engine: DjEngine.State) {
    if (engine.connected && engine.error == null) return
    val needsScope = !SpotifyAuth.hasRemoteScope()
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A12)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when {
                    engine.connected -> "Spotify"
                    needsScope -> "One more step: let DJ Mix control the Spotify app"
                    engine.connecting -> "Connecting to the Spotify app…"
                    else -> "Not connected to the Spotify app"
                },
                fontWeight = FontWeight.Bold,
            )
            if (!engine.connected && needsScope) {
                Text(
                    "Tap Re-authorize and approve in the browser. This grants playback control.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (engine.connecting) {
                Text(
                    "If a Spotify approval screen appears, tap Agree. Stuck? Tap Open Spotify, then come back.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            engine.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row {
                if (!engine.connected) {
                    if (needsScope) {
                        Button(onClick = { reauthorize(activity) }) { Text("Re-authorize") }
                    } else {
                        TextButton(onClick = { DjEngine.connect(activity) }) { Text("Connect") }
                    }
                    TextButton(onClick = { openSpotify(activity) }) { Text("Open Spotify") }
                    if (!needsScope) TextButton(onClick = { reauthorize(activity) }) { Text("Re-authorize") }
                }
                if (engine.error != null && engine.connected) TextButton(onClick = { DjEngine.clearError() }) { Text("Dismiss") }
            }
        }
    }
}

private fun reauthorize(activity: ComponentActivity) {
    activity.startActivity(Intent(Intent.ACTION_VIEW, SpotifyAuth.authUri()))
}

/** Brings Spotify to the foreground (to wake it up, or to change its settings). */
fun openSpotify(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("spotify:"))
    try {
        ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppState.message.value = "Couldn't open Spotify: ${e.message}"
    }
}

@Composable
fun PlaylistRow(p: PlaylistInfo, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF333333)),
            contentAlignment = Alignment.Center,
        ) {
            if (p.id == LIKED_ID) {
                Box(Modifier.fillMaxSize().background(Color(0xFF4A2FBD)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Favorite, null, tint = Color.White)
                }
            } else if (p.imageUrl != null) {
                AsyncImage(model = p.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.LibraryMusic, null, tint = Color.Gray)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val sub = buildString {
                append(if (p.total > 0) "${p.total} tracks · ${p.owner}" else p.owner)
                if (!p.readable) append(" · plays in blocks")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open", tint = Color.Gray)
    }
}

@Composable
private fun MiniPlayer(engine: DjEngine.State, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E2E2E)).clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        engine.art?.let {
            Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(engine.trackName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(engine.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        IconButton(onClick = { DjEngine.togglePause() }) {
            Icon(if (engine.paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Play/pause")
        }
        IconButton(onClick = { DjEngine.next() }) { Icon(Icons.Default.SkipNext, "Mix next") }
    }
}
