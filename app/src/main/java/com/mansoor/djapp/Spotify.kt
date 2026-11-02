package com.mansoor.djapp

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

object Http {
    fun request(method: String, url: String, headers: Map<String, String>, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 20000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }
}

/** Authorization Code + PKCE flow (no client secret needed). */
object SpotifyAuth {
    const val REDIRECT_URI = "djapp://callback"
    // app-remote-control is granted here so App Remote doesn't need Spotify's own auth
    // screen, which Android blocks when Spotify is in the background.
    private const val SCOPES =
        "playlist-read-private playlist-read-collaborative user-library-read app-remote-control"
    private val mutex = Mutex()

    fun isLoggedIn() = Prefs.refreshToken.isNotBlank()

    fun hasRemoteScope() = Prefs.grantedScopes.split(" ").contains("app-remote-control")

    fun logout() {
        Prefs.accessToken = ""
        Prefs.refreshToken = ""
        Prefs.tokenExpiry = 0
    }

    fun authUri(): Uri {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        val verifier = (1..64).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        Prefs.codeVerifier = verifier
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", Prefs.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .build()
    }

    suspend fun exchange(redirect: Uri) {
        redirect.getQueryParameter("error")?.let { throw IOException("Spotify said: $it") }
        val code = redirect.getQueryParameter("code") ?: throw IOException("No code in redirect")
        tokenRequest(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to Prefs.clientId,
                "code_verifier" to Prefs.codeVerifier,
            )
        )
    }

    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        if (!forceRefresh && Prefs.accessToken.isNotBlank() && System.currentTimeMillis() < Prefs.tokenExpiry) {
            return@withLock Prefs.accessToken
        }
        if (Prefs.refreshToken.isBlank()) throw IOException("Not logged in to Spotify")
        tokenRequest(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to Prefs.refreshToken,
                "client_id" to Prefs.clientId,
            )
        )
        Prefs.accessToken
    }

    private suspend fun tokenRequest(params: Map<String, String>) = withContext(Dispatchers.IO) {
        val body = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val (code, text) = Http.request(
            "POST", "https://accounts.spotify.com/api/token",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"), body,
        )
        if (code !in 200..299) {
            if (text.contains("invalid_grant")) logout()
            throw IOException("Spotify token error $code: ${text.take(200)}")
        }
        val j = JSONObject(text)
        Prefs.accessToken = j.getString("access_token")
        Prefs.tokenExpiry = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000
        val refresh = j.optString("refresh_token", "")
        if (refresh.isNotBlank()) Prefs.refreshToken = refresh
        val scope = j.optString("scope", "")
        if (scope.isNotBlank()) Prefs.grantedScopes = scope
    }
}

object SpotifyApi {
    private const val BASE = "https://api.spotify.com/v1"
    private const val MAX_TRACKS = 3000

    class ApiException(val code: Int, msg: String) : IOException(msg)

    private suspend fun get(path: String): JSONObject {
        val url = if (path.startsWith("http")) path else BASE + path
        var token = SpotifyAuth.accessToken()
        repeat(4) { attempt ->
            val (code, text) = withContext(Dispatchers.IO) {
                Http.request("GET", url, mapOf("Authorization" to "Bearer $token"))
            }
            if (code in 200..299) {
                return withContext(Dispatchers.Default) { if (text.isBlank()) JSONObject() else JSONObject(text) }
            }
            if (code == 401 && attempt == 0) {
                token = SpotifyAuth.accessToken(forceRefresh = true)
            } else if (code == 429 || code >= 500) {
                delay(1500L * (attempt + 1))
            } else {
                throw ApiException(code, "Spotify API $code: ${text.take(200)}")
            }
        }
        throw IOException("Spotify API is not responding, try again")
    }

    private fun JSONObject.nextUrl(): String? =
        if (isNull("next")) null else optString("next").ifBlank { null }

    data class Profile(val id: String, val name: String)

    data class SearchResults(val tracks: List<TrackInfo>, val playlists: List<PlaylistInfo>, val hasMore: Boolean)

    suspend fun me(): Profile {
        val j = get("/me")
        val id = j.optString("id")
        return Profile(id, j.optString("display_name").ifBlank { id })
    }

    private fun parsePlaylist(p: JSONObject, myId: String): PlaylistInfo {
        val owner = p.optJSONObject("owner")
        val ownerId = owner?.optString("id") ?: ""
        val total = (p.optJSONObject("items") ?: p.optJSONObject("tracks"))?.optInt("total", 0) ?: 0
        return PlaylistInfo(
            id = p.optString("id"),
            uri = p.optString("uri"),
            name = p.optString("name"),
            imageUrl = p.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
            owner = if (ownerId == myId) "You" else (owner?.optString("display_name")?.ifBlank { null } ?: ownerId),
            total = total,
            readable = ownerId == myId || p.optBoolean("collaborative"),
        )
    }

    private fun parseTrack(t: JSONObject, source: String): TrackInfo? {
        val uri = t.optString("uri")
        if (!uri.startsWith("spotify:track:") && !uri.startsWith("spotify:episode:")) return null
        val artists = t.optJSONArray("artists")
        val names = mutableListOf<String>()
        if (artists != null) {
            for (a in 0 until artists.length()) {
                artists.optJSONObject(a)?.optString("name")?.let { names += it }
            }
        }
        return TrackInfo(
            uri = uri,
            name = t.optString("name"),
            artist = names.joinToString(", ").ifBlank { t.optJSONObject("show")?.optString("name") ?: "" },
            artistKey = (names.firstOrNull() ?: "").lowercase(),
            durationMs = t.optLong("duration_ms"),
            source = source,
        )
    }

    suspend fun playlists(myId: String): List<PlaylistInfo> {
        val likedTotal = runCatching { get("/me/tracks?limit=1").optInt("total", 0) }.getOrDefault(0)
        val out = mutableListOf(
            PlaylistInfo(LIKED_ID, "spotify:user:$myId:collection", "Liked Songs", null, "You", likedTotal, true)
        )
        var next: String? = "/me/playlists?limit=50"
        while (next != null) {
            val j = get(next)
            val items = j.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val p = items.optJSONObject(i) ?: continue
                    out += parsePlaylist(p, myId)
                }
            }
            next = j.nextUrl()
        }
        return out
    }

    /** Searches the whole Spotify catalog for songs and playlists (Spotify caps a page at 10). */
    suspend fun search(query: String, offset: Int, myId: String): SearchResults {
        val q = URLEncoder.encode(query, "UTF-8")
        val j = get("/search?q=$q&type=track,playlist&limit=10&offset=$offset")
        val tracks = mutableListOf<TrackInfo>()
        val playlists = mutableListOf<PlaylistInfo>()
        val tj = j.optJSONObject("tracks")
        val pj = j.optJSONObject("playlists")
        val tArr = tj?.optJSONArray("items")
        if (tArr != null) {
            for (i in 0 until tArr.length()) {
                val t = tArr.optJSONObject(i) ?: continue
                parseTrack(t, "Search")?.let { tracks += it }
            }
        }
        val pArr = pj?.optJSONArray("items")
        if (pArr != null) {
            for (i in 0 until pArr.length()) {
                val p = pArr.optJSONObject(i) ?: continue
                playlists += parsePlaylist(p, myId)
            }
        }
        val more = (tj?.nextUrl() != null) || (pj?.nextUrl() != null)
        return SearchResults(tracks, playlists, more)
    }

    /** Returns the playlist's tracks, or null if Spotify won't let us read them. */
    suspend fun tracks(p: PlaylistInfo): List<TrackInfo>? {
        val out = mutableListOf<TrackInfo>()
        var next: String? = if (p.id == LIKED_ID) "/me/tracks?limit=50" else "/playlists/${p.id}/items?limit=50"
        var triedLegacy = false
        var page = 0
        while (next != null && out.size < MAX_TRACKS) {
            val url: String = next
            val j = try {
                get(url)
            } catch (e: ApiException) {
                if (page == 0 && e.code == 404 && !triedLegacy && url.contains("/items")) {
                    triedLegacy = true
                    next = "/playlists/${p.id}/tracks?limit=50"
                    continue
                }
                if (e.code == 403 || e.code == 404) return null
                throw e
            }
            page++
            val items = j.optJSONArray("items") ?: return if (page == 1) null else out
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                if (entry.optBoolean("is_local")) continue
                val t = entry.optJSONObject("item") ?: entry.optJSONObject("track") ?: continue
                parseTrack(t, p.name)?.let { out += it }
            }
            next = j.nextUrl()
        }
        return out
    }
}
