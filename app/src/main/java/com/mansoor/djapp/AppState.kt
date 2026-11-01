package com.mansoor.djapp

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DjApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        Prefs.init(this)
        DjEngine.init(this)
    }
}

/** Library / login / settings state shared by the UI. */
object AppState {
    private val scope = MainScope()

    val loggedIn = MutableStateFlow(SpotifyAuth.isLoggedIn())
    val playlists = MutableStateFlow<List<PlaylistInfo>>(emptyList())
    val loadingPlaylists = MutableStateFlow(false)
    val selected = MutableStateFlow(Prefs.selected)
    val settings = MutableStateFlow(Prefs.settings)
    val message = MutableStateFlow<String?>(null)
    val problemReport = MutableStateFlow<String?>(null)

    fun checkLastProblem(ctx: android.content.Context) {
        val app = ctx.applicationContext
        scope.launch {
            val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Diagnostics.takeLastProblem(app)
            }
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
            } catch (e: Exception) {
                message.value = "Couldn't load playlists: ${e.message}"
                if (!SpotifyAuth.isLoggedIn()) loggedIn.value = false
            } finally {
                loadingPlaylists.value = false
            }
        }
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
    }

    fun logout() {
        DjEngine.stop()
        SpotifyAuth.logout()
        playlists.value = emptyList()
        loggedIn.value = false
    }
}
