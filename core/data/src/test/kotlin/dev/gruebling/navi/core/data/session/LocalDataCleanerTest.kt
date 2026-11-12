package dev.gruebling.navi.core.data.session

import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.database.entity.QueueStateEntity
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalDataCleanerTest {
    private lateinit var db: NaviDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    private class CountingCache : AccountCache {
        var cleared = 0
        override suspend fun clear() {
            cleared++
        }
    }

    @Test
    fun `clears the database and every account cache`() = runTest {
        db.playbackDao().upsertQueueState(QueueStateEntity(currentIndex = 3, positionMs = 1_000, shuffle = false, repeatMode = 0))
        val audio = CountingCache()
        val images = CountingCache()

        LocalDataCleaner(db, { setOf(audio, images) }, UnconfinedTestDispatcher(testScheduler)).clear()

        assertNull(db.playbackDao().queueState())
        assertEquals(1, audio.cleared)
        assertEquals(1, images.cleared)
    }
}
