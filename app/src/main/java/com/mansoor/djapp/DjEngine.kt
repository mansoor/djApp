package com.mansoor.djapp

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The DJ: drives the Spotify app through App Remote, watches playback position and
 * performs transitions (fade out -> drop next track -> fade in) at the mix point.
 */
object DjEngine {
    enum class Phase { IDLE, PLAYING, FADING_OUT, SWITCHING, FADING_IN }

    data class State(
        val connected: Boolean = false,
        val connecting: Boolean = false,
        val running: Boolean = false,
        val loading: Boolean = false,
        val paused: Boolean = true,
        val trackName: String = "",
        val artist: String = "",
        val durationMs: Long = 0,
        val positionMs: Long = 0,
        val mixPointMs: Long = 0,
        val art: Bitmap? = null,
        val phase: Phase = Phase.IDLE,
        val status: String = "",
        val source: String = "",
        val upNext: List<String> = emptyList(),
        val error: String? = null,
        val crossfadeInfo: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Context
    private lateinit var fader: VolumeFader

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var remote: SpotifyAppRemote? = null
    private var connecting = false
    private var connectAttempt = 0
    private var connectStartedAt = 0L
    private var reconnectAttempts = 0

    private var sources: List<Pair<PlaylistInfo, List<TrackInfo>?>> = emptyList()
    private var queue: List<QueueEntry> = emptyList()
    private var index = 0
    private var blockPlayed = 0
    private var running = false
    private var loading = false
    private var phase = Phase.IDLE
    private var status = ""
    private var error: String? = null
    private var crossfadeInfo: String? = null
    private var nativeQueued: String? = null

    private var lastPs: PlayerState? = null
    private var lastPsAt = 0L
    private var currentUri: String? = null
    private var lastImage: String? = null
    private var art: Bitmap? = null
    private var transitionJob: Job? = null

    private val settings: MixSettings get() = AppState.settings.value

    fun init(ctx: Context) {
        app = ctx.applicationContext
        fader = VolumeFader(app)
        scope.launch {
            while (true) {
                tick()
                delay(200)
            }
        }
        scope.launch {
            while (true) {
                delay(3000)
                // Re-sync position periodically; events only arrive on state changes.
                if (running) remote?.playerApi?.playerState?.setResultCallback { ps -> scope.launch { onPlayerState(ps) } }
            }
        }
    }

    // ---------------------------------------------------------------- connection

    /**
     * Connects to the Spotify app. A connect that hasn't answered within a few seconds
     * (e.g. Spotify was asleep) is abandoned and a fresh one started when called again.
     */
    fun connect(ctx: Context, showAuth: Boolean = true) {
        if (remote?.isConnected == true) return
        if (connecting && SystemClock.elapsedRealtime() - connectStartedAt < 5000) return
        val cid = Prefs.clientId
        if (cid.isBlank()) return
        if (!SpotifyAppRemote.isSpotifyInstalled(ctx)) {
            error = "The Spotify app isn't installed on this phone."
            publish()
            return
        }
        val attempt = ++connectAttempt
        connecting = true
        connectStartedAt = SystemClock.elapsedRealtime()
        error = null
        publish()
        val params = ConnectionParams.Builder(cid)
            .setRedirectUri(SpotifyAuth.REDIRECT_URI)
            .showAuthView(showAuth)
            .build()
        SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
            override fun onConnected(r: SpotifyAppRemote) {
                scope.launch { onConnected(r) }
            }

            override fun onFailure(t: Throwable) {
                scope.launch { if (attempt == connectAttempt || remote != null) onConnectionFailed(t) }
            }
        })
        scope.launch {
            delay(20_000)
            if (attempt == connectAttempt && connecting && remote?.isConnected != true) {
                connecting = false
                error = "The Spotify app didn't answer. Tap \"Open Spotify\", play any song for a moment, " +
                    "come back here and tap Connect. If it still hangs, tap \"Re-authorize\"."
                publish()
            }
        }
    }

    private fun onConnected(r: SpotifyAppRemote) {
        val existing = remote
        if (existing != null && existing !== r && existing.isConnected) {
            // A late answer from an abandoned attempt.
            SpotifyAppRemote.disconnect(r)
            return
        }
        connecting = false
        reconnectAttempts = 0
        remote = r
        error = null
        r.playerApi.subscribeToPlayerState().setEventCallback { ps -> scope.launch { onPlayerState(ps) } }
        r.playerApi.crossfadeState
            .setResultCallback { cf ->
                scope.launch {
                    val secs = if (cf.duration > 100) cf.duration / 1000 else cf.duration
                    crossfadeInfo = if (cf.isEnabled) "Spotify crossfade is ON (${secs}s)" else "Spotify crossfade is OFF"
                    publish()
                }
            }
            .setErrorCallback { }
        publish()
    }

    private fun onConnectionFailed(t: Throwable) {
        connecting = false
        remote = null
        error = describe(t)
        publish()
        if (running && reconnectAttempts < 5) {
            reconnectAttempts++
            scope.launch {
                delay(3000)
                connect(app, showAuth = false)
            }
        }
    }

    private fun describe(t: Throwable): String {
        val n = t.javaClass.simpleName
        return when {
            n.contains("CouldNotFindSpotifyApp") -> "The Spotify app isn't installed on this phone."
            n.contains("NotLoggedIn") -> "Open the Spotify app and log in, then tap Connect."
            n.contains("UserNotAuthorized") || n.contains("AuthenticationFailed") ->
                "Spotify refused the connection. In the Spotify dashboard check the redirect URI " +
                    "(${SpotifyAuth.REDIRECT_URI}), package name and SHA1 from the setup screen. (${t.message})"
            n.contains("Offline") -> "Spotify is in offline mode."
            n.contains("Terminated") || n.contains("Disconnected") -> "Lost connection to Spotify. Tap Connect."
            else -> "Spotify connection failed ($n): ${t.message ?: "no details"}"
        }
    }

    // ---------------------------------------------------------------- public controls

    fun start(selected: List<PlaylistInfo>) {
        val r = remote
        if (r == null || !r.isConnected) {
            error = "The Spotify app isn't connected yet. Tap Connect."
            publish()
            return
        }
        if (selected.isEmpty() || loading) return
        transitionJob?.cancel()
        scope.launch {
            loading = true
            error = null
            status = "Loading tracks…"
            publish()
            try {
                val loaded = selected.map { p ->
                    p to try {
                        SpotifyApi.tracks(p)
                    } catch (e: Exception) {
                        null
                    }
                }
                val q = MixBuilder.build(loaded, settings)
                if (q.isEmpty()) {
                    error = "Those playlists have no playable tracks."
                    return@launch
                }
                sources = loaded
                queue = q
                index = 0
                running = true
                phase = Phase.SWITCHING
                DjService.start(app)
                r.playerApi.setRepeat(0)
                val blocks = loaded.count { it.second == null }
                status = if (blocks > 0) {
                    "$blocks playlist(s) play in blocks (Spotify hides tracks of playlists you don't own)"
                } else {
                    "Mix started: ${q.size} tracks"
                }
                playCurrent(r, currentUri)
            } finally {
                loading = false
                if (running) phase = Phase.PLAYING
                publish()
            }
        }
    }

    fun stop() {
        val wasRunning = running
        running = false
        phase = Phase.IDLE
        nativeQueued = null
        transitionJob?.cancel()
        if (wasRunning) remote?.playerApi?.pause()
        status = ""
        DjService.stop(app)
        publish()
    }

    fun togglePause() {
        val r = remote ?: return
        if (lastPs?.isPaused == true) {
            r.playerApi.resume()
        } else {
            transitionJob?.cancel()
            r.playerApi.pause()
        }
    }

    /** "Mix now": transition to the next track straight away. */
    fun next() {
        if (running) transition(fadeOutMs = minOf(settings.fadeOutSec * 1000L, 3000L))
    }

    fun previous() {
        if (!running) return
        if (estimatedPosition() > 8000) {
            remote?.playerApi?.seekTo(0)
            return
        }
        val e = queue.getOrNull(index)
        if (e is QueueEntry.Block && blockPlayed > 0) {
            blockPlayed = (blockPlayed - 2).coerceAtLeast(-1) // the track-change event adds one back
            remote?.playerApi?.skipPrevious()
            return
        }
        transition(fadeOutMs = 1500, jumpTo = (index - 1).coerceAtLeast(0))
    }

    /** Jump to the k-th upcoming entry (0 = next). */
    fun playUpcoming(k: Int) {
        if (running) transition(fadeOutMs = 2000, jumpTo = index + 1 + k)
    }

    fun clearError() {
        error = null
        publish()
    }

    // ---------------------------------------------------------------- playback tracking

    private fun onPlayerState(ps: PlayerState) {
        val prevPos = estimatedPosition()
        val prevDur = lastPs?.track?.duration ?: 0L
        lastPs = ps
        lastPsAt = SystemClock.elapsedRealtime()
        val uri = ps.track?.uri
        if (uri != null && uri != currentUri) {
            currentUri = uri
            onTrackChanged(uri, prevPos, prevDur)
        }
        val img = ps.track?.imageUri
        if (img != null && img.raw != lastImage) {
            lastImage = img.raw
            remote?.imagesApi?.getImage(img, Image.Dimension.LARGE)
                ?.setResultCallback { bmp -> scope.launch { art = bmp; publish() } }
        }
        publish()
    }

    private fun onTrackChanged(uri: String, prevPos: Long, prevDur: Long) {
        // During our own transitions the change is expected.
        if (!running || phase == Phase.SWITCHING || phase == Phase.FADING_OUT) return
        val queued = nativeQueued
        if (queued != null && uri == queued) {
            // Spotify moved on to the track we queued (with its own crossfade).
            index++
            blockPlayed = 0
            nativeQueued = null
            queueNextNative()
            return
        }
        when (val e = queue.getOrNull(index)) {
            is QueueEntry.Block -> {
                blockPlayed++
                return
            }
            is QueueEntry.Track -> if (e.track.uri == uri) return
            null -> {}
        }
        if (prevDur > 0 && prevPos > prevDur - 6000) {
            // The track ran out and Spotify autoplayed something else: get back on the mix.
            transition(withFadeOut = false)
        } else {
            status = "Playing a track picked in Spotify. The mix continues after it."
        }
    }

    private fun tick() {
        val ps = lastPs
        if (ps != null && !ps.isPaused) publish()
        if (!running || phase != Phase.PLAYING || ps == null) return
        val t = ps.track ?: return
        if (t.duration <= 0) return
        val pos = estimatedPosition()
        if (ps.isPaused) {
            // Spotify stopped by itself at the end of something: keep the music going.
            if (pos >= t.duration - 1500 && SystemClock.elapsedRealtime() - lastPsAt > 1500) transition(withFadeOut = false)
            return
        }
        if (pos > t.duration + 4000) {
            transition(withFadeOut = false)
            return
        }
        if (spotifyHandlesNext()) return
        if (pos >= triggerPoint(t.duration)) transition()
    }

    private fun spotifyHandlesNext(): Boolean {
        if (settings.style != TransitionStyle.SPOTIFY_CROSSFADE) return false
        return when (val e = queue.getOrNull(index)) {
            is QueueEntry.Block -> blockPlayed + 1 < e.count
            is QueueEntry.Track -> nativeQueued != null
            null -> false
        }
    }

    private fun triggerPoint(durationMs: Long): Long {
        val s = settings
        val lead = (s.fadeOutSec + s.mixOutEarlySec) * 1000L
        return maxOf(durationMs - lead, minOf(durationMs / 2, 20_000L))
    }

    private fun estimatedPosition(): Long {
        val ps = lastPs ?: return 0
        if (ps.isPaused) return ps.playbackPosition
        val speed = if (ps.playbackSpeed > 0f) ps.playbackSpeed else 1f
        return ps.playbackPosition + ((SystemClock.elapsedRealtime() - lastPsAt) * speed).toLong()
    }

    // ---------------------------------------------------------------- transitions

    private fun transition(withFadeOut: Boolean = true, fadeOutMs: Long? = null, jumpTo: Int? = null) {
        if (transitionJob?.isActive == true) return
        val r = remote ?: return
        val s = settings
        transitionJob = scope.launch {
            val baseVol = fader.current()
            val floor = (baseVol * s.floorPct / 100f).roundToInt()
            try {
                if (withFadeOut && baseVol > 0) {
                    phase = Phase.FADING_OUT
                    status = "Mixing out…"
                    publish()
                    fader.ramp(baseVol, floor, fadeOutMs ?: (s.fadeOutSec * 1000L))
                } else {
                    fader.set(floor)
                }
                phase = Phase.SWITCHING
                status = "Dropping the next track…"
                publish()
                val started = advance(r, jumpTo)
                if (started && s.skipIntroSec > 0) {
                    val dur = lastPs?.track?.duration ?: 0L
                    if (dur > s.skipIntroSec * 1000L + 60_000L) {
                        r.playerApi.seekTo(s.skipIntroSec * 1000L)
                        delay(300)
                    }
                }
                phase = Phase.FADING_IN
                status = "Mixing in…"
                publish()
                fader.ramp(floor, baseVol, s.fadeInSec * 1000L)
                status = ""
            } finally {
                fader.set(baseVol)
                if (running) phase = Phase.PLAYING
                publish()
            }
        }
    }

    private suspend fun advance(r: SpotifyAppRemote, jumpTo: Int?): Boolean {
        val fromUri = currentUri
        val entry = queue.getOrNull(index)
        val queued = nativeQueued
        nativeQueued = null

        if (jumpTo == null && queued != null && entry is QueueEntry.Track) {
            // The next track is already in Spotify's queue: skip into it so it's consumed.
            r.playerApi.skipNext()
            val ok = awaitTrack { it == queued }
            index++
            blockPlayed = 0
            if (ok) queueNextNative()
            return ok
        }
        if (jumpTo == null && entry is QueueEntry.Block && blockPlayed + 1 < entry.count) {
            blockPlayed++
            r.playerApi.skipNext()
            return awaitTrack { it != null && it != fromUri }
        }
        index = jumpTo ?: (index + 1)
        if (index >= queue.size) {
            queue = MixBuilder.build(sources, settings)
            status = "Starting a fresh round"
            index = 0
        }
        if (index < 0) index = 0
        return playCurrent(r, fromUri)
    }

    private suspend fun playCurrent(r: SpotifyAppRemote, fromUri: String?): Boolean {
        blockPlayed = 0
        val ok = when (val e = queue.getOrNull(index) ?: return false) {
            is QueueEntry.Track -> {
                r.playerApi.play(e.track.uri).setErrorCallback { t -> onPlayError(t) }
                awaitTrack { it == e.track.uri }
            }
            is QueueEntry.Block -> {
                r.playerApi.setShuffle(settings.mode == PlayMode.SHUFFLE)
                r.playerApi.play(e.playlist.uri).setErrorCallback { t -> onPlayError(t) }
                awaitTrack { it != null && it != fromUri }
            }
        }
        if (ok) queueNextNative()
        return ok
    }

    private fun onPlayError(t: Throwable) {
        scope.launch {
            error = "Spotify couldn't play that: ${t.message}"
            publish()
        }
    }

    /** In Spotify-crossfade mode, keep the next track queued in Spotify so it overlaps natively. */
    private fun queueNextNative() {
        if (settings.style != TransitionStyle.SPOTIFY_CROSSFADE) return
        if (queue.getOrNull(index) !is QueueEntry.Track) return
        val next = queue.getOrNull(index + 1) as? QueueEntry.Track ?: return
        remote?.playerApi?.queue(next.track.uri)
        nativeQueued = next.track.uri
    }

    private suspend fun awaitTrack(timeoutMs: Long = 6000, pred: (String?) -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            val ps = lastPs
            if (pred(ps?.track?.uri) && ps?.isPaused == false) return true
            delay(100)
        }
        return pred(lastPs?.track?.uri)
    }

    // ---------------------------------------------------------------- UI state

    private fun label(e: QueueEntry): String = when (e) {
        is QueueEntry.Track -> "${e.track.name} · ${e.track.artist}"
        is QueueEntry.Block -> "${e.count} tracks from \"${e.playlist.name}\""
    }

    private fun publish() {
        val ps = lastPs
        val t = ps?.track
        val dur = t?.duration ?: 0L
        val e = queue.getOrNull(index)
        val from = minOf(index + 1, queue.size)
        val to = minOf(index + 9, queue.size)
        _state.value = State(
            connected = remote?.isConnected == true,
            connecting = connecting,
            running = running,
            loading = loading,
            paused = ps?.isPaused ?: true,
            trackName = t?.name ?: "",
            artist = t?.artist?.name ?: "",
            durationMs = dur,
            positionMs = estimatedPosition().coerceIn(0L, maxOf(dur, 0L)),
            mixPointMs = if (dur > 0) triggerPoint(dur) else 0,
            art = art,
            phase = phase,
            status = status,
            source = when (e) {
                is QueueEntry.Track -> e.track.source
                is QueueEntry.Block -> "${e.playlist.name} · ${(blockPlayed + 1).coerceAtLeast(1)}/${e.count}"
                null -> ""
            },
            upNext = if (running) queue.subList(from, to).map { label(it) } else emptyList(),
            error = error,
            crossfadeInfo = crossfadeInfo,
        )
    }
}
