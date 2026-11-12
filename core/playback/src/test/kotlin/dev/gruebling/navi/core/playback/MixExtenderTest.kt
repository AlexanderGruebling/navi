package dev.gruebling.navi.core.playback

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.model.SongId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MixExtenderTest {
    private lateinit var player: ExoPlayer
    private val requests = mutableListOf<String>()
    private var answer: List<String> = emptyList()

    @Before
    fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() = player.release()

    private fun song(id: String) = Song(
        SongId(id), "Song $id", null, null, null, null, null, null, 200, null, null, false, null, null, 0,
    )

    private fun queue(vararg ids: String, fromMix: Boolean) =
        player.setMediaItems(ids.map { MediaItems.fromSong(song(it), fromMix) })

    private fun ids() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    private fun TestScope.attach() {
        MixExtender { seed, _ -> requests += seed; Result.success(answer.map(::song)) }.attach(player, backgroundScope)
    }

    /** Lets the player deliver its listener events, then runs the launched work. */
    private fun TestScope.settle() {
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    @Test
    fun `a mix near its end grows with new songs similar to the current one`() = runTest {
        answer = listOf("b", "x", "y")
        attach()
        queue("a", "b", fromMix = true)
        settle()
        assertEquals(listOf("a"), requests)
        // "b" is already queued.
        assertEquals(listOf("a", "b", "x", "y"), ids())
    }

    @Test
    fun `a normal queue is left alone`() = runTest {
        answer = listOf("x")
        attach()
        queue("a", "b", fromMix = false)
        settle()
        assertEquals(emptyList<String>(), requests)
        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun `repeat turns the endless part off`() = runTest {
        answer = listOf("x")
        player.repeatMode = Player.REPEAT_MODE_ALL
        attach()
        queue("a", fromMix = true)
        settle()
        assertEquals(listOf("a"), ids())
    }

    @Test
    fun `an empty answer is not asked again for the same queue end`() = runTest {
        attach()
        queue("a", fromMix = true)
        settle()
        player.shuffleModeEnabled = true
        player.shuffleModeEnabled = false
        settle()
        assertEquals(listOf("a"), requests)
    }
}
