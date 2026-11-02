@file:OptIn(ExperimentalMaterial3Api::class)

package com.mansoor.djapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by AppState.settings.collectAsState()
    val engine by DjEngine.state.collectAsState()
    val ctx = LocalContext.current
    val update: (MixSettings) -> Unit = { AppState.updateSettings(it) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            update(AppState.settings.value.copy(smartSwitch = true))
        } else {
            Toast.makeText(ctx, "Smart switch needs microphone access to analyse the music", Toast.LENGTH_LONG).show()
        }
    }

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
            Section("Transition")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.style == TransitionStyle.DJ_FADE,
                    onClick = { update(s.copy(style = TransitionStyle.DJ_FADE)) },
                    label = { Text("Smooth fade") },
                )
                FilterChip(
                    selected = s.style == TransitionStyle.OVERLAP,
                    onClick = { update(s.copy(style = TransitionStyle.OVERLAP)) },
                    label = { Text("Outro crossfade") },
                )
            }
            if (s.style == TransitionStyle.OVERLAP) {
                Hint(
                    "Experimental. Spotify can only blend the last seconds of a song into the next one, so at " +
                        "the mix point DJ Mix jumps the song to its outro and lets Spotify's crossfade take over. " +
                        "It only sounds right if Spotify's own crossfade works on your phone; otherwise use Smooth fade."
                )
                Hint(
                    "1. In Spotify open Settings > Playback and turn on Crossfade (1-12 s). Apps can't change it.\n" +
                        "2. Set the same length here. Spotify doesn't reliably tell apps this setting."
                )
                TextButton(onClick = { openSpotify(ctx) }) { Text("Open Spotify") }
                Column {
                    Row {
                        Text("Crossfade length set in Spotify", Modifier.weight(1f))
                        Text(if (s.crossfadeSec == 0) "Auto" else "${s.crossfadeSec}s", color = Green)
                    }
                    Slider(
                        value = s.crossfadeSec.toFloat(),
                        onValueChange = { update(s.copy(crossfadeSec = it.roundToInt())) },
                        valueRange = 0f..12f,
                        steps = 11,
                    )
                }
                val detected = engine.detectedCrossfadeMs
                Hint(
                    "Auto uses what Spotify reports: " + when {
                        detected > 0 -> "${detected / 1000}s"
                        detected == 0 -> "off (often wrong)"
                        else -> "unknown"
                    } + ". Overlap needs a length above 0."
                )
            } else {
                Hint(
                    "At the mix point the song fades down from wherever it is (no jumping). The next song " +
                        "starts underneath at low volume and rises. Spotify plays one song at a time, so the two " +
                        "never sound together, but the handover is quick."
                )
                SliderRow("Fade out", s.fadeOutSec, 1..15, "s") { update(s.copy(fadeOutSec = it)) }
                SliderRow("Fade in", s.fadeInSec, 0..10, "s") { update(s.copy(fadeInSec = it)) }
                SliderRow("Lowest volume at the switch", s.floorPct, 0..60, "%") { update(s.copy(floorPct = it)) }
                Hint("Higher keeps more energy through the switch; 0% fades to silence.")
                SliderRow("Skip intro of next track", s.skipIntroSec, 0..30, "s") { update(s.copy(skipIntroSec = it)) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("When to mix (auto mode)")
            SwitchRow("Random play length", s.randomLength) { update(s.copy(randomLength = it)) }
            if (s.randomLength) {
                Hint("Each song mixes out at a random point between these parts of its length. Drag the purple marker on the deck to change it for the current song.")
                SliderRow("Shortest", s.minPlayPct, 20..95, "%") { update(s.copy(minPlayPct = minOf(it, s.maxPlayPct))) }
                SliderRow("Longest", s.maxPlayPct, 20..100, "%") { update(s.copy(maxPlayPct = maxOf(it, s.minPlayPct))) }
            }
            SliderRow("Mix out before the end", s.mixOutEarlySec, 0..45, "s") { update(s.copy(mixOutEarlySec = it)) }
            Hint("Cuts long outros: the latest possible mix point moves this much earlier.")

            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Smart switch (beta)")
                    Hint(
                        "Listens to the music around the mix point and switches at a break, when the voice band " +
                            "goes quiet (often where there are no vocals). Spotify doesn't give apps audio analysis, " +
                            "so this is a best guess. It needs microphone permission to read the phone's audio " +
                            "output. Nothing is recorded or stored."
                    )
                }
                Switch(
                    checked = s.smartSwitch,
                    onCheckedChange = { on ->
                        if (!on) {
                            update(s.copy(smartSwitch = false))
                        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            update(s.copy(smartSwitch = true))
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Shuffle")
            SwitchRow("Avoid the same artist back to back", s.avoidSameArtist) { update(s.copy(avoidSameArtist = it)) }
            SwitchRow("Alternate between playlists", s.alternatePlaylists) { update(s.copy(alternatePlaylists = it)) }
            SliderRow("Block size for playlists you don't own", s.blockSize, 1..10, " tracks") { update(s.copy(blockSize = it)) }
            Hint(
                "Spotify only shares the songs of playlists you own or collaborate on, so other playlists are " +
                    "played as blocks of songs in between."
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { update(MixSettings(mode = s.mode)) }) { Text("Reset to defaults") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            AboutSection(engine)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** App, SDK, device and Spotify account details, with a copy button for bug reports. */
@Composable
private fun AboutSection(engine: DjEngine.State) {
    val ctx = LocalContext.current
    val profile by AppState.profile.collectAsState()
    val clipboard = LocalClipboardManager.current
    val rows = remember(profile, engine.connected, engine.detectedCrossfadeMs) {
        val pm = ctx.packageManager
        val app = runCatching { pm.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val spotify = runCatching { pm.getPackageInfo("com.spotify.music", 0) }.getOrNull()
        val clientId = Prefs.clientId
        listOf(
            "App version" to (app?.let { "${it.versionName} (build ${PackageInfoCompat.getLongVersionCode(it)})" } ?: "unknown"),
            "Spotify account" to (profile?.let { "${it.name} (${it.id})" } ?: "not loaded yet"),
            "Spotify app" to (spotify?.versionName?.let { "installed, version $it" } ?: "not installed"),
            "Spotify app connection" to (if (engine.connected) "connected" else "not connected"),
            "Spotify App Remote SDK" to "0.8.0",
            "Spotify Web API" to "v1, Authorization Code + PKCE",
            "Spotify crossfade (reported)" to when {
                engine.detectedCrossfadeMs > 0 -> "${engine.detectedCrossfadeMs / 1000}s"
                engine.detectedCrossfadeMs == 0 -> "off"
                else -> "unknown"
            },
            "Client ID" to (if (clientId.length > 8) clientId.take(4) + "…" + clientId.takeLast(4) else clientId.ifBlank { "not set" }),
            "Redirect URI" to SpotifyAuth.REDIRECT_URI,
            "Package" to ctx.packageName,
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), target API ${ctx.applicationInfo.targetSdkVersion}",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }
    Section("About")
    rows.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(label, Modifier.weight(0.45f), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(value, Modifier.weight(0.55f), style = MaterialTheme.typography.bodySmall)
        }
    }
    TextButton(onClick = {
        clipboard.setText(AnnotatedString(rows.joinToString("\n") { "${it.first}: ${it.second}" }))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }) { Text("Copy info") }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text("$value$unit", color = Green)
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
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
