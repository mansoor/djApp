package com.mansoor.djapp

data class PlaylistInfo(
    val id: String,
    val uri: String,
    val name: String,
    val imageUrl: String?,
    val owner: String,
    val total: Int,
    /** Spotify only exposes track lists for playlists you own or collaborate on. */
    val readable: Boolean,
)

data class TrackInfo(
    val uri: String,
    val name: String,
    val artist: String,
    val artistKey: String,
    val durationMs: Long,
    val source: String,
)

sealed class QueueEntry {
    data class Track(val track: TrackInfo) : QueueEntry()

    /** A playlist whose tracks we can't read: Spotify plays [count] tracks from it as a context. */
    data class Block(val playlist: PlaylistInfo, val count: Int) : QueueEntry()

    fun label(): String = when (this) {
        is Track -> "${track.name} · ${track.artist}"
        is Block -> "$count tracks from \"${playlist.name}\""
    }
}

enum class PlayMode { SHUFFLE, SEQUENCE, CUSTOM }

enum class TransitionStyle {
    /** Spotify's own crossfade overlaps the two tracks; the app picks the moment. */
    OVERLAP,

    /** The app dips the volume around the track change. */
    DJ_FADE,
}

data class MixSettings(
    val mode: PlayMode = PlayMode.SHUFFLE,
    val style: TransitionStyle = TransitionStyle.DJ_FADE,
    val fadeOutSec: Int = 6,
    val fadeInSec: Int = 2,
    val floorPct: Int = 25,
    val mixOutEarlySec: Int = 0,
    val skipIntroSec: Int = 0,
    val avoidSameArtist: Boolean = true,
    val alternatePlaylists: Boolean = false,
    val blockSize: Int = 4,
    val randomLength: Boolean = true,
    val minPlayPct: Int = 50,
    val maxPlayPct: Int = 90,
    val smartSwitch: Boolean = false,
    /** Spotify crossfade length the user set in Spotify; 0 = trust auto-detection. */
    val crossfadeSec: Int = 0,
    val loopSec: Int = 8,
)

const val LIKED_ID = "__liked__"
