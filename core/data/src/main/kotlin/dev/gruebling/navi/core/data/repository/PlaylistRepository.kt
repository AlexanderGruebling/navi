package dev.gruebling.navi.core.data.repository

import androidx.room.withTransaction
import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.mapping.toInstant
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.database.dao.PlaylistDao
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.database.entity.PlaylistPrefsEntity
import dev.gruebling.navi.core.database.entity.PlaylistWithPrefs
import dev.gruebling.navi.core.model.CoverArtId
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistDetail
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.model.PlaylistSettings
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Playlists, stale-while-revalidate: `observe*` read Room and emit at once; `refresh*` fetch from the server and
 * write to Room, which makes the observers emit again.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val api: SubsonicApi,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val db: NaviDatabase,
) {
    private val grouping = PlaylistGrouping()

    /** All playlists in Home order: by group, then pinned, manual order, "Neu", server order. */
    fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll()
            .map { rows -> rows.map { it.toDomain() }.sortedWith(HOME_ORDER) }
            .distinctUntilChanged()

    fun observePlaylist(id: PlaylistId): Flow<PlaylistDetail?> =
        combine(
            playlistDao.observe(id.value),
            playlistDao.observeSongs(id.value),
            playlistDao.observeMosaic(id.value),
        ) { row, songs, mosaic ->
            row?.let { PlaylistDetail(it.toDomain(), songs.map { s -> s.toDomain() }, mosaic.map(::CoverArtId)) }
        }.distinctUntilChanged()

    /**
     * Reloads the playlist list. Also loads the tracks of playlists whose content may have changed: all non-smart
     * playlists whose `changed` moved (for the "Neu" badge) and smart playlists (so opening them shows fresh content
     * without the list jumping). [prefetchSmartLimit] caps the smart ones for huge setups.
     */
    suspend fun refreshPlaylists(prefetchSmartLimit: Int = 12): Result<Unit> = suspendRunCatching {
        val remote = api.getPlaylists().ok().playlists?.playlist.orEmpty()
        val cached = playlistDao.getAll().associateBy { it.id }
        playlistDao.replaceAll(
            remote.mapIndexed { i, p -> p.toEntity(position = i, contentSignature = cached[p.id]?.contentSignature) },
        )
        val toLoad = remote.filter { p ->
            val old = cached[p.id]
            when {
                p.validUntil != null -> true
                old?.contentSignature == null -> true
                else -> old.changed != p.toEntity(0, null).changed
            }
        }.let { list ->
            val (smart, plain) = list.partition { it.validUntil != null }
            plain + smart.take(prefetchSmartLimit)
        }
        val permits = Semaphore(4)
        coroutineScope {
            toLoad.map { p -> async { permits.withPermit { refreshPlaylist(PlaylistId(p.id)).getOrThrow() } } }.awaitAll()
        }
    }

    /** Reloads one playlist with its tracks. Smart playlists are re-evaluated by the server on this call. */
    suspend fun refreshPlaylist(id: PlaylistId): Result<Unit> = suspendRunCatching {
        val remote = api.getPlaylist(id.value).ok().playlist ?: error("Playlist ${id.value} missing in response")
        val songIds = remote.entry.map { it.id }
        val signature = PlaylistGrouping.signature(songIds)
        // One transaction: observers (home, the playlist screen) see one change instead of five.
        db.withTransaction {
            songDao.upsert(remote.entry.map { it.toEntity() })
            val position = playlistDao.get(id.value)?.position ?: Int.MAX_VALUE
            playlistDao.upsert(listOf(remote.toEntity(position, signature)))
            playlistDao.replaceSongs(id.value, songIds, signature)
            // First time we see this playlist's content: that is the baseline, not "new".
            val prefs = playlistDao.prefs(id.value)
            if (prefs?.seenSignature == null) {
                playlistDao.upsertPrefs((prefs ?: PlaylistPrefsEntity(id.value)).copy(seenSignature = signature))
            }
        }
    }

    /** The user opened the playlist: clears the "Neu" badge. */
    suspend fun markSeen(id: PlaylistId) {
        val signature = playlistDao.get(id.value)?.contentSignature ?: return
        val prefs = playlistDao.prefs(id.value) ?: PlaylistPrefsEntity(id.value)
        if (prefs.seenSignature != signature) playlistDao.upsertPrefs(prefs.copy(seenSignature = signature))
    }

    suspend fun updateSettings(id: PlaylistId, settings: PlaylistSettings) {
        val prefs = playlistDao.prefs(id.value) ?: PlaylistPrefsEntity(id.value)
        playlistDao.upsertPrefs(
            prefs.copy(
                groupOverride = settings.groupOverride?.name,
                pinned = settings.pinned,
                sortOrder = settings.sortOrder,
            ),
        )
    }

    /** Manual order within a group: [ids] in the wanted order get sortOrder 0, 1, 2, … */
    suspend fun reorder(ids: List<PlaylistId>) {
        ids.forEachIndexed { index, id ->
            val prefs = playlistDao.prefs(id.value) ?: PlaylistPrefsEntity(id.value)
            if (prefs.sortOrder != index) playlistDao.upsertPrefs(prefs.copy(sortOrder = index))
        }
    }

    private fun PlaylistWithPrefs.toDomain(): Playlist {
        val p = playlist
        val isSmart = p.validUntil != null
        val override = groupOverride?.let { runCatching { PlaylistGroup.valueOf(it) }.getOrNull() }
        val group = grouping.groupOf(
            name = p.name,
            comment = p.comment,
            isSmart = isSmart,
            override = override,
        )
        return Playlist(
            id = PlaylistId(p.id),
            name = p.name,
            comment = PlaylistGrouping.displayComment(p.comment),
            owner = p.owner,
            songCount = p.songCount,
            durationSec = p.duration,
            coverArt = p.coverArt?.let(::CoverArtId),
            changed = p.changed.toInstant(),
            isSmart = isSmart,
            group = group,
            pinned = pinned == true,
            sortOrder = sortOrder,
            hasNewContent = !isSmart && p.contentSignature != null && seenSignature != null &&
                p.contentSignature != seenSignature,
            groupOverride = override,
        )
    }

    private companion object {
        val HOME_ORDER = compareBy<Playlist>(
            { it.group.ordinal },
            { !it.pinned },
            { it.sortOrder ?: Int.MAX_VALUE },
            { !it.hasNewContent },
        )
    }
}
