package dev.gruebling.navi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.gruebling.navi.core.database.entity.NewReleaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NewReleaseDao {
    @Query("SELECT * FROM new_releases ORDER BY position")
    fun observeAll(): Flow<List<NewReleaseEntity>>

    @Query("SELECT * FROM new_releases WHERE id = :id")
    fun observe(id: String): Flow<NewReleaseEntity?>

    /** The feed is small (a few dozen rows) and always complete, so a refresh replaces it as a whole. */
    @Transaction
    suspend fun replaceAll(releases: List<NewReleaseEntity>) {
        deleteAll()
        insertAll(releases)
    }

    @Insert
    suspend fun insertAll(releases: List<NewReleaseEntity>)

    @Query("DELETE FROM new_releases")
    suspend fun deleteAll()
}
