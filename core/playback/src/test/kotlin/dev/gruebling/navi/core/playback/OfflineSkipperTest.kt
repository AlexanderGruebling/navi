package dev.gruebling.navi.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineSkipperTest {
    private lateinit var player: ExoPlayer
    private lateinit var skipper: OfflineSkipper
    private val messages = PlaybackMessages()
    private var downloaded = emptySet<String>()

    @Before
    fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItems(listOf("a", "b", "c", "d").map { MediaItem.Builder().setMediaId(it).setUri("navi://stream/$it").build() })
        skipper = OfflineSkipper({ it in downloaded }, messages).also { it.attach(player) }
    }

    @After
    fun tearDown() = player.release()

    private fun TestScope.received(): List<PlaybackMessage> {
        val got = mutableListOf<PlaybackMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.messages.toList(got) }
        return got
    }

    // The test player never loads anything, so the error is handed to the listener directly.
    private fun fail(code: Int = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) =
        skipper.onPlayerError(PlaybackException("offline", null, code))

    @Test
    fun `skips to the next downloaded song and says how many were skipped`() = runTest {
        val got = received()
        downloaded = setOf("c")
        fail()
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(listOf(PlaybackMessage.SkippedNotDownloaded(2)), got)
    }

    @Test
    fun `stays put when nothing later is downloaded`() = runTest {
        val got = received()
        downloaded = setOf("a")
        fail()
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(emptyList<PlaybackMessage>(), got)
    }

    @Test
    fun `wraps around with repeat all`() = runTest {
        downloaded = setOf("a")
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.seekToDefaultPosition(2)
        fail()
        assertEquals(0, player.currentMediaItemIndex)
    }

    @Test
    fun `other errors are left alone`() = runTest {
        downloaded = setOf("b")
        fail(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertEquals(0, player.currentMediaItemIndex)
    }
}
