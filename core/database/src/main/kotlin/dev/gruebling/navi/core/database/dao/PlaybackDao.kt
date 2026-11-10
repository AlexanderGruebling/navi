package dev.gruebling.navi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.gruebling.navi.core.database.entity.PendingScrobbleEntity
import dev.gruebling.navi.core.database.entity.QueueItemEntity
import dev.gruebling.navi.core.database.entity.QueueStateEntity

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun queue(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun queueState(): QueueStateEntity?

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Insert suspend fun insertQueue(items: List<QueueItemEntity>)

    @Upsert suspend fun upsertQueueState(state: QueueStateEntity)

    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>, state: QueueStateEntity) {
        clearQueue()
        insertQueue(items)
        upsertQueueState(state)
    }

    @Insert suspend fun insertScrobble(scrobble: PendingScrobbleEntity)

    @Query("SELECT * FROM pending_scrobbles ORDER BY playedAt LIMIT :limit")
    suspend fun pendingScrobbles(limit: Int): List<PendingScrobbleEntity>

    @Query("DELETE FROM pending_scrobbles WHERE id = :id")
    suspend fun deleteScrobble(id: Long)
}
