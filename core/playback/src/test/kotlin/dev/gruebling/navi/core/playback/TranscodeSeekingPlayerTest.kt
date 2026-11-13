package dev.gruebling.navi.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import dev.gruebling.navi.core.model.StreamQuality
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranscodeSeekingPlayerTest {
    private val opus = StreamQuality(maxBitRate = 192, format = "opus")
    private var forNewStream = opus
    private val qualities = object : StreamQualities {
        override fun qualityOf(songId: String): StreamQuality? = null
        override fun qualityForNewStream(songId: String) = forNewStream
    }
    private var serverHasOffset = true
    private lateinit var exo: ExoPlayer
    private lateinit var player: TranscodeSeekingPlayer
    private val items = listOf("a", "b").map {
        MediaItem.Builder().setMediaId(it).setUri(NaviUris.stream(it))
            .setMediaMetadata(MediaMetadata.Builder().setDurationMs(200_000).build()).build()
    }

    @Before
    fun setUp() {
        exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
        player = TranscodeSeekingPlayer(exo, qualities) { serverHasOffset }
    }

    @After
    fun tearDown() = exo.release()

    private fun uriAt(index: Int) = exo.getMediaItemAt(index).localConfiguration?.uri

    @Test
    fun `a restored transcoded song streams from its position once prepared`() {
        player.setMediaItems(items, 1, 83_400)
        assertEquals(NaviUris.stream("b"), uriAt(1)) // nothing decided before playback
        player.prepare()
        assertEquals(NaviUris.streamFrom("b", 83, opus), uriAt(1))
        assertEquals(83_000L, player.currentPosition)
    }

    @Test
    fun `the quality is the one when playback starts, not when the queue was restored`() {
        player.setMediaItems(items, 1, 83_400)
        forNewStream = StreamQuality.Original
        player.prepare()
        assertEquals(NaviUris.stream("b"), uriAt(1))
        assertEquals(83_400L, player.currentPosition)
    }

    @Test
    fun `a seek before playing moves the start`() {
        player.setMediaItems(items, 1, 83_400)
        player.seekTo(120_000)
        player.prepare()
        assertEquals(NaviUris.streamFrom("b", 120, opus), uriAt(1))
    }

    @Test
    fun `a position at the end or the start is left alone`() {
        player.setMediaItems(items, 0, 199_500)
        player.prepare()
        assertEquals(NaviUris.stream("a"), uriAt(0))
    }

    @Test
    fun `a seek in an offset stream that hasn't opened yet uses its pinned quality`() {
        player.setMediaItems(items, 1, 83_400)
        player.prepare()
        player.seekTo(150_000)
        assertEquals(NaviUris.streamFrom("b", 150, opus), uriAt(1))
    }

    @Test
    fun `without server support a transcoded song is never started mid-song`() {
        serverHasOffset = false
        player.setMediaItems(items, 1, 83_400)
        player.prepare()
        assertEquals(NaviUris.stream("b"), uriAt(1))
        player.seekTo(150_000)
        assertEquals(NaviUris.stream("b"), uriAt(1))
    }
}
