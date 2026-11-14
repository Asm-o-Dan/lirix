package com.lirix.app.feature.lyrics

import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ContentRejection
import com.lirix.app.storage.LyricsDao
import com.lirix.app.storage.RejectedContent
import kotlinx.coroutines.CancellationException

sealed interface RejectionOutcome {
    /** Another source supplied the content. */
    data class Replaced(val rejected: RejectedContent, val newSourceId: String) : RejectionOutcome
    /** The wrong content is gone and no other source has it (or there is no network now). */
    data class NoMoreSources(val rejected: RejectedContent) : RejectionOutcome
    /** Nothing automatic to reject: no content, or it is the user's own. */
    data object NotAvailable : RejectionOutcome
}

/**
 * "Wrong lyrics" / "Wrong chords": records the rejection of the current automatic source,
 * removes only that content and asks the shared automatic lookup for the next source.
 * Every step is keyed by the track the action was started for (AGENTS 7.2).
 */
class ContentRejector(
    private val lyricsDao: LyricsDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lookup: suspend (trackKey: String, target: LookupTarget) -> Unit
) {
    suspend fun reject(trackKey: String, chords: Boolean): RejectionOutcome {
        val rejected = (if (chords) lyricsDao.rejectAutomaticChords(trackKey, clock()) else lyricsDao.rejectAutomaticLyrics(trackKey, clock()))
            ?: return RejectionOutcome.NotAvailable
        try {
            lookup(trackKey, if (chords) LookupTarget.CHORDS else LookupTarget.LYRICS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The rejection is already stored; a failed search is reported as "nothing else found".
        }
        val cache = lyricsDao.getLyrics(trackKey)
        val source = if (chords) ContentRejection.chordsSource(cache) else ContentRejection.lyricsSource(cache)
        return if (source != null) RejectionOutcome.Replaced(rejected, source) else RejectionOutcome.NoMoreSources(rejected)
    }

    /** Brings the rejected content back. False when the user has entered their own content since. */
    suspend fun undo(rejected: RejectedContent): Boolean = lyricsDao.undoContentRejection(rejected)

    companion object {
        fun forDb(db: AppDatabase, networkUsable: () -> Boolean): ContentRejector =
            ContentRejector(db.lyricsDao()) { key, target ->
                AutomaticLookupCoordinator.get(db).lookup(key, target, networkUsable())
            }
    }
}
