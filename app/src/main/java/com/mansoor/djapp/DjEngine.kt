package com.mansoor.djapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.content.ContextCompat
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
import kotlin.random.Random

/**
 * The DJ: drives the Spotify app through App Remote, watches playback position, decides each
 * track's mix point and performs the transition (Spotify crossfade overlap or a volume dip).
 */
object DjEngine {
    enum class Phase { IDLE, PLAYING, FADING_OUT, SWITCHING, FADING_IN, OVERLAP }

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
        val mixPointManual: Boolean = false,
        val smartStartMs: Long = 0,
        val smartEndMs: Long = 0,
        val art: Bitmap? = null,
        val phase: Phase = Phase.IDLE,
        val status: String = "",
        val source: String = "",
        val upNext: List<String> = emptyList(),
        val upcomingCount: Int = 0,
        val queueVersion: Int = 0,
        val error: String? = null,
        /** Spotify's crossfade length in ms; 0 = off, -1 = unknown. */
        val crossfadeMs: Int = -1,
        val loopLenMs: Long = 0,
        val loopStartMs: Long = 0,
        val repeatTrack: Boolean = false,
        val smartInfo: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Context
    private lateinit var fader: VolumeFader
    private val rnd = Random.Default

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var remote: SpotifyAppRemote? = null
    private var connecting = false
    private var connectAttempt = 0
    private var connectStartedAt = 0L
    private var lastAttemptShowedAuth = false
    private var authViewNeeded = false
    private var reconnectAttempts = 0

    private var mode = PlayMode.SHUFFLE
    private var sources: List<Pair<PlaylistInfo, List<TrackInfo>?>> = emptyList()
    private var customTracks: List<TrackInfo> = emptyList()
    private val queue = mutableListOf<QueueEntry>()
    private var index = 0
    private var blockPlayed = 0
    private var queueVersion = 0
    private var running = false
    private var loading = false
    private var phase = Phase.IDLE
    private var status = ""
    private var error: String? = null
    private var crossfadeMs = -1
    private var nativeQueued: String? = null

    // Current track plan
    private var planUri: String? = null
    private var plannedMix = 0L
    private var manualMix: Long? = null
    private var loopLen = 0L
    private var loopStart = 0L
    private var repeatTrack = false

    private var lastPs: PlayerState? = null
    private var basePos = 0L
    private var baseAt = 0L
    private var currentUri: String? = null
    private var lastImage: String? = null
    private var art: Bitmap? = null
    private var transitionJob: Job? = null
    private var detector: LullDetector? = null
    private var detectorFailed = false

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
                if (running && transitionJob?.isActive != true) {
                    remote?.playerApi?.playerState?.setResultCallback { ps -> scope.launch { onPlayerState(ps) } }
                }
            }
        }
    }

    // ---------------------------------------------------------------- connection

    /**
     * Connects to the Spotify app. A connect that hasn't answered within a few seconds
     * (e.g. Spotify was asleep) is abandoned and a fresh one started when called again.
     */
    fun connect(ctx: Context, showAuth: Boolean = !SpotifyAuth.hasRemoteScope() || authViewNeeded) {
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
        lastAttemptShowedAuth = showAuth
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
                scope.launch { handleConnected(r) }
            }

            override fun onFailure(t: Throwable) {
                scope.launch { if (attempt == connectAttempt || remote != null) handleConnectionFailed(t) }
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

    private fun handleConnected(r: SpotifyAppRemote) {
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
        refreshCrossfade()
        publish()
    }

    private fun handleConnectionFailed(t: Throwable) {
        connecting = false
        remote = null
        val n = t.javaClass.simpleName
        if (!lastAttemptShowedAuth && (n.contains("UserNotAuthorized") || n.contains("AuthenticationFailed"))) {
            // The web login grant wasn't enough: fall back to Spotify's own approval screen.
            authViewNeeded = true
            error = "Spotify needs you to approve DJ Mix in the Spotify app. Tap Connect, then Agree."
            publish()
            return
        }
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

    private fun refreshCrossfade() {
        remote?.playerApi?.crossfadeState
            ?.setResultCallback { cf ->
                scope.launch {
                    crossfadeMs = when {
                        !cf.isEnabled -> 0
                        cf.duration > 100 -> cf.duration
                        else -> cf.duration * 1000
                    }
                    publish()
                }
            }
            ?.setErrorCallback { }
    }

    // ---------------------------------------------------------------- start / stop

    fun start(selected: List<PlaylistInfo>, custom: List<TrackInfo>) {
        val r = remote
        if (r == null || !r.isConnected) {
            error = "The Spotify app isn't connected yet. Tap Connect."
            publish()
            return
        }
        if (loading) return
        transitionJob?.cancel()
        scope.launch {
            loading = true
            error = null
            status = "Loading tracks…"
            publish()
            try {
                val s = settings
                val loaded = selected.map { it to AppState.loadTracks(it) }
                val first: List<QueueEntry> = if (s.mode == PlayMode.CUSTOM) {
                    custom.map { QueueEntry.Track(it) } +
                        (if (loaded.isEmpty()) emptyList() else MixBuilder.build(loaded, s.copy(mode = PlayMode.SHUFFLE)))
                } else {
                    MixBuilder.build(loaded, s)
                }
                if (first.isEmpty()) {
                    error = if (s.mode == PlayMode.CUSTOM) {
                        "Your custom queue is empty. Open a playlist and add songs, or select playlists."
                    } else {
                        "Those playlists have no playable tracks."
                    }
                    return@launch
                }
                mode = s.mode
                sources = loaded
                customTracks = custom
                queue.clear()
                queue.addAll(first)
                index = 0
                nativeQueued = null
                running = true
                phase = Phase.SWITCHING
                DjService.start(app)
                r.playerApi.setRepeat(0)
                if (s.smartSwitch) startDetector()
                val blocks = loaded.count { it.second == null }
                status = if (blocks > 0) {
                    "$blocks playlist(s) play in blocks (Spotify hides tracks of playlists you don't own)"
                } else {
                    "Mix started"
                }
                playCurrent(r, currentUri)
            } finally {
                loading = false
                if (running) phase = Phase.PLAYING
                bump()
                publish()
            }
        }
    }

    fun stop() {
        val wasRunning = running
        running = false
        phase = Phase.IDLE
        nativeQueued = null
        loopLen = 0
        transitionJob?.cancel()
        stopDetector()
        if (wasRunning) remote?.playerApi?.pause()
        status = ""
        DjService.stop(app)
        publish()
    }

    fun onSettingsChanged() {
        if (!running) return
        if (settings.smartSwitch) startDetector() else stopDetector()
        if (settings.style == TransitionStyle.OVERLAP) queueNextNative()
        publish()
    }

    private fun startDetector() {
        if (detector != null) return
        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            detectorFailed = true
            return
        }
        val d = LullDetector()
        if (d.start()) {
            detector = d
            detectorFailed = false
        } else {
            detectorFailed = true
        }
    }

    private fun stopDetector() {
        detector?.stop()
        detector = null
    }

    // ---------------------------------------------------------------- deck controls

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
    fun next() = mixToNext(manual = true)

    fun previous() {
        if (!running) return
        if (estimatedPosition() > 8000) {
            seek(0)
            return
        }
        val e = queue.getOrNull(index)
        if (e is QueueEntry.Block && blockPlayed > 0 && blockPlayed < e.count) {
            blockPlayed = (blockPlayed - 2).coerceAtLeast(-1) // the track-change event adds one back
            remote?.playerApi?.skipPrevious()
            return
        }
        dipTransition(fadeOutMs = 1500, jumpTo = (index - 1).coerceAtLeast(0))
    }

    fun seek(ms: Long) {
        val t = lastPs?.track ?: return
        val dur = t.duration
        val target = ms.coerceIn(0L, (dur - 1000).coerceAtLeast(0L))
        seekLocal(target)
        if (loopLen > 0 && (target < loopStart || target > loopStart + loopLen)) loopLen = 0
        if (running && effectiveMix() < target + 4000) {
            // Seeking past the mix point moves it later so the track isn't cut right away.
            manualMix = null
            plannedMix = maxOf(target + 4000, endBasedMix(dur)).coerceAtMost(dur - 1500)
        }
        publish()
    }

    fun nudge(deltaMs: Long) {
        if (deltaMs != 0L) seek(estimatedPosition() + deltaMs)
    }

    /** Drag the mix marker: play the current track until [ms], then mix. */
    fun setMixPoint(ms: Long) {
        val dur = lastPs?.track?.duration ?: return
        manualMix = ms.coerceAtMost(dur - 1500).coerceAtLeast(estimatedPosition() + 3000)
        publish()
    }

    fun resetMixPoint() {
        manualMix = null
        publish()
    }

    fun toggleLoop(lenMs: Long) {
        val dur = lastPs?.track?.duration ?: return
        if (loopLen == lenMs) {
            loopLen = 0
        } else {
            loopLen = lenMs
            loopStart = estimatedPosition().coerceAtMost(dur - lenMs - 1500).coerceAtLeast(0)
        }
        publish()
    }

    fun toggleRepeat() {
        repeatTrack = !repeatTrack
        publish()
    }

    // ---------------------------------------------------------------- queue editing

    fun upcoming(): List<QueueEntry> =
        if (running && index + 1 < queue.size) queue.subList(index + 1, queue.size).toList() else emptyList()

    fun moveUpcoming(from: Int, to: Int) {
        val a = index + 1 + from
        val b = index + 1 + to
        if (a !in queue.indices || b !in queue.indices || a == b) return
        queue.add(b, queue.removeAt(a))
        edited()
    }

    fun removeUpcoming(i: Int) {
        val a = index + 1 + i
        if (a in queue.indices) {
            queue.removeAt(a)
            edited()
        }
    }

    fun playNext(t: TrackInfo) {
        if (!running) return
        queue.add(index + 1, QueueEntry.Track(t))
        edited()
    }

    fun addToQueue(t: TrackInfo) {
        if (!running) return
        queue.add(QueueEntry.Track(t))
        edited()
    }

    /** Someone asked for a song: mix into it now. */
    fun playNow(t: TrackInfo) {
        if (!running) return
        queue.add(index + 1, QueueEntry.Track(t))
        leaveBlock()
        edited()
        mixToNext(manual = true)
    }

    fun jumpToUpcoming(i: Int) {
        if (!running) return
        if (i != 0) moveUpcoming(i, 0)
        leaveBlock()
        edited()
        mixToNext(manual = true)
    }

    private fun leaveBlock() {
        val e = queue.getOrNull(index)
        if (e is QueueEntry.Block) blockPlayed = e.count
    }

    private fun edited() {
        bump()
        queueNextNative()
        publish()
    }

    private fun bump() {
        queueVersion++
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
        basePos = ps.playbackPosition
        baseAt = SystemClock.elapsedRealtime()
        val t = ps.track
        val uri = t?.uri
        if (t != null && uri != null && uri != currentUri) {
            currentUri = uri
            planTrack(uri, t.duration)
            refreshCrossfade()
            onTrackChanged(uri, prevPos, prevDur)
        }
        val img = t?.imageUri
        if (img != null && img.raw != lastImage) {
            lastImage = img.raw
            remote?.imagesApi?.getImage(img, Image.Dimension.LARGE)
                ?.setResultCallback { bmp -> scope.launch { art = bmp; publish() } }
        }
        publish()
    }

    private fun onTrackChanged(uri: String, prevPos: Long, prevDur: Long) {
        // During our own dip transitions the change is expected.
        if (!running || phase == Phase.SWITCHING || phase == Phase.FADING_OUT) return
        val queued = nativeQueued
        if (queued != null && uri == queued) {
            // Spotify moved on to the track we queued (with its own crossfade).
            index++
            blockPlayed = 0
            nativeQueued = null
            ensureAhead()
            bump()
            queueNextNative()
            return
        }
        when (val e = queue.getOrNull(index)) {
            is QueueEntry.Block -> {
                blockPlayed++
                queueNextNative()
                return
            }
            is QueueEntry.Track -> if (e.track.uri == uri) return
            null -> {}
        }
        if (phase == Phase.OVERLAP) return
        if (prevDur > 0 && prevPos > prevDur - 6000) {
            // The track ran out and Spotify autoplayed something else: get back on the mix.
            dipTransition(withFadeOut = false)
        } else {
            status = "Playing a track picked in Spotify. The mix continues after it."
        }
    }

    private fun planTrack(uri: String, dur: Long) {
        planUri = uri
        plannedMix = if (dur > 0) computeMixPoint(dur) else 0
        manualMix = null
        loopLen = 0
        detector?.reset()
    }

    private fun transitionLenMs(): Long =
        if (settings.style == TransitionStyle.OVERLAP && crossfadeMs > 0) crossfadeMs.toLong()
        else settings.fadeOutSec * 1000L

    private fun endBasedMix(dur: Long): Long = dur - settings.mixOutEarlySec * 1000L - transitionLenMs()

    private fun computeMixPoint(dur: Long): Long {
        val s = settings
        val end = endBasedMix(dur)
        var mp = end
        if (s.randomLength) {
            val lo = minOf(s.minPlayPct, s.maxPlayPct)
            val hi = maxOf(s.minPlayPct, s.maxPlayPct)
            val pct = if (hi > lo) rnd.nextInt(lo, hi + 1) else lo
            mp = minOf(end, dur * pct / 100)
            mp = maxOf(mp, minOf(45_000L, end))
        }
        return mp.coerceIn(minOf(5000L, dur / 2), maxOf(dur - 2000, 5000L))
    }

    private fun effectiveMix(): Long = manualMix ?: plannedMix

    /** Where the smart detector may switch: around the planned point, never past the end. */
    private fun smartWindow(dur: Long): Pair<Long, Long> {
        val start = (plannedMix - 12_000).coerceAtLeast(0)
        val end = minOf(plannedMix + 12_000, dur - transitionLenMs() - 1000)
        return start to end
    }

    private fun smartActive(): Boolean =
        manualMix == null && settings.smartSwitch && detector?.hasData() == true

    private fun tick() {
        val ps = lastPs
        if (ps != null && !ps.isPaused) publish()
        if (!running || phase != Phase.PLAYING || ps == null) return
        val t = ps.track ?: return
        val dur = t.duration
        if (dur <= 0) return
        if (planUri != t.uri) planTrack(t.uri, dur)
        val pos = estimatedPosition()
        if (ps.isPaused) {
            // Spotify stopped by itself at the end of something: keep the music going.
            if (pos >= dur - 1500 && SystemClock.elapsedRealtime() - baseAt > 1500) dipTransition(withFadeOut = false)
            return
        }
        if (loopLen > 0) {
            if (pos >= loopStart + loopLen) seekLocal(loopStart)
            return
        }
        if (pos > dur + 4000) {
            dipTransition(withFadeOut = false)
            return
        }
        if (smartActive()) {
            val (ws, we) = smartWindow(dur)
            if (we > ws) {
                if (pos >= we || (pos >= ws && detector?.isLull() == true)) trigger()
                return
            }
        }
        if (pos >= effectiveMix()) trigger()
    }

    private fun trigger() {
        if (repeatTrack) repeatCurrent() else mixToNext(manual = false)
    }

    private fun seekLocal(ms: Long) {
        basePos = ms
        baseAt = SystemClock.elapsedRealtime()
        remote?.playerApi?.seekTo(ms)
    }

    private fun estimatedPosition(): Long {
        val ps = lastPs ?: return 0
        if (ps.isPaused) return basePos
        return basePos + (SystemClock.elapsedRealtime() - baseAt)
    }

    // ---------------------------------------------------------------- transitions

    private fun mixToNext(manual: Boolean) {
        if (!running || transitionJob?.isActive == true) return
        if (settings.style == TransitionStyle.OVERLAP && crossfadeMs > 0 && overlapReady()) {
            startOverlap()
        } else {
            dipTransition(fadeOutMs = if (manual) minOf(settings.fadeOutSec * 1000L, 3000L) else null)
        }
    }

    /** True when Spotify's own next track is the one we want, so its crossfade can do the mix. */
    private fun overlapReady(): Boolean {
        val cur = queue.getOrNull(index) ?: return false
        if (cur is QueueEntry.Block && blockPlayed + 1 < cur.count) return nativeQueued == null
        val next = queue.getOrNull(index + 1) as? QueueEntry.Track ?: return false
        return nativeQueued == next.track.uri
    }

    /**
     * Jump the current track to its last few seconds so Spotify's crossfade starts now and
     * overlaps it with the next track: both play at once, one fading out, one fading in.
     */
    private fun startOverlap() {
        val dur = lastPs?.track?.duration ?: return
        val fromUri = currentUri
        val xf = crossfadeMs.toLong()
        transitionJob = scope.launch {
            phase = Phase.OVERLAP
            status = "Crossfading…"
            publish()
            val base = fader.current()
            try {
                val target = dur - xf - 300
                if (estimatedPosition() < target - 1500) {
                    val dip = (base * 0.7f).roundToInt()
                    fader.ramp(base, dip, 200)
                    seekLocal(target)
                    delay(150)
                    fader.ramp(dip, base, 300)
                }
                awaitTrack(xf + 6000) { it != null && it != fromUri }
                status = ""
            } finally {
                fader.set(base)
                if (running) phase = Phase.PLAYING
                publish()
            }
        }
    }

    private fun dipTransition(withFadeOut: Boolean = true, fadeOutMs: Long? = null, jumpTo: Int? = null) {
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
                        seekLocal(s.skipIntroSec * 1000L)
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

    /** Repeat button: fade out, start the same track again, fade in. */
    private fun repeatCurrent() {
        if (transitionJob?.isActive == true) return
        val s = settings
        transitionJob = scope.launch {
            val baseVol = fader.current()
            val floor = (baseVol * s.floorPct / 100f).roundToInt()
            try {
                phase = Phase.FADING_OUT
                status = "Repeating…"
                publish()
                fader.ramp(baseVol, floor, 1500)
                seekLocal(s.skipIntroSec * 1000L)
                delay(300)
                phase = Phase.FADING_IN
                fader.ramp(floor, baseVol, maxOf(s.fadeInSec, 1) * 1000L)
                status = ""
            } finally {
                fader.set(baseVol)
                val uri = currentUri
                val dur = lastPs?.track?.duration ?: 0L
                if (uri != null) planTrack(uri, dur)
                if (running) phase = Phase.PLAYING
                publish()
            }
        }
    }

    private suspend fun advance(r: SpotifyAppRemote, jumpTo: Int?): Boolean {
        val fromUri = currentUri
        val entry = queue.getOrNull(index)
        val next = queue.getOrNull(index + 1)
        val queued = nativeQueued
        nativeQueued = null

        if (jumpTo == null && queued != null && next is QueueEntry.Track && next.track.uri == queued) {
            // The next track is already in Spotify's queue: skip into it so it's consumed.
            r.playerApi.skipNext()
            val ok = awaitTrack { it == queued }
            index++
            blockPlayed = 0
            ensureAhead()
            bump()
            if (ok) queueNextNative()
            return ok
        }
        if (jumpTo == null && queued == null && entry is QueueEntry.Block && blockPlayed + 1 < entry.count) {
            blockPlayed++
            r.playerApi.skipNext()
            return awaitTrack { it != null && it != fromUri }
        }
        if (queued != null) {
            // Something we queued in Spotify is no longer next: play through it while the volume is down.
            r.playerApi.skipNext()
            awaitTrack(3000) { it == queued }
        }
        index = jumpTo ?: (index + 1)
        ensureAhead()
        if (index !in queue.indices) index = 0
        bump()
        return playCurrent(r, currentUri)
    }

    private suspend fun playCurrent(r: SpotifyAppRemote, fromUri: String?): Boolean {
        blockPlayed = 0
        val ok = when (val e = queue.getOrNull(index) ?: return false) {
            is QueueEntry.Track -> {
                val uri = e.track.uri
                r.playerApi.play(uri).setErrorCallback { t -> onPlayError(t) }
                val started = awaitTrack { it == uri && (uri != fromUri || estimatedPosition() < 4000) }
                if (started && uri == fromUri) planTrack(uri, lastPs?.track?.duration ?: 0L)
                started
            }
            is QueueEntry.Block -> {
                r.playerApi.setShuffle(mode != PlayMode.SEQUENCE)
                r.playerApi.play(e.playlist.uri).setErrorCallback { t -> onPlayError(t) }
                awaitTrack { it != null && it != fromUri }
            }
        }
        ensureAhead()
        if (ok) queueNextNative()
        return ok
    }

    /** Keeps the queue endless: append a fresh round when we're near the end. */
    private fun ensureAhead() {
        if (queue.size - index > 2) return
        val more: List<QueueEntry> = when {
            mode == PlayMode.CUSTOM && sources.isEmpty() -> customTracks.map { QueueEntry.Track(it) }
            mode == PlayMode.CUSTOM -> MixBuilder.build(sources, settings.copy(mode = PlayMode.SHUFFLE))
            else -> MixBuilder.build(sources, settings.copy(mode = mode))
        }
        if (more.isNotEmpty()) {
            queue.addAll(more)
            bump()
        }
    }

    private fun onPlayError(t: Throwable) {
        scope.launch {
            error = "Spotify couldn't play that: ${t.message}"
            publish()
        }
    }

    /** In overlap mode, keep the next track queued in Spotify so its crossfade can blend into it. */
    private fun queueNextNative() {
        if (!running || settings.style != TransitionStyle.OVERLAP || nativeQueued != null) return
        val ready = when (val cur = queue.getOrNull(index)) {
            is QueueEntry.Track -> true
            is QueueEntry.Block -> blockPlayed + 1 >= cur.count
            null -> false
        }
        if (!ready) return
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

    private fun publish() {
        val ps = lastPs
        val t = ps?.track
        val dur = t?.duration ?: 0L
        val e = queue.getOrNull(index)
        val from = minOf(index + 1, queue.size)
        val to = minOf(index + 9, queue.size)
        val smart = running && smartActive() && dur > 0
        val window = if (smart) smartWindow(dur) else (0L to 0L)
        val smartInfo = when {
            !settings.smartSwitch -> null
            !running -> null
            detectorFailed -> "Smart switch unavailable (allow microphone access in settings)"
            detector?.hasData() == true -> if (manualMix != null) "Smart switch paused: you set the mix point" else "Smart switch: listening for a break"
            else -> "Smart switch: no audio data yet, using the planned point"
        }
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
            mixPointMs = if (running && dur > 0) effectiveMix() else 0,
            mixPointManual = manualMix != null,
            smartStartMs = window.first,
            smartEndMs = window.second,
            art = art,
            phase = phase,
            status = status,
            source = when (e) {
                is QueueEntry.Track -> e.track.source
                is QueueEntry.Block -> "${e.playlist.name} · ${(blockPlayed + 1).coerceIn(1, e.count)}/${e.count}"
                null -> ""
            },
            upNext = if (running) queue.subList(from, to).map { it.label() } else emptyList(),
            upcomingCount = if (running) queue.size - from else 0,
            queueVersion = queueVersion,
            error = error,
            crossfadeMs = crossfadeMs,
            loopLenMs = loopLen,
            loopStartMs = loopStart,
            repeatTrack = repeatTrack,
            smartInfo = smartInfo,
        )
    }
}
