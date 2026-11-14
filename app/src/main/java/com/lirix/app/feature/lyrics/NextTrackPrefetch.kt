package com.lirix.app.feature.lyrics

import com.lirix.app.storage.AutomaticLookupCandidate
import java.util.Locale

/** One item of a player's queue as the player publishes it (title and subtitle are free text). */
data class QueueEntry(val queueId: Long, val title: String, val artist: String)

/**
 * Reads "what plays next" from a player's published queue. Many players publish no queue, or a
 * queue without a current-item id; then the answer is null and nothing is preloaded. Playback
 * is never touched: there is no skipping forward and back to peek at the next track.
 */
object PlayerQueue {
    /** Queue id players report when they do not track the current item. */
    const val UNKNOWN_QUEUE_ID = -1L

    fun next(queue: List<QueueEntry>?, activeQueueId: Long?, currentTitle: String, currentArtist: String): QueueEntry? {
        if (queue.isNullOrEmpty()) return null
        val byId = activeQueueId?.takeIf { it != UNKNOWN_QUEUE_ID }
            ?.let { id -> queue.indexOfFirst { it.queueId == id }.takeIf { it >= 0 } }
        // Without a usable id the current item is found by its text, and only when that is unambiguous.
        val index = byId ?: queue.indices.filter { sameTrack(queue[it], currentTitle, currentArtist) }.singleOrNull() ?: return null
        val candidate = queue.getOrNull(index + 1)?.takeIf { it.title.isNotBlank() } ?: return null
        // A queue that repeats the current track (repeat-one, duplicates) gives nothing new to load.
        return candidate.takeUnless { sameTrack(it, currentTitle, currentArtist) }
    }

    fun sameTrack(entry: QueueEntry, title: String, artist: String): Boolean =
        normalize(entry.title) == normalize(title) && artistsAgree(entry.artist, artist)

    /** Queue subtitles are often "Artist • Album" or empty, so containment counts as agreement. */
    fun artistsAgree(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        return x.isEmpty() || y.isEmpty() || x == y || x.contains(y) || y.contains(x)
    }

    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("""\s+"""), " ").trim()
}

/**
 * Small in-memory store of lookups made for the upcoming track. Nothing is written to the
 * library for a song that has not played; when the song really starts, the regular automatic
 * lookup takes the stored result instead of going to the network again.
 */
class NextTrackWarmCache(
    private val maxEntries: Int = 4,
    private val ttlMs: Long = 30 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val title: String, val artist: String, val candidate: AutomaticLookupCandidate, val storedAt: Long)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun put(title: String, artist: String, candidate: AutomaticLookupCandidate) {
        if (candidate.plain == null && candidate.synced == null && candidate.chords == null) return
        entries.removeAll { PlayerQueue.normalize(it.title) == PlayerQueue.normalize(title) && PlayerQueue.artistsAgree(it.artist, artist) }
        entries.addLast(Entry(title, artist, candidate, clock()))
        while (entries.size > maxEntries) entries.removeFirst()
    }

    @Synchronized
    fun contains(title: String, artist: String): Boolean = find(title, artist) != null

    /**
     * Removes and returns the stored lookup for this track, without the parts whose source the
     * user rejected for it. Null when nothing usable is stored.
     */
    @Synchronized
    fun take(title: String, artist: String, rejected: Set<String>): AutomaticLookupCandidate? {
        val entry = find(title, artist) ?: return null
        entries.remove(entry)
        val candidate = AutomaticLookupCandidate(
            plain = entry.candidate.plain?.takeIf { it.authorized(rejected) },
            synced = entry.candidate.synced?.takeIf { it.authorized(rejected) },
            chords = entry.candidate.chords?.takeIf { it.authorized(rejected) }
        )
        // Without words the regular lookup must still run; chords alone do not replace it.
        return candidate.takeIf { it.plain != null || it.synced != null }
    }

    @Synchronized
    fun clear() = entries.clear()

    private fun find(title: String, artist: String): Entry? {
        val now = clock()
        entries.removeAll { now - it.storedAt >= ttlMs }
        // The playing track's artist is exact here, so an empty stored artist is not enough to match.
        return entries.lastOrNull {
            PlayerQueue.normalize(it.title) == PlayerQueue.normalize(title) &&
                PlayerQueue.normalize(it.artist).isNotEmpty() && PlayerQueue.artistsAgree(it.artist, artist)
        }
    }

    companion object {
        val shared = NextTrackWarmCache()
    }
}
