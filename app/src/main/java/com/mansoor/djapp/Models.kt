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
}

enum class PlayMode { SHUFFLE, SEQUENCE }

enum class TransitionStyle { DJ_FADE, SPOTIFY_CROSSFADE }

data class MixSettings(
    val mode: PlayMode = PlayMode.SHUFFLE,
    val style: TransitionStyle = TransitionStyle.DJ_FADE,
    val fadeOutSec: Int = 6,
    val fadeInSec: Int = 3,
    val floorPct: Int = 15,
    val mixOutEarlySec: Int = 0,
    val skipIntroSec: Int = 0,
    val avoidSameArtist: Boolean = true,
    val alternatePlaylists: Boolean = false,
    val blockSize: Int = 4,
)

const val LIKED_ID = "__liked__"
