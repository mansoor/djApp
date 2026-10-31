package com.mansoor.djapp

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spotify only plays one stream at a time, so DJ transitions are done by riding the
 * device media volume: an equal-power style fade out, the track switch, then a fade in.
 */
class VolumeFader(ctx: Context) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    val max: Int = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun current(): Int = am.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun set(v: Int) {
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, max), 0)
        } catch (_: SecurityException) {
            // Do Not Disturb can block volume changes; ignore.
        }
    }

    suspend fun ramp(from: Int, to: Int, durationMs: Long) {
        if (durationMs <= 0 || from == to) {
            set(to)
            return
        }
        val steps = (durationMs / 40).toInt().coerceAtLeast(1)
        val stepDelay = durationMs / steps
        val fadingOut = to < from
        var last = -1
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            // Fade out holds the level then drops away; fade in rises fast then settles.
            val shape = if (fadingOut) 1f - cos(t * PI.toFloat() / 2f) else sin(t * PI.toFloat() / 2f)
            val v = (from + (to - from) * shape).roundToInt()
            if (v != last) {
                set(v)
                last = v
            }
            delay(stepDelay)
        }
        set(to)
    }
}
