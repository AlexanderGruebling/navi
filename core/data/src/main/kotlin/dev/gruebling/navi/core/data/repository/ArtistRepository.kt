package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.dao.AlbumDao
import dev.gruebling.navi.core.database.dao.ArtistDao
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.database.entity.ArtistEntity
import dev.gruebling.navi.core.database.entity.ArtistInfoEntity
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.ArtistDetail
import dev.gruebling.navi.core.model.ArtistId
import dev.gruebling.navi.core.model.ArtistInfo
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.model.Artist as NetworkArtist
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class ArtistRepository @Inject constructor(
    private val api: SubsonicApi,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    suspend fun refreshArtists(): Result<Unit> = suspendRunCatching {
        val index = api.getArtists().ok().artists?.index.orEmpty()
        artistDao.replaceAll(index.flatMap { group -> group.artist.map { it.toEntity(indexLetter = group.name) } })
    }

    fun observeArtist(id: ArtistId): Flow<ArtistDetail?> =
        combine(artistDao.observe(id.value), albumDao.observeByArtist(id.value)) { artist, albums ->
            artist?.let { ArtistDetail(it.toDomain(), albums.map { a -> a.toDomain() }) }
        }.distinctUntilChanged()

    suspend fun refreshArtist(id: ArtistId): Result<Unit> = suspendRunCatching {
        val remote = api.getArtist(id.value).ok().artist ?: error("Artist ${id.value} missing in response")
        upsertKeepingIndex(listOf(remote))
        val existing = albumDao.getAll(remote.album.map { it.id }).associateBy({ it.id }, { it.syncedAt })
        albumDao.upsert(remote.album.map { it.toEntity(existing[it.id]) })
    }

    fun observeArtistInfo(id: ArtistId): Flow<ArtistInfo> =
        combine(
            artistDao.observeInfo(id.value),
            artistDao.observeTopSongs(id.value),
            artistDao.observeSimilar(id.value),
        ) { info, top, similar ->
            ArtistInfo(
                biography = info?.biography,
                lastFmUrl = info?.lastFmUrl,
                topSongs = top.map { it.toDomain() },
                similarArtists = similar.map { it.toDomain() },
            )
        }.distinctUntilChanged()

    /**
     * Loads biography, similar artists and top songs. The two calls fail independently (getTopSongs needs the
     * artist's name, and both depend on the server's Last.fm setup); the result is a failure if either failed.
     */
    suspend fun refreshArtistInfo(id: ArtistId, name: String): Result<Unit> = coroutineScope {
        val info = async {
            suspendRunCatching {
                val remote = api.getArtistInfo2(id.value).ok().artistInfo2
                val similar = remote?.similarArtist.orEmpty().filter { it.id != id.value }
                upsertKeepingIndex(similar)
                artistDao.replaceInfo(
                    ArtistInfoEntity(id.value, cleanBiography(remote?.biography), remote?.lastFmUrl?.ifBlank { null }),
                    similar.map { it.id },
                )
            }
        }
        val top = async {
            suspendRunCatching {
                val songs = api.getTopSongs(name).ok().topSongs?.song.orEmpty()
                songDao.upsert(songs.map { it.toEntity() })
                artistDao.replaceTopSongs(id.value, songs.map { it.id })
            }
        }
        val results = listOf(info.await(), top.await())
        results.firstOrNull { it.isFailure } ?: Result.success(Unit)
    }

    /** getArtist and getArtistInfo2 don't send the index letter; keep the one from the last index sync. */
    private suspend fun upsertKeepingIndex(remote: List<NetworkArtist>) {
        if (remote.isEmpty()) return
        val letters = artistDao.getAll(remote.map { it.id }).associateBy(ArtistEntity::id, ArtistEntity::indexLetter)
        artistDao.upsert(remote.map { it.toEntity(indexLetter = letters[it.id]) })
    }
}

private val lastFmLink = Regex("""<a\b[^>]*>\s*Read more on Last\.fm\s*</a>\.?""", RegexOption.IGNORE_CASE)

/** Last.fm biographies are HTML and end in a "Read more on Last.fm" link; the page shows its own link instead. */
internal fun cleanBiography(html: String?): String? {
    if (html.isNullOrBlank()) return null
    // Paragraphs come as plain newlines, which HTML parsing would collapse into spaces.
    val withoutLink = html.replace(lastFmLink, "").replace("\n", "<br>")
    val text = android.text.Html.fromHtml(withoutLink, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    return text.replace('\u00A0', ' ').replace(Regex("[ \t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n")
        .trim().ifEmpty { null }
}
