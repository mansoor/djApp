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

    /** The Spotify account the Web API login belongs to. */
    val profile = MutableStateFlow<SpotifyApi.Profile?>(null)

    /** Playlists found in search and ticked for the mix (they aren't in the user's library). */
    val extraPlaylists = MutableStateFlow<List<PlaylistInfo>>(emptyList())

    // Spotify catalog search
    val searchQuery = MutableStateFlow("")
    val searchTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val searchPlaylists = MutableStateFlow<List<PlaylistInfo>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchHasMore = MutableStateFlow(false)
    private var searchOffset = 0

    fun search(query: String, more: Boolean = false) {
        val q = query.trim()
        if (q.isBlank() || searchLoading.value) return
        if (!more) {
            searchQuery.value = q
            searchOffset = 0
            searchTracks.value = emptyList()
            searchPlaylists.value = emptyList()
        }
        scope.launch {
            searchLoading.value = true
            try {
                val me = profile.value ?: SpotifyApi.me().also { profile.value = it }
                val r = SpotifyApi.search(q, searchOffset, me.id)
                searchOffset += 10
                searchTracks.value = (searchTracks.value + r.tracks).distinctBy { it.uri }
                searchPlaylists.value = (searchPlaylists.value + r.playlists).distinctBy { it.id }
                searchHasMore.value = r.hasMore
            } catch (e: Exception) {
                message.value = "Search failed: ${e.message}"
            } finally {
                searchLoading.value = false
            }
        }
    }

    fun findPlaylist(id: String): PlaylistInfo? =
        playlists.value.firstOrNull { it.id == id }
            ?: extraPlaylists.value.firstOrNull { it.id == id }
            ?: searchPlaylists.value.firstOrNull { it.id == id }

    /** Everything that can be ticked for the mix: the library plus playlists added from search. */
    fun selectablePlaylists(): List<PlaylistInfo> = playlists.value + extraPlaylists.value

    /** Tick/untick a playlist; ones from search are remembered so they can be mixed. */
    fun toggleSelect(p: PlaylistInfo) {
        val inLibrary = playlists.value.any { it.id == p.id }
        val nowSelected = p.id !in selected.value
        toggle(p.id)
        if (!inLibrary) {
            extraPlaylists.value = if (nowSelected) {
                (extraPlaylists.value + p).distinctBy { it.id }
            } else {
                extraPlaylists.value.filter { it.id != p.id }
            }
        }
    }

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
                val me = SpotifyApi.me()
                profile.value = me
                playlists.value = SpotifyApi.playlists(me.id)
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
        profile.value = null
        extraPlaylists.value = emptyList()
        loggedIn.value = false
    }
}
