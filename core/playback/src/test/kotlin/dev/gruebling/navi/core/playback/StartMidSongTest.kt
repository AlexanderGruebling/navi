package dev.gruebling.navi.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import dev.gruebling.navi.core.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartMidSongTest {
    private val items = listOf("a", "b").map { MediaItem.Builder().setMediaId(it).setUri(NaviUris.stream(it)).build() }
    private val opus = StreamQuality(maxBitRate = 192, format = "opus")

    private fun start(index: Int, positionMs: Long, quality: StreamQuality = opus) =
        TranscodeSeekingPlayer.startMidSong(items, index, positionMs) { quality }

    @Test
    fun `a transcoded start item streams from the saved position`() {
        val (result, position) = start(1, 83_400)
        assertEquals(0L, position)
        assertEquals(NaviUris.streamFrom("b", 83, opus), result[1].localConfiguration?.uri)
        assertSame(items[0], result[0])
    }

    @Test
    fun `original quality seeks normally`() {
        val (result, position) = start(1, 83_400, StreamQuality.Original)
        assertSame(items, result)
        assertEquals(83_400L, position)
    }

    @Test
    fun `the start of a song or an unset position are left alone`() {
        assertSame(items, start(0, 0).first)
        assertSame(items, start(0, 999).first)
        assertEquals(C.TIME_UNSET, start(0, C.TIME_UNSET).second)
    }

    @Test
    fun `items without a uri are left alone`() {
        val bare = listOf(MediaItem.Builder().setMediaId("a").build())
        val (result, position) = TranscodeSeekingPlayer.startMidSong(bare, 0, 60_000) { opus }
        assertSame(bare, result)
        assertEquals(60_000L, position)
    }
}
