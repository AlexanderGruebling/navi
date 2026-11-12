package dev.gruebling.navi.core.download

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The downloaded songs: one file per song id in [dir]. A song counts as downloaded when its file exists; files are
 * written under a temporary name and renamed when complete, so a cut-off download never looks finished.
 *
 * The files have no extension: ExoPlayer recognises the format from the content.
 */
class DownloadFiles(private val dir: File, private val io: CoroutineDispatcher) {
    /** null until the directory was listed once. */
    private val ids = MutableStateFlow<Set<String>?>(null)

    /** Ids of all downloaded songs; updates when a download finishes or files are deleted. */
    val downloaded: Flow<Set<String>> = ids.onStart { ensureScanned() }.filterNotNull()

    /** The finished file for [songId], or null. Cheap enough for the player's loader threads. */
    fun fileIfDownloaded(songId: String): File? = fileFor(songId).takeIf { it.isFile }

    suspend fun downloadedNow(): Set<String> {
        ensureScanned()
        return ids.value.orEmpty()
    }

    /**
     * Writes [songId] through [write], which fills the temporary file it gets. On any exception the partial file is
     * deleted and the song stays not downloaded.
     */
    suspend fun write(songId: String, write: suspend (File) -> Unit) {
        ensureScanned()
        val part = withContext(io) {
            dir.mkdirs()
            File(dir, fileName(songId) + PART_SUFFIX)
        }
        try {
            write(part)
            withContext(io) {
                if (!part.renameTo(fileFor(songId))) throw IOException("Could not store the download of $songId")
            }
        } finally {
            withContext(io) { part.delete() }
        }
        ids.update { it.orEmpty() + songId }
    }

    suspend fun delete(songIds: Collection<String>) = withContext(io) {
        ensureScanned()
        for (id in songIds) fileFor(id).delete()
        ids.update { it.orEmpty() - songIds.toSet() }
    }

    /** Deletes every song not in [keep], and leftover temporary files. */
    suspend fun deleteAllExcept(keep: Set<String>) = withContext(io) {
        ensureScanned()
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(PART_SUFFIX) || idOf(f.name) !in keep) f.delete()
        }
        ids.update { it.orEmpty().intersect(keep) }
    }

    suspend fun deleteAll() = withContext(io) {
        dir.deleteRecursively()
        ids.value = emptySet()
    }

    /** Bytes used by [songIds] (all downloads when null). */
    suspend fun sizeOf(songIds: Collection<String>? = null): Long = withContext(io) {
        if (songIds == null) dir.listFiles()?.sumOf { it.length() } ?: 0L
        else songIds.sumOf { fileFor(it).length() }
    }

    private suspend fun ensureScanned() {
        if (ids.value != null) return
        val found = withContext(io) {
            dir.listFiles().orEmpty().filter { !it.name.endsWith(PART_SUFFIX) }.map { idOf(it.name) }.toSet()
        }
        ids.update { current -> current?.plus(found) ?: found }
    }

    private fun fileFor(songId: String) = File(dir, fileName(songId))

    private companion object {
        const val PART_SUFFIX = ".part"

        fun fileName(songId: String): String = URLEncoder.encode(songId, Charsets.UTF_8.name())
        fun idOf(fileName: String): String = URLDecoder.decode(fileName, Charsets.UTF_8.name())
    }
}
