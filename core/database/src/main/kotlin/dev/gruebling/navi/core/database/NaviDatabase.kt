package dev.gruebling.navi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.gruebling.navi.core.database.dao.AlbumDao
import dev.gruebling.navi.core.database.dao.ArtistDao
import dev.gruebling.navi.core.database.dao.PlaybackDao
import dev.gruebling.navi.core.database.dao.PlaylistDao
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.database.entity.AlbumEntity
import dev.gruebling.navi.core.database.entity.AlbumListEntryEntity
import dev.gruebling.navi.core.database.entity.ArtistEntity
import dev.gruebling.navi.core.database.entity.ArtistInfoEntity
import dev.gruebling.navi.core.database.entity.ArtistSimilarEntity
import dev.gruebling.navi.core.database.entity.ArtistTopSongEntity
import dev.gruebling.navi.core.database.entity.PendingScrobbleEntity
import dev.gruebling.navi.core.database.entity.QueueItemEntity
import dev.gruebling.navi.core.database.entity.QueueStateEntity
import dev.gruebling.navi.core.database.entity.PlaylistEntity
import dev.gruebling.navi.core.database.entity.PlaylistPrefsEntity
import dev.gruebling.navi.core.database.entity.PlaylistSongEntity
import dev.gruebling.navi.core.database.entity.SongEntity

/**
 * Local cache of the server library. It is a cache, not the source of truth: until there are offline downloads,
 * schema changes may simply drop and rebuild it (see DatabaseModule).
 */
@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ArtistInfoEntity::class,
        ArtistSimilarEntity::class,
        ArtistTopSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlaylistPrefsEntity::class,
        AlbumListEntryEntity::class,
        QueueItemEntity::class,
        QueueStateEntity::class,
        PendingScrobbleEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NaviDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackDao(): PlaybackDao
}
