@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mansoor.djapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** One full turn of the jog wheel moves this much through the track. */
private const val JOG_MS_PER_TURN = 10_000f

private val Cyan = Color(0xFF22D3EE)
private val Magenta = Color(0xFFE040FB)
private val Orange = Color(0xFFFFA726)
private val DeckBackground = Brush.verticalGradient(
    listOf(Color(0xFF241345), Color(0xFF120C1F), Color(0xFF09080D)),
)

/** Neon color for what the deck is doing right now. */
private fun phaseColor(e: DjEngine.State): Color = when {
    e.paused -> Color.Gray
    e.phase == DjEngine.Phase.FADING_OUT -> Magenta
    e.phase == DjEngine.Phase.SWITCHING -> Orange
    e.phase == DjEngine.Phase.FADING_IN || e.phase == DjEngine.Phase.OVERLAP -> Cyan
    else -> Green
}

private fun phaseLabel(e: DjEngine.State): String = when {
    e.loading -> "LOADING"
    !e.running -> if (e.trackName.isBlank()) "READY" else "SPOTIFY"
    e.paused -> "PAUSED"
    e.phase == DjEngine.Phase.FADING_OUT -> "MIXING OUT"
    e.phase == DjEngine.Phase.SWITCHING -> "DROP"
    e.phase == DjEngine.Phase.FADING_IN -> "MIXING IN"
    e.phase == DjEngine.Phase.OVERLAP -> "CROSSFADE"
    e.loopLenMs > 0 -> "LOOPING"
    else -> "LIVE"
}

@Composable
fun PlayerScreen(engine: DjEngine.State, onBack: () -> Unit, onSettings: () -> Unit, onQueue: () -> Unit) {
    val settings by AppState.settings.collectAsState()
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(DeckBackground)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            "DJ MIX",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            style = MaterialTheme.typography.titleLarge.copy(
                                brush = Brush.horizontalGradient(listOf(Green, Cyan, Magenta)),
                            ),
                        )
                    },
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
                Modifier.padding(pad).fillMaxSize().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    PhaseBadge(engine)
                    Spacer(Modifier.height(10.dp))
                    Vinyl(engine)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        engine.trackName.ifBlank { if (engine.loading) "Loading tracks…" else "Nothing playing" },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, color = Color.White,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(engine.artist, color = Color(0xFFCFC6E6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (engine.source.isNotBlank()) {
                        Text(
                            engine.source.uppercase(),
                            style = MaterialTheme.typography.labelSmall, color = Purple, letterSpacing = 1.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MixBar(engine)
                    StatusLine(engine)
                    Spacer(Modifier.height(10.dp))
                    Transport(engine)
                    Spacer(Modifier.height(16.dp))
                    LoopLength(settings)
                    Spacer(Modifier.height(8.dp))
                    PadGrid(engine, settings)
                    Spacer(Modifier.height(12.dp))
                    TransitionInfo(engine, settings, onOpenSpotify = { openSpotify(ctx) })
                    Spacer(Modifier.height(16.dp))
                    if (engine.upNext.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "UP NEXT", fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                                color = Color.White, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onQueue) { Text("Queue (${engine.upcomingCount})", color = Cyan) }
                        }
                        Text(
                            "Tap a song to mix into it now.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                itemsIndexed(engine.upNext) { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable { DjEngine.jumpToUpcoming(i) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d".format(i + 1), color = Purple, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp),
                        )
                        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

/** Pill showing LIVE / MIXING OUT / DROP / ... with a pulsing dot. */
@Composable
private fun PhaseBadge(engine: DjEngine.State) {
    val color by animateColorAsState(phaseColor(engine), label = "phaseColor")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (engine.paused || !engine.running) 1f else alpha)),
        )
        Spacer(Modifier.width(8.dp))
        Text(phaseLabel(engine), color = color, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
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
    val glow by animateColorAsState(phaseColor(engine), label = "glow")

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
            .fillMaxWidth(0.82f)
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
        // Neon halo behind the record.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.45f), glow.copy(alpha = 0.12f), Color.Transparent),
                    center = center, radius = r * 1.0f,
                ),
                radius = r,
            )
            drawCircle(glow.copy(alpha = 0.9f), radius = r * 0.93f, style = Stroke(width = 2.5.dp.toPx()))
        }
        // The record itself, turning.
        Canvas(Modifier.fillMaxSize(0.9f).graphicsLayer { rotationZ = angle }) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF0B0B0F), radius = r)
            for (i in 0 until 18) {
                drawCircle(
                    color = if (i % 3 == 0) Color(0xFF2A2433) else Color(0xFF17141C),
                    radius = r * (0.98f - i * 0.026f),
                    style = Stroke(width = 1.2f),
                )
            }
            drawArc(color = Color.White.copy(alpha = 0.08f), startAngle = -70f, sweepAngle = 35f, useCenter = true)
            drawArc(color = Color.White.copy(alpha = 0.08f), startAngle = 110f, sweepAngle = 35f, useCenter = true)
        }
        val art = engine.art
        Box(
            Modifier
                .fillMaxSize(0.44f)
                .graphicsLayer { rotationZ = angle }
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
                .border(3.dp, glow.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(48.dp), tint = Color.Gray)
            }
        }
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF09080D)))
        if (dragging) {
            val sign = if (scrubMs >= 0) "+" else "-"
            Text(
                "$sign${fmt(abs(scrubMs))}",
                Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Cyan, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = Cyan, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/**
 * Progress bar with two handles: the white playhead (drag or tap to seek) and the magenta
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
                .height(52.dp)
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
                val cy = size.height * 0.64f
                fun x(ms: Long) = (ms / d * w).coerceIn(0f, w)
                val pos = if (dragTarget == 1) dragMs else e.positionMs
                val mix = if (dragTarget == 2) dragMs else e.mixPointMs
                val thick = 8.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, cy), Offset(w, cy), strokeWidth = thick, cap = StrokeCap.Round)
                if (e.smartEndMs > e.smartStartMs) {
                    val xs = x(e.smartStartMs)
                    drawRect(Magenta.copy(alpha = 0.25f), topLeft = Offset(xs, cy - thick), size = Size(x(e.smartEndMs) - xs, thick * 2))
                }
                if (e.loopLenMs > 0) {
                    val xs = x(e.loopStartMs)
                    val loopColor = if (e.loopBack) Cyan else Magenta
                    drawRect(loopColor.copy(alpha = 0.35f), topLeft = Offset(xs, cy - thick), size = Size(x(e.loopStartMs + e.loopLenMs) - xs, thick * 2))
                }
                val px = x(pos)
                if (px > 0f) {
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(Green, Cyan), startX = 0f, endX = px.coerceAtLeast(1f)),
                        start = Offset(0f, cy), end = Offset(px, cy), strokeWidth = thick, cap = StrokeCap.Round,
                    )
                }
                if (e.running && mix > 0) {
                    val mx = x(mix)
                    val top = cy - 20.dp.toPx()
                    drawLine(Magenta, Offset(mx, top), Offset(mx, cy + 12.dp.toPx()), strokeWidth = 3.dp.toPx())
                    drawCircle(Magenta.copy(alpha = 0.3f), radius = 16.dp.toPx(), center = Offset(mx, top))
                    drawCircle(Magenta, radius = (if (dragTarget == 2) 11 else 8).dp.toPx(), center = Offset(mx, top))
                }
                drawCircle(Cyan.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = Offset(px, cy))
                drawCircle(Color.White, radius = (if (dragTarget == 1) 10 else 8).dp.toPx(), center = Offset(px, cy))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmt(if (dragTarget == 1) dragMs else engine.positionMs),
                fontFamily = FontFamily.Monospace, color = Color.White, style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            if (engine.running && engine.mixPointMs > 0) {
                val mixAt = if (dragTarget == 2) dragMs else engine.mixPointMs
                Text(
                    "MIX @ ${fmt(mixAt)}" + if (engine.mixPointManual) " · YOURS" else " · AUTO",
                    color = Magenta, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (engine.mixPointManual) {
                    TextButton(onClick = { DjEngine.resetMixPoint() }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("AUTO", color = Cyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(fmt(engine.durationMs), fontFamily = FontFamily.Monospace, color = Color.Gray, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusLine(engine: DjEngine.State) {
    val text = engine.status.ifBlank { engine.smartInfo ?: "" }
    if (text.isNotBlank()) {
        Text(text, color = phaseColor(engine).copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
    }
    engine.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, enabled: Boolean, size: Dp = 52.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.18f else 0.06f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size * 0.5f), tint = if (enabled) Color.White else Color.DarkGray)
    }
}

@Composable
private fun Transport(engine: DjEngine.State) {
    val hasTrack = engine.durationMs > 0
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundButton(Icons.Default.SkipPrevious, "Previous", engine.running) { DjEngine.previous() }
        RoundButton(Icons.Default.Replay10, "Back 10 seconds", hasTrack) { DjEngine.nudge(-10_000) }
        Box(
            Modifier
                .size(78.dp)
                .shadow(18.dp, CircleShape, ambientColor = Green, spotColor = Green)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Green, Cyan)))
                .clickable { DjEngine.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (engine.paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Play/pause",
                Modifier.size(42.dp), tint = Color.Black,
            )
        }
        RoundButton(Icons.Default.Forward10, "Forward 10 seconds", hasTrack) { DjEngine.nudge(10_000) }
        RoundButton(Icons.Default.SkipNext, "Mix next now", engine.running) { DjEngine.next() }
    }
}

@Composable
private fun LoopLength(settings: MixSettings) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("LOOP", color = Color.Gray, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
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
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Cyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(
            "${settings.loopSec}s", color = Cyan, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp),
        )
    }
}

/** A DJ pad: neon outline when idle, lit up when active. */
@Composable
private fun DjPad(
    title: String,
    value: String,
    color: Color,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val glowModifier = if (active) Modifier.shadow(16.dp, shape, ambientColor = color, spotColor = color) else Modifier
    val bg = if (active) Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))) else
        Brush.linearGradient(listOf(color.copy(alpha = 0.14f), color.copy(alpha = 0.05f)))
    val fg = when {
        !enabled -> Color.DarkGray
        active -> Color.Black
        else -> color
    }
    Column(
        modifier
            .height(72.dp)
            .then(glowModifier)
            .clip(shape)
            .background(bg)
            .border(1.5.dp, if (enabled) color.copy(alpha = 0.9f) else Color.DarkGray, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = fg.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, color = fg, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

@Composable
private fun PadGrid(engine: DjEngine.State, settings: MixSettings) {
    val hasTrack = engine.durationMs > 0
    val looping = engine.loopLenMs > 0
    val lastOn = looping && engine.loopBack
    val nextOn = looping && !engine.loopBack
    val lenMs = settings.loopSec * 1000L
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DjPad("LOOP LAST", "◀ ${settings.loopSec}s", Cyan, lastOn, hasTrack, Modifier.weight(1f)) {
                DjEngine.setLoop(!lastOn, lenMs, back = true)
            }
            DjPad("LOOP NEXT", "${settings.loopSec}s ▶", Magenta, nextOn, hasTrack, Modifier.weight(1f)) {
                DjEngine.setLoop(!nextOn, lenMs, back = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DjPad("REPEAT", if (engine.repeatTrack) "ON" else "OFF", Orange, engine.repeatTrack, true, Modifier.weight(1f)) {
                DjEngine.toggleRepeat()
            }
            DjPad("MIX NOW", "▶▶", Green, engine.phase != DjEngine.Phase.PLAYING && engine.phase != DjEngine.Phase.IDLE, engine.running, Modifier.weight(1f)) {
                DjEngine.next()
            }
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
                    "Outro crossfade uses Spotify's Crossfade (Spotify > Settings > Playback). Spotify doesn't " +
                        "reliably tell apps this setting, so tap the length you set there. Until then DJ Mix uses smooth fades.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4, 6, 8, 10, 12).forEach { sec ->
                        TextButton(
                            onClick = { AppState.updateSettings(AppState.settings.value.copy(crossfadeSec = sec)) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
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
    Text("$style$length · $mode".uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
}
