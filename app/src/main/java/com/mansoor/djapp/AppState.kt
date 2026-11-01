package com.mansoor.djapp

import android.app.Application
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DjApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        Prefs.init(this)
        DjEngine.init(this)
    }
}

/** Library / login / settings / custom-queue state shared by the UI. */
object AppState {
    private val scope = MainScope()

    val loggedIn = MutableStateFlow(SpotifyAuth.isLoggedIn())
    val playlists = MutableStateFlow<List<PlaylistInfo>>(emptyList())
    val loadingPlaylists = MutableStateFlow(false)
    val selected = MutableStateFlow(Prefs.selected)
    val settings = MutableStateFlow(Prefs.settings)
    val message = MutableStateFlow<String?>(null)
    val problemReport = MutableStateFlow<String?>(null)

    /** Playlist id -> tracks, or null when Spotify won't share that playlist's tracks. */
    val trackCache = MutableStateFlow<Map<String, List<TrackInfo>?>>(emptyMap())

    /** Songs picked by hand for Custom mode. */
    val customQueue = MutableStateFlow<List<TrackInfo>>(emptyList())

    fun checkLastProblem(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            val report = withContext(Dispatchers.IO) { Diagnostics.takeLastProblem(app) }
            if (report != null) problemReport.value = report
        }
    }

    fun handleRedirect(uri: Uri) {
        scope.launch {
            try {
                SpotifyAuth.exchange(uri)
                loggedIn.value = true
                refreshPlaylists()
            } catch (e: Exception) {
                message.value = "Spotify login failed: ${e.message}"
            }
        }
    }

    fun refreshPlaylists() {
        if (loadingPlaylists.value) return
        scope.launch {
            loadingPlaylists.value = true
            try {
                playlists.value = SpotifyApi.playlists()
                trackCache.value = emptyMap()
            } catch (e: Exception) {
                message.value = "Couldn't load playlists: ${e.message}"
                if (!SpotifyAuth.isLoggedIn()) loggedIn.value = false
            } finally {
                loadingPlaylists.value = false
            }
        }
    }

    /** Loads (and caches) a playlist's tracks. Network errors aren't cached. */
    suspend fun loadTracks(p: PlaylistInfo): List<TrackInfo>? {
        val cache = trackCache.value
        if (cache.containsKey(p.id)) return cache[p.id]
        return try {
            val tracks = SpotifyApi.tracks(p)
            trackCache.value = trackCache.value + (p.id to tracks)
            tracks
        } catch (e: Exception) {
            message.value = "Couldn't load \"${p.name}\": ${e.message}"
            null
        }
    }

    fun ensureTracks(p: PlaylistInfo) {
        if (trackCache.value.containsKey(p.id)) return
        scope.launch { loadTracks(p) }
    }

    fun toggle(id: String) {
        val s = selected.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        selected.value = s
        Prefs.selected = s
    }

    fun updateSettings(s: MixSettings) {
        settings.value = s
        Prefs.settings = s
        DjEngine.onSettingsChanged()
    }

    fun addCustom(t: TrackInfo) {
        customQueue.value = customQueue.value + t
    }

    fun removeCustom(i: Int) {
        val l = customQueue.value.toMutableList()
        if (i in l.indices) l.removeAt(i)
        customQueue.value = l
    }

    fun moveCustom(from: Int, to: Int) {
        val l = customQueue.value.toMutableList()
        if (from !in l.indices || to !in l.indices) return
        l.add(to, l.removeAt(from))
        customQueue.value = l
    }

    fun clearCustom() {
        customQueue.value = emptyList()
    }

    fun logout() {
        DjEngine.stop()
        SpotifyAuth.logout()
        playlists.value = emptyList()
        trackCache.value = emptyMap()
        loggedIn.value = false
    }
}
