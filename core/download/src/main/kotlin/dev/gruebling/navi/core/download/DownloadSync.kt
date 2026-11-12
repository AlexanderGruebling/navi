package dev.gruebling.navi.core.download

import dev.gruebling.navi.core.datastore.DownloadSettingsStore
import dev.gruebling.navi.core.model.DownloadTarget
import dev.gruebling.navi.core.network.NotLoggedInException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * One download run: reloads the downloaded albums and playlists from the server, downloads the songs that are
 * missing and deletes the ones nothing needs any more.
 *
 * Deleting only happens when every target was reloaded: with an outdated song list (offline, server error) a song
 * that is still wanted could look unneeded.
 */
class DownloadSync @Inject constructor(
    private val settings: DownloadSettingsStore,
    private val library: DownloadLibrary,
    private val files: DownloadFiles,
    private val fetcher: SongFetcher,
) {
    enum class Outcome {
        /** Everything is downloaded. */
        Done,

        /** Some songs were refused by the server; the rest is downloaded. */
        Incomplete,

        /** The network went away; try again later. Finished songs stay. */
        Retry,

        /** Logged out. */
        Failed,
    }

    suspend fun run(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): Outcome {
        val refreshed = HashSet<DownloadTarget>()
        val failedTargets = HashSet<DownloadTarget>()
        val failedSongs = HashSet<String>()
        while (true) {
            // Targets added while this run is going on are reloaded too; removed ones stop counting.
            val targets = settings.targets.first()
            for (t in targets - refreshed - failedTargets) {
                if (library.refresh(t)) refreshed += t else failedTargets += t
            }
            val quality = settings.quality.first()
            val wanted = wanted(targets)
            val have = files.downloadedNow()
            onProgress(wanted.count { it in have }, wanted.size)
            val next = wanted.firstOrNull { it !in have && it !in failedSongs }
            if (next == null) {
                // Nothing left to load; settle unless the targets changed meanwhile.
                if (settings.targets.first() != targets) continue
                if (failedTargets.isEmpty()) files.deleteAllExcept(wanted)
                return if (failedSongs.isEmpty() && failedTargets.isEmpty()) Outcome.Done else Outcome.Incomplete
            }
            currentCoroutineContext().ensureActive()
            try {
                files.write(next) { fetcher.fetch(next, quality, it) }
            } catch (e: NotLoggedInException) {
                return Outcome.Failed
            } catch (e: SongUnavailableException) {
                failedSongs += next
            } catch (e: IOException) {
                return Outcome.Retry
            }
        }
    }

    /** All songs of [targets], albums first, without duplicates. */
    private suspend fun wanted(targets: Set<DownloadTarget>): Set<String> {
        val ordered = targets.sortedWith(compareBy({ it.kind }, { it.id }))
        return ordered.flatMapTo(LinkedHashSet()) { library.songIds(it) }
    }
}
