package com.mansoor.djapp

import kotlin.random.Random

object MixBuilder {

    /**
     * Builds the play queue. [sources] pairs each selected playlist with its tracks,
     * or null when Spotify won't expose the tracks (those play as context blocks).
     */
    fun build(
        sources: List<Pair<PlaylistInfo, List<TrackInfo>?>>,
        s: MixSettings,
        rnd: Random = Random.Default,
    ): List<QueueEntry> {
        val seen = HashSet<String>()
        val deduped = sources.map { (p, tracks) -> p to tracks?.filter { seen.add(it.uri) } }

        if (s.mode == PlayMode.SEQUENCE) {
            return deduped.flatMap { (p, tracks) ->
                // Size unknown (Spotify hides it): play through until Spotify runs out.
                tracks?.map { QueueEntry.Track(it) } ?: listOf(QueueEntry.Block(p, if (p.total > 0) p.total else 1000))
            }
        }

        val readable = deduped.mapNotNull { it.second }.filter { it.isNotEmpty() }
        var tracks = if (s.alternatePlaylists && readable.size > 1) {
            roundRobin(readable.map { it.shuffled(rnd) })
        } else {
            readable.flatten().shuffled(rnd)
        }
        if (s.avoidSameArtist) tracks = spreadArtists(tracks)

        val out: MutableList<QueueEntry> = tracks.map { QueueEntry.Track(it) }.toMutableList()
        val blockSize = s.blockSize.coerceAtLeast(1)
        for ((p, t) in deduped) {
            if (t != null) continue
            val blocks = ((p.total + blockSize - 1) / blockSize).coerceIn(1, 40)
            repeat(blocks) { out.add(rnd.nextInt(out.size + 1), QueueEntry.Block(p, blockSize)) }
        }
        return out
    }

    private fun roundRobin(lists: List<List<TrackInfo>>): List<TrackInfo> {
        val out = ArrayList<TrackInfo>(lists.sumOf { it.size })
        val max = lists.maxOf { it.size }
        for (i in 0 until max) for (l in lists) if (i < l.size) out += l[i]
        return out
    }

    /** Greedy pass so the same artist doesn't play twice in a row. */
    private fun spreadArtists(list: List<TrackInfo>): List<TrackInfo> {
        val a = list.toMutableList()
        for (i in 1 until a.size) {
            val prev = a[i - 1].artistKey
            if (prev.isEmpty() || a[i].artistKey != prev) continue
            val j = (i + 1 until a.size).firstOrNull { a[it].artistKey != prev } ?: break
            val tmp = a[i]
            a[i] = a[j]
            a[j] = tmp
        }
        return a
    }
}
