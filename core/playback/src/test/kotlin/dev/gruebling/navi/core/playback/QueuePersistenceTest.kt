package dev.gruebling.navi.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import dev.gruebling.navi.core.database.dao.PlaybackDao
import dev.gruebling.navi.core.database.entity.PendingScrobbleEntity
import dev.gruebling.navi.core.database.entity.QueueItemEntity
import dev.gruebling.navi.core.database.entity.QueueStateEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueuePersistenceTest {
    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItems(listOf("a", "b").map { MediaItem.Builder().setMediaId(it).setUri(NaviUris.stream(it)).build() })
    }

    @After
    fun tearDown() = player.release()

    /** Records writes; [replaceQueue] hangs while [blockReplace] is set, like a slow transaction. */
    private class FakeDao : PlaybackDao {
        val writes = mutableListOf<String>()
        var blockReplace: CompletableDeferred<Unit>? = null

        override suspend fun replaceQueue(items: List<QueueItemEntity>, state: QueueStateEntity) {
            blockReplace?.await()
            writes += "items:" + items.joinToString(",") { it.songId }
        }

        override suspend fun upsertQueueState(state: QueueStateEntity) {
            writes += "state"
        }

        override suspend fun queue(): List<QueueItemEntity> = emptyList()
        override suspend fun queueState(): QueueStateEntity? = null
        override suspend fun clearQueue() = Unit
        override suspend fun insertQueue(items: List<QueueItemEntity>) = Unit
        override suspend fun insertScrobble(scrobble: PendingScrobbleEntity) = Unit
        override suspend fun pendingScrobbles(limit: Int): List<PendingScrobbleEntity> = emptyList()
        override suspend fun deleteScrobble(id: Long) = Unit
    }

    @Test
    fun `a cancelled queue write is redone by the next save`() = runTest {
        val dao = FakeDao().apply { blockReplace = CompletableDeferred() }
        val persistence = QueuePersistence(dao, UnconfinedTestDispatcher(testScheduler))
        persistence.attach(player, backgroundScope)

        // A save starts writing the new queue, then a newer save cancels it mid-write.
        val first = launch { persistence.save() }
        runCurrent()
        first.cancel()
        runCurrent()

        dao.blockReplace = null
        persistence.save()

        assertEquals(listOf("items:a,b"), dao.writes)
    }

    @Test
    fun `an unchanged queue only saves the position`() = runTest {
        val dao = FakeDao()
        val persistence = QueuePersistence(dao, UnconfinedTestDispatcher(testScheduler))
        persistence.attach(player, backgroundScope)

        persistence.save()
        persistence.save()

        assertEquals(listOf("items:a,b", "state"), dao.writes)
    }
}
