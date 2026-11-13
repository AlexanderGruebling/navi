package dev.gruebling.navi.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.database.dao.SongDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

/**
 * Session callback. Fills in stream URIs for items sent by controllers, resolves bare ids (Bluetooth, Assistant,
 * later Android Auto) from the cache, and resumes the saved queue. The browse tree is a stub until Android Auto.
 */
@OptIn(UnstableApi::class)
class LibraryCallback @Inject constructor(
    private val songDao: SongDao,
    private val queuePersistence: QueuePersistence,
    @ApplicationScope private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future {
        val bare = mediaItems.filter { it.mediaMetadata.title == null }.map { it.mediaId }
        val looked = songDao.getAll(bare).associateBy { it.id }
        mediaItems.mapNotNull { item ->
            if (item.mediaMetadata.title != null) MediaItems.withStreamUri(item)
            else looked[item.mediaId]?.let(MediaItems::fromEntity)
        }.toMutableList()
    }

    /** Bluetooth "play" or the system media UI asking to resume after the app was killed. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        queuePersistence.load() ?: throw UnsupportedOperationException("Nothing to resume")
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaId(ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
                )
                .build(),
            params,
        ),
    )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))

    private companion object {
        const val ROOT = "root"
    }
}
