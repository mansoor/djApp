@file:OptIn(ExperimentalMaterial3Api::class)

package com.mansoor.djapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Search the whole Spotify catalog: songs to play or queue, playlists to add to the mix. */
@Composable
fun SearchScreen(engine: DjEngine.State, onBack: () -> Unit, onOpenPlaylist: (PlaylistInfo) -> Unit) {
    val lastQuery by AppState.searchQuery.collectAsState()
    val tracks by AppState.searchTracks.collectAsState()
    val playlists by AppState.searchPlaylists.collectAsState()
    val loading by AppState.searchLoading.collectAsState()
    val hasMore by AppState.searchHasMore.collectAsState()
    val selected by AppState.selected.collectAsState()
    val custom by AppState.customQueue.collectAsState()
    var query by rememberSaveable { mutableStateOf(lastQuery) }
    var showSongs by rememberSaveable { mutableStateOf(true) }
    val customUris = remember(custom) { custom.map { it.uri }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Spotify") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Song, artist or playlist") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { AppState.search(query) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showSongs, onClick = { showSongs = true }, label = { Text("Songs (${tracks.size})") })
                FilterChip(selected = !showSongs, onClick = { showSongs = false }, label = { Text("Playlists (${playlists.size})") })
            }
            Text(
                if (showSongs) {
                    if (engine.running) "Play mixes into the song now · Next plays it after this one · +Queue adds it to the end"
                    else "Add songs to your Custom queue, or start a mix to play them straight away."
                } else {
                    "Tick a playlist to add it to your mix. Spotify only shares the songs of playlists you own, " +
                        "so others play in blocks."
                },
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                if (showSongs) {
                    items(tracks, key = { it.uri }) { t ->
                        TrackActionRow(t, engine.running, t.uri in customUris)
                    }
                } else {
                    items(playlists, key = { it.id }) { p ->
                        PlaylistRow(p, p.id in selected, onToggle = { AppState.toggleSelect(p) }, onOpen = { onOpenPlaylist(p) })
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator()
                            lastQuery.isNotBlank() && tracks.isEmpty() && playlists.isEmpty() ->
                                Text("Nothing found for \"$lastQuery\"", color = Color.Gray)
                            hasMore -> OutlinedButton(onClick = { AppState.search(lastQuery, more = true) }) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
}
