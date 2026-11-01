package com.mansoor.djapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("djapp", Context.MODE_PRIVATE)
    }

    private fun str(key: String) = sp.getString(key, "") ?: ""
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()

    var clientId: String
        get() = str("clientId")
        set(v) = put("clientId", v)
    var accessToken: String
        get() = str("accessToken")
        set(v) = put("accessToken", v)
    var refreshToken: String
        get() = str("refreshToken")
        set(v) = put("refreshToken", v)
    var codeVerifier: String
        get() = str("codeVerifier")
        set(v) = put("codeVerifier", v)
    var grantedScopes: String
        get() = str("grantedScopes")
        set(v) = put("grantedScopes", v)
    var tokenExpiry: Long
        get() = sp.getLong("tokenExpiry", 0)
        set(v) = sp.edit().putLong("tokenExpiry", v).apply()

    var selected: Set<String>
        get() = sp.getStringSet("selected", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("selected", v).apply()

    var settings: MixSettings
        get() {
            val raw = str("settings")
            if (raw.isBlank()) return MixSettings()
            return try {
                val j = JSONObject(raw)
                val d = MixSettings()
                MixSettings(
                    mode = runCatching { PlayMode.valueOf(j.getString("mode")) }.getOrDefault(d.mode),
                    style = runCatching { TransitionStyle.valueOf(j.getString("style")) }.getOrDefault(d.style),
                    fadeOutSec = j.optInt("fadeOutSec", d.fadeOutSec),
                    fadeInSec = j.optInt("fadeInSec", d.fadeInSec),
                    floorPct = j.optInt("floorPct", d.floorPct),
                    mixOutEarlySec = j.optInt("mixOutEarlySec", d.mixOutEarlySec),
                    skipIntroSec = j.optInt("skipIntroSec", d.skipIntroSec),
                    avoidSameArtist = j.optBoolean("avoidSameArtist", d.avoidSameArtist),
                    alternatePlaylists = j.optBoolean("alternatePlaylists", d.alternatePlaylists),
                    blockSize = j.optInt("blockSize", d.blockSize),
                    randomLength = j.optBoolean("randomLength", d.randomLength),
                    minPlayPct = j.optInt("minPlayPct", d.minPlayPct),
                    maxPlayPct = j.optInt("maxPlayPct", d.maxPlayPct),
                    smartSwitch = j.optBoolean("smartSwitch", d.smartSwitch),
                    crossfadeSec = j.optInt("crossfadeSec", d.crossfadeSec),
                )
            } catch (e: Exception) {
                MixSettings()
            }
        }
        set(s) {
            val j = JSONObject()
                .put("mode", s.mode.name)
                .put("style", s.style.name)
                .put("fadeOutSec", s.fadeOutSec)
                .put("fadeInSec", s.fadeInSec)
                .put("floorPct", s.floorPct)
                .put("mixOutEarlySec", s.mixOutEarlySec)
                .put("skipIntroSec", s.skipIntroSec)
                .put("avoidSameArtist", s.avoidSameArtist)
                .put("alternatePlaylists", s.alternatePlaylists)
                .put("blockSize", s.blockSize)
                .put("randomLength", s.randomLength)
                .put("minPlayPct", s.minPlayPct)
                .put("maxPlayPct", s.maxPlayPct)
                .put("smartSwitch", s.smartSwitch)
                .put("crossfadeSec", s.crossfadeSec)
            put("settings", j.toString())
        }
}
