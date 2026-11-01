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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.security.MessageDigest
import kotlin.math.roundToInt

private val Green = Color(0xFF1ED760)

private val DjColors = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    secondary = Color(0xFFB388FF),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF242424),
)

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

// ------------------------------------------------------------------ main

@Composable
private fun MainScreen(activity: ComponentActivity) {
    var screen by rememberSaveable { mutableStateOf("library") }
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
    BackHandler(enabled = screen != "library") { screen = "library" }

    val startMix: () -> Unit = {
        val sel = AppState.selected.value
        DjEngine.start(playlists.filter { it.id in sel })
        screen = "player"
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

    when (screen) {
        "settings" -> SettingsScreen(onBack = { screen = if (engine.running) "player" else "library" })
        "player" -> PlayerScreen(engine, onBack = { screen = "library" }, onSettings = { screen = "settings" })
        else -> LibraryScreen(activity, engine, snackbar, requestStart, onOpenPlayer = { screen = "player" }, onSettings = { screen = "settings" })
    }
}

@Composable
private fun LibraryScreen(
    activity: ComponentActivity,
    engine: DjEngine.State,
    snackbar: SnackbarHostState,
    onStart: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSettings: () -> Unit,
) {
    val playlists by AppState.playlists.collectAsState()
    val loading by AppState.loadingPlaylists.collectAsState()
    val selected by AppState.selected.collectAsState()
    val settings by AppState.settings.collectAsState()

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(Modifier.weight(1f))
                    val count = playlists.count { it.id in selected }
                    Button(onClick = onStart, enabled = count > 0 && engine.connected && !engine.loading) {
                        if (engine.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Text(if (engine.running) "Restart mix ($count)" else "Start mix ($count)")
                        }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { ConnectionBanner(activity, engine) }
            if (loading && playlists.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            items(playlists, key = { it.id }) { p ->
                PlaylistRow(p, p.id in selected) { AppState.toggle(p.id) }
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

/** Brings Spotify to the foreground so it can wake up / show its approval screen. */
private fun openSpotify(activity: ComponentActivity) {
    val launch = activity.packageManager.getLaunchIntentForPackage("com.spotify.music")
        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("spotify:"))
    try {
        activity.startActivity(launch)
    } catch (e: Exception) {
        AppState.message.value = "Couldn't open Spotify: ${e.message}"
    }
}

@Composable
private fun PlaylistRow(p: PlaylistInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
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
                append("${p.total} tracks · ${p.owner}")
                if (!p.readable) append(" · plays in blocks")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
        }
    }
}

@Composable
private fun MiniPlayer(engine: DjEngine.State, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E2E2E)).clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        engine.art?.let {
            Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
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

// ------------------------------------------------------------------ player

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PlayerScreen(engine: DjEngine.State, onBack: () -> Unit, onSettings: () -> Unit) {
    val settings by AppState.settings.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (engine.running) "Now mixing" else "Player") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = engine.art
                    if (art != null) {
                        Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = Color.Gray)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    engine.trackName.ifBlank { if (engine.loading) "Loading tracks…" else "Nothing playing" },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(engine.artist, color = Color.LightGray, maxLines = 1)
                if (engine.source.isNotBlank()) {
                    Text("from ${engine.source}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                MixProgress(engine)
                Spacer(Modifier.height(4.dp))
                val phaseText = when (engine.phase) {
                    DjEngine.Phase.FADING_OUT, DjEngine.Phase.SWITCHING, DjEngine.Phase.FADING_IN -> engine.status
                    else -> engine.status.ifBlank {
                        if (engine.running && engine.mixPointMs > 0) "Next mix at ${fmt(engine.mixPointMs)}" else ""
                    }
                }
                Text(
                    phaseText,
                    color = if (engine.phase == DjEngine.Phase.PLAYING || engine.phase == DjEngine.Phase.IDLE) Color.Gray else Green,
                    style = MaterialTheme.typography.bodySmall,
                )
                engine.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { DjEngine.previous() }, enabled = engine.running) {
                        Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(32.dp))
                    }
                    FilledIconButton(
                        onClick = { DjEngine.togglePause() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black),
                    ) {
                        Icon(if (engine.paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Play/pause", Modifier.size(40.dp))
                    }
                    IconButton(onClick = { DjEngine.next() }, enabled = engine.running) {
                        Icon(Icons.Default.SkipNext, "Mix next", Modifier.size(32.dp))
                    }
                    IconButton(onClick = { DjEngine.stop() }, enabled = engine.running) {
                        Icon(Icons.Default.Stop, "Stop", Modifier.size(32.dp))
                    }
                }
                Text(
                    when (settings.style) {
                        TransitionStyle.DJ_FADE -> "DJ fade · ${settings.fadeOutSec}s out / ${settings.fadeInSec}s in"
                        TransitionStyle.SPOTIFY_CROSSFADE -> "Spotify crossfade mode" + (engine.crossfadeInfo?.let { " · $it" } ?: "")
                    } + " · " + if (settings.mode == PlayMode.SHUFFLE) "shuffle" else "in order",
                    style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                )
                Spacer(Modifier.height(16.dp))
                if (engine.upNext.isNotEmpty()) {
                    Text("Up next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
            }
            itemsIndexed(engine.upNext) { i, label ->
                Text(
                    label,
                    Modifier
                        .fillMaxWidth()
                        .clickable { DjEngine.playUpcoming(i) }
                        .padding(vertical = 10.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MixProgress(engine: DjEngine.State) {
    val dur = engine.durationMs.coerceAtLeast(1)
    val progress = (engine.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val mix = (engine.mixPointMs.toFloat() / dur).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(6.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Green,
                trackColor = Color(0xFF3A3A3A),
            )
            if (engine.running && mix > 0f && mix < 1f) {
                // Marker for where the DJ starts the transition.
                Row(Modifier.fillMaxWidth()) {
                    if (mix > 0.001f) Spacer(Modifier.weight(mix))
                    Box(Modifier.width(3.dp).height(6.dp).background(Color(0xFFB388FF)))
                    Spacer(Modifier.weight((1f - mix).coerceAtLeast(0.001f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(engine.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Text(fmt(engine.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val s by AppState.settings.collectAsState()
    val update: (MixSettings) -> Unit = { AppState.updateSettings(it) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mix settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Transition style", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.style == TransitionStyle.DJ_FADE,
                    onClick = { update(s.copy(style = TransitionStyle.DJ_FADE)) },
                    label = { Text("DJ fade") },
                )
                FilterChip(
                    selected = s.style == TransitionStyle.SPOTIFY_CROSSFADE,
                    onClick = { update(s.copy(style = TransitionStyle.SPOTIFY_CROSSFADE)) },
                    label = { Text("Spotify crossfade") },
                )
            }
            Text(
                if (s.style == TransitionStyle.DJ_FADE) {
                    "The app rides the volume: it fades the track out at the mix point, drops the next track and " +
                        "fades it in. Works with any Spotify setting."
                } else {
                    "The app queues the next track in Spotify so Spotify overlaps them (true crossfade). Turn on " +
                        "Spotify > Settings > Playback > Crossfade (e.g. 8-12s). Intro skip doesn't apply in this mode."
                },
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SliderRow("Fade out", s.fadeOutSec, 1..15, "s") { update(s.copy(fadeOutSec = it)) }
            SliderRow("Fade in", s.fadeInSec, 0..10, "s") { update(s.copy(fadeInSec = it)) }
            SliderRow("Volume dip during switch", s.floorPct, 0..60, "%") { update(s.copy(floorPct = it)) }
            SliderRow("Mix out before the end", s.mixOutEarlySec, 0..45, "s") { update(s.copy(mixOutEarlySec = it)) }
            Text("Cuts long outros: the transition starts this much earlier.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            SliderRow("Skip intro of next track", s.skipIntroSec, 0..30, "s") { update(s.copy(skipIntroSec = it)) }
            Text("Starts the next track a few seconds in, like a DJ cueing past the intro.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Shuffle", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Avoid the same artist back to back", s.avoidSameArtist) { update(s.copy(avoidSameArtist = it)) }
            SwitchRow("Alternate between playlists", s.alternatePlaylists) { update(s.copy(alternatePlaylists = it)) }
            SliderRow("Block size for playlists you don't own", s.blockSize, 1..10, " tracks") { update(s.copy(blockSize = it)) }
            Text(
                "Spotify only shares the tracks of playlists you own or collaborate on, so other playlists are " +
                    "played as blocks of tracks in between.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { update(MixSettings(mode = s.mode)) }) { Text("Reset to defaults") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text("$value$unit", color = Green)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
