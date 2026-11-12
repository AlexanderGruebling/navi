package dev.gruebling.navi.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The play queue, persisted so it survives the app being killed. Metadata is copied so restoring needs no network. */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val coverArt: String?,
    val durationMs: Long,
    /** Added by an instant mix; while the last item is one, the queue grows by itself. */
    val fromMix: Boolean = false,
)

/** Single row (id = 0) with the player state that belongs to the queue. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** Plays that could not be reported to the server yet (offline); sent later with their original time. */
@Entity(tableName = "pending_scrobbles")
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
)
