@file:OptIn(ExperimentalMaterial3Api::class)

package com.mansoor.djapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** One full turn of the jog wheel moves this much through the track. */
private const val JOG_MS_PER_TURN = 10_000f

@Composable
fun PlayerScreen(engine: DjEngine.State, onBack: () -> Unit, onSettings: () -> Unit, onQueue: () -> Unit) {
    val settings by AppState.settings.collectAsState()
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (engine.running) "Deck" else "Player") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onQueue, enabled = engine.running) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
                    IconButton(onClick = { DjEngine.stop() }, enabled = engine.running) { Icon(Icons.Default.Stop, "Stop mix") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Vinyl(engine)
                Spacer(Modifier.height(14.dp))
                Text(
                    engine.trackName.ifBlank { if (engine.loading) "Loading tracks…" else "Nothing playing" },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(engine.artist, color = Color.LightGray, maxLines = 1)
                if (engine.source.isNotBlank()) {
                    Text("from ${engine.source}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                MixBar(engine)
                StatusLine(engine)
                Spacer(Modifier.height(4.dp))
                Transport(engine)
                Spacer(Modifier.height(6.dp))
                Pads(engine)
                Spacer(Modifier.height(8.dp))
                TransitionInfo(engine, settings, onOpenSpotify = { openSpotify(ctx) })
                Spacer(Modifier.height(12.dp))
                if (engine.upNext.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Up next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onQueue) { Text("Queue (${engine.upcomingCount})") }
                    }
                    Text("Tap a song to mix into it now.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
            }
            itemsIndexed(engine.upNext) { i, label ->
                Text(
                    label,
                    Modifier
                        .fillMaxWidth()
                        .clickable { DjEngine.jumpToUpcoming(i) }
                        .padding(vertical = 10.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun angleOf(p: Offset, size: IntSize): Float =
    Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()

/** Spinning record with the artwork as its label. Drag it round like a jog wheel to move through the track. */
@Composable
private fun Vinyl(engine: DjEngine.State) {
    var angle by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    val spinning = !engine.paused && !dragging && engine.trackName.isNotBlank()

    LaunchedEffect(spinning) {
        if (spinning) {
            var last = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    angle = (angle + (now - last) * 0.2f) % 360f // ~33 rpm
                    last = now
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var prev = 0f
                detectDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        scrubMs = 0
                        prev = angleOf(o, size)
                    },
                    onDragEnd = {
                        dragging = false
                        DjEngine.nudge(scrubMs)
                        scrubMs = 0
                    },
                    onDragCancel = {
                        dragging = false
                        scrubMs = 0
                    },
                    onDrag = { change, _ ->
                        val a = angleOf(change.position, size)
                        var d = a - prev
                        if (d > 180) d -= 360
                        if (d < -180) d += 360
                        prev = a
                        angle = (angle + d) % 360f
                        scrubMs += (d / 360f * JOG_MS_PER_TURN).toLong()
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF0B0B0B), radius = r)
            for (i in 0 until 16) {
                drawCircle(
                    color = if (i % 3 == 0) Color(0xFF262626) else Color(0xFF181818),
                    radius = r * (0.97f - i * 0.028f),
                    style = Stroke(width = 1.2f),
                )
            }
            // A sheen that turns with the record so the spin is visible.
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -70f, sweepAngle = 35f, useCenter = true,
            )
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = 110f, sweepAngle = 35f, useCenter = true,
            )
        }
        val art = engine.art
        Box(
            Modifier
                .fillMaxSize(0.5f)
                .graphicsLayer { rotationZ = angle }
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(48.dp), tint = Color.Gray)
            }
        }
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF121212)))
        if (dragging) {
            val sign = if (scrubMs >= 0) "+" else "-"
            Text(
                "$sign${fmt(abs(scrubMs))}",
                Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Green, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Progress bar with two handles: the white playhead (drag or tap to seek) and the purple
 * mix marker (drag to choose when the next track comes in).
 */
@Composable
private fun MixBar(engine: DjEngine.State) {
    val current by rememberUpdatedState(engine)
    var dragTarget by remember { mutableIntStateOf(0) } // 0 none, 1 playhead, 2 mix marker
    var dragMs by remember { mutableLongStateOf(0L) }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val d = current.durationMs
                        if (d > 0) DjEngine.seek((o.x / size.width * d).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val e = current
                            val d = e.durationMs.coerceAtLeast(1)
                            val markerX = e.mixPointMs.toFloat() / d * size.width
                            dragTarget = if (e.running && e.mixPointMs > 0 && abs(o.x - markerX) < 28.dp.toPx()) 2 else 1
                            dragMs = (o.x.coerceIn(0f, size.width.toFloat()) / size.width * d).toLong()
                        },
                        onDrag = { change, _ ->
                            val d = current.durationMs.coerceAtLeast(1)
                            dragMs = (change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * d).toLong()
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragTarget == 2) DjEngine.setMixPoint(dragMs) else DjEngine.seek(dragMs)
                            dragTarget = 0
                        },
                        onDragCancel = { dragTarget = 0 },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val e = current
                val d = e.durationMs.coerceAtLeast(1).toFloat()
                val w = size.width
                val cy = size.height * 0.62f
                fun x(ms: Long) = (ms / d * w).coerceIn(0f, w)
                val pos = if (dragTarget == 1) dragMs else e.positionMs
                val mix = if (dragTarget == 2) dragMs else e.mixPointMs
                val thick = 6.dp.toPx()
                drawLine(Color(0xFF3A3A3A), Offset(0f, cy), Offset(w, cy), strokeWidth = thick, cap = StrokeCap.Round)
                if (e.smartEndMs > e.smartStartMs) {
                    val xs = x(e.smartStartMs)
                    drawRect(
                        Purple.copy(alpha = 0.3f),
                        topLeft = Offset(xs, cy - thick),
                        size = Size(x(e.smartEndMs) - xs, thick * 2),
                    )
                }
                if (e.loopLenMs > 0) {
                    val xs = x(e.loopStartMs)
                    drawRect(
                        Green.copy(alpha = 0.3f),
                        topLeft = Offset(xs, cy - thick),
                        size = Size(x(e.loopStartMs + e.loopLenMs) - xs, thick * 2),
                    )
                }
                drawLine(Green, Offset(0f, cy), Offset(x(pos), cy), strokeWidth = thick, cap = StrokeCap.Round)
                if (e.running && mix > 0) {
                    val mx = x(mix)
                    val top = cy - 18.dp.toPx()
                    drawLine(Purple, Offset(mx, top), Offset(mx, cy + 10.dp.toPx()), strokeWidth = 3.dp.toPx())
                    drawCircle(Purple, radius = (if (dragTarget == 2) 10 else 8).dp.toPx(), center = Offset(mx, top))
                }
                drawCircle(Color.White, radius = (if (dragTarget == 1) 10 else 7).dp.toPx(), center = Offset(x(pos), cy))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmt(if (dragTarget == 1) dragMs else engine.positionMs),
                style = MaterialTheme.typography.labelMedium, color = Color.LightGray,
            )
            Spacer(Modifier.weight(1f))
            if (engine.running && engine.mixPointMs > 0) {
                val mixAt = if (dragTarget == 2) dragMs else engine.mixPointMs
                Text(
                    "Mix at ${fmt(mixAt)}" + if (engine.mixPointManual) " · yours" else " · auto",
                    style = MaterialTheme.typography.labelMedium, color = Purple,
                )
                if (engine.mixPointManual) {
                    TextButton(onClick = { DjEngine.resetMixPoint() }) { Text("Auto", style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(fmt(engine.durationMs), style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
        }
    }
}

@Composable
private fun StatusLine(engine: DjEngine.State) {
    val busy = engine.phase != DjEngine.Phase.PLAYING && engine.phase != DjEngine.Phase.IDLE
    val text = engine.status.ifBlank { engine.smartInfo ?: "" }
    if (text.isNotBlank()) {
        Text(text, color = if (busy) Green else Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
    engine.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Transport(engine: DjEngine.State) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { DjEngine.previous() }, enabled = engine.running) {
            Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(30.dp))
        }
        IconButton(onClick = { DjEngine.nudge(-10_000) }, enabled = engine.durationMs > 0) {
            Icon(Icons.Default.Replay10, "Back 10 seconds", Modifier.size(30.dp))
        }
        FilledIconButton(
            onClick = { DjEngine.togglePause() },
            modifier = Modifier.size(68.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black),
        ) {
            Icon(if (engine.paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Play/pause", Modifier.size(38.dp))
        }
        IconButton(onClick = { DjEngine.nudge(10_000) }, enabled = engine.durationMs > 0) {
            Icon(Icons.Default.Forward10, "Forward 10 seconds", Modifier.size(30.dp))
        }
        IconButton(onClick = { DjEngine.next() }, enabled = engine.running) {
            Icon(Icons.Default.SkipNext, "Mix next now", Modifier.size(30.dp))
        }
    }
}

/** Loop length slider (1-16 s) with a Loop on/off pad, plus Repeat track. */
@Composable
private fun Pads(engine: DjEngine.State) {
    val settings by AppState.settings.collectAsState()
    val looping = engine.loopLenMs > 0
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Loop length", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
            Slider(
                value = settings.loopSec.toFloat(),
                onValueChange = { v ->
                    val sec = v.roundToInt().coerceIn(1, 16)
                    if (sec != AppState.settings.value.loopSec) {
                        AppState.updateSettings(AppState.settings.value.copy(loopSec = sec))
                        DjEngine.setLoopLength(sec * 1000L)
                    }
                },
                valueRange = 1f..16f,
                steps = 14,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${settings.loopSec}s", color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            FilterChip(
                selected = looping,
                onClick = { DjEngine.setLoop(!looping, settings.loopSec * 1000L) },
                label = { Text(if (looping) "Looping ${engine.loopLenMs / 1000}s" else "Loop ${settings.loopSec}s") },
                enabled = engine.durationMs > 0,
            )
            FilterChip(
                selected = engine.repeatTrack,
                onClick = { DjEngine.toggleRepeat() },
                label = { Text("Repeat track") },
            )
        }
    }
}

@Composable
private fun TransitionInfo(engine: DjEngine.State, settings: MixSettings, onOpenSpotify: () -> Unit) {
    val overlapOff = settings.style == TransitionStyle.OVERLAP && engine.crossfadeMs <= 0
    if (overlapOff) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A12)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Set your Spotify crossfade length", fontWeight = FontWeight.Bold)
                Text(
                    "Overlap mixes use Spotify's Crossfade (Spotify > Settings > Playback). Spotify doesn't " +
                        "reliably tell apps this setting, so tap the length you set there. Until then DJ Mix uses dip fades.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4, 6, 8, 10, 12).forEach { sec ->
                        TextButton(
                            onClick = { AppState.updateSettings(AppState.settings.value.copy(crossfadeSec = sec)) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        ) { Text("${sec}s") }
                    }
                }
                TextButton(onClick = onOpenSpotify) { Text("Open Spotify") }
            }
        }
        return
    }
    val style = when (settings.style) {
        TransitionStyle.OVERLAP ->
            if (engine.crossfadeMs > 0) "Outro crossfade ${engine.crossfadeMs / 1000}s" else "Outro crossfade"
        TransitionStyle.DJ_FADE -> "Smooth fade ${settings.fadeOutSec}s out / ${settings.fadeInSec}s in"
    }
    val length = if (settings.randomLength) " · random length ${settings.minPlayPct}-${settings.maxPlayPct}%" else ""
    val mode = when (settings.mode) {
        PlayMode.SHUFFLE -> "shuffle"
        PlayMode.SEQUENCE -> "in order"
        PlayMode.CUSTOM -> "custom"
    }
    Text("$style$length · $mode", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
}
