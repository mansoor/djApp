@file:OptIn(ExperimentalMaterial3Api::class)

package com.mansoor.djapp

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
                    selected = s.style == TransitionStyle.OVERLAP,
                    onClick = { update(s.copy(style = TransitionStyle.OVERLAP)) },
                    label = { Text("Overlap (crossfade)") },
                )
                FilterChip(
                    selected = s.style == TransitionStyle.DJ_FADE,
                    onClick = { update(s.copy(style = TransitionStyle.DJ_FADE)) },
                    label = { Text("Dip fade") },
                )
            }
            if (s.style == TransitionStyle.OVERLAP) {
                Hint(
                    "Both songs play at the same time: the current one fades out while the next fades in. " +
                        "At the mix point DJ Mix jumps the current song to its outro and Spotify's crossfade " +
                        "blends it into the next track."
                )
                val cf = engine.crossfadeMs
                Text(
                    when {
                        cf > 0 -> "Overlap length: ${cf / 1000}s (Spotify's Crossfade setting)"
                        cf == 0 -> "Spotify Crossfade is OFF: turn it on to overlap"
                        else -> "Overlap length: set by Spotify's Crossfade setting"
                    },
                    color = if (cf == 0) MaterialTheme.colorScheme.error else Green,
                )
                Hint("Change the overlap length in Spotify > Settings > Playback > Crossfade (1-12 s). Apps can't set it.")
                TextButton(onClick = { openSpotify(ctx) }) { Text("Open Spotify") }
            } else {
                Hint("The app fades the song down, drops the next one in quietly and fades it up.")
                SliderRow("Fade out", s.fadeOutSec, 1..15, "s") { update(s.copy(fadeOutSec = it)) }
                SliderRow("Fade in", s.fadeInSec, 0..10, "s") { update(s.copy(fadeInSec = it)) }
                SliderRow("Volume dip during switch", s.floorPct, 0..60, "%") { update(s.copy(floorPct = it)) }
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
            Spacer(Modifier.height(24.dp))
        }
    }
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
