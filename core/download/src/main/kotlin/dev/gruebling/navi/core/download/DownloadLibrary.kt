package dev.gruebling.navi.core.download

import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.DownloadTarget
import dev.gruebling.navi.core.model.PlaylistId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What a download target contains, from the library cache. */
interface DownloadLibrary {
    /** Song ids of [target] in the cache, in order without duplicates; empty while unknown. */
    fun observeSongIds(target: DownloadTarget): Flow<List<String>>

    /** Name, owner/artist and cover of [target]; null while it is not in the cache. */
    fun observeInfo(target: DownloadTarget): Flow<TargetInfo?>

    /** Reloads [target] from the server; false when that failed. */
    suspend fun refresh(target: DownloadTarget): Boolean

    suspend fun songIds(target: DownloadTarget): List<String> = observeSongIds(target).first()
}

data class TargetInfo(val name: String, val subtitle: String?, val coverArt: String?)

class RepositoryDownloadLibrary @Inject constructor(
    private val albums: AlbumRepository,
    private val playlists: PlaylistRepository,
) : DownloadLibrary {
    override fun observeSongIds(target: DownloadTarget): Flow<List<String>> = when (target.kind) {
        DownloadTarget.Kind.Album -> albums.observeAlbum(AlbumId(target.id)).map { d -> d?.songs.orEmpty().map { it.id.value } }
        DownloadTarget.Kind.Playlist -> playlists.observePlaylist(PlaylistId(target.id)).map { d ->
            d?.songs.orEmpty().map { it.id.value }.distinct()
        }
    }

    override fun observeInfo(target: DownloadTarget): Flow<TargetInfo?> = when (target.kind) {
        DownloadTarget.Kind.Album -> albums.observeAlbum(AlbumId(target.id)).map { d ->
            d?.album?.let { TargetInfo(it.name, it.artist, it.coverArt?.value) }
        }
        DownloadTarget.Kind.Playlist -> playlists.observePlaylist(PlaylistId(target.id)).map { d ->
            d?.playlist?.let { TargetInfo(it.name, it.owner, it.coverArt?.value ?: d.mosaic.firstOrNull()?.value) }
        }
    }

    override suspend fun refresh(target: DownloadTarget): Boolean = when (target.kind) {
        DownloadTarget.Kind.Album -> albums.refreshAlbum(AlbumId(target.id)).isSuccess
        DownloadTarget.Kind.Playlist -> playlists.refreshPlaylist(PlaylistId(target.id)).isSuccess
    }
}
