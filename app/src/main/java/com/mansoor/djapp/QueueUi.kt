@file:OptIn(ExperimentalMaterial3Api::class)

package com.mansoor.djapp

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val CompactPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)

/**
 * While a mix runs: everything coming up, with search, jump, reorder and remove.
 * Before a mix: the hand-picked custom queue.
 */
@Composable
fun QueueScreen(engine: DjEngine.State, onBack: () -> Unit, onBrowse: () -> Unit) {
    val custom by AppState.customQueue.collectAsState()
    var query by remember { mutableStateOf("") }
    val running = engine.running
    val labels: List<String> = remember(engine.queueVersion, running, custom) {
        if (running) DjEngine.upcoming().map { it.label() }
        else custom.map { "${it.name} · ${it.artist}" }
    }
    val shown = labels.withIndex().filter { query.isBlank() || it.value.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (running) "Up next (${labels.size})" else "Custom queue (${labels.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!running && custom.isNotEmpty()) TextButton(onClick = { AppState.clearCustom() }) { Text("Clear") }
                    IconButton(onClick = onBrowse) { Icon(Icons.Default.Add, "Add songs from playlists") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Find a song in the queue") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                if (running) "▶ mixes into a song now. Arrows reorder. Tap + to add songs from any playlist."
                else "These play first when you start a Custom mix. Tap + to add songs from your playlists.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (labels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing queued yet", color = Color.Gray)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { "${it.index}:${it.value}" }) { (i, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", color = Color.Gray, modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
                        Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (running) {
                            IconButton(onClick = { DjEngine.jumpToUpcoming(i) }) { Icon(Icons.Default.PlayArrow, "Mix into this now", tint = Green) }
                        }
                        IconButton(onClick = {
                            if (running) DjEngine.moveUpcoming(i, i - 1) else AppState.moveCustom(i, i - 1)
                        }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = {
                            if (running) DjEngine.moveUpcoming(i, i + 1) else AppState.moveCustom(i, i + 1)
                        }, enabled = i < labels.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = {
                            if (running) DjEngine.removeUpcoming(i) else AppState.removeCustom(i)
                        }) { Icon(Icons.Default.Close, "Remove") }
                    }
                    HorizontalDivider(color = Color(0xFF242424))
                }
            }
        }
    }
}

/** A playlist's songs: play any of them now, next, or add them to the queue. */
@Composable
fun TracksScreen(p: PlaylistInfo, engine: DjEngine.State, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cache by AppState.trackCache.collectAsState()
    val custom by AppState.customQueue.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(p.id) { AppState.ensureTracks(p) }
    val loaded = cache.containsKey(p.id)
    val tracks = cache[p.id]
    val shown = tracks?.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    } ?: emptyList()
    val customUris = remember(custom) { custom.map { it.uri }.toSet() }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                tracks == null -> Text(
                    "Spotify doesn't share the songs of playlists you don't own, so they can't be picked one by one. " +
                        "Tick it in the library to play it in blocks, or copy its songs into a playlist of your own.",
                    modifier = Modifier.padding(20.dp),
                )
                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search ${tracks.size} songs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Text(
                        if (engine.running) "Play mixes into the song now · Next plays it after this one · +Queue adds it to the end"
                        else "Add songs to your Custom queue, then start a Custom mix from the library.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.uri }) { t ->
                            TrackActionRow(t, engine.running, t.uri in customUris)
                        }
                    }
                }
            }
        }
    }
}

/** A song with DJ actions: Play now / Next / +Queue while mixing, or + Custom before a mix. */
@Composable
fun TrackActionRow(t: TrackInfo, running: Boolean, inCustom: Boolean) {
    val ctx = LocalContext.current
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = running) { DjEngine.playNow(t); toast("Mixing into ${t.name}") }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                "${t.artist} · ${fmt(t.durationMs)}",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1,
            )
        }
        if (running) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = { DjEngine.playNow(t); toast("Mixing into ${t.name}") }, contentPadding = CompactPadding) { Text("Play") }
                TextButton(onClick = { DjEngine.playNext(t); toast("${t.name} plays next") }, contentPadding = CompactPadding) { Text("Next") }
                TextButton(onClick = { DjEngine.addToQueue(t); toast("Added to queue") }, contentPadding = CompactPadding) { Text("+Queue") }
            }
        } else {
            TextButton(
                onClick = { AppState.addCustom(t); toast("Added to custom queue") },
                enabled = !inCustom,
            ) { Text(if (inCustom) "Added" else "+ Custom") }
        }
    }
}
