package dev.gruebling.navi.core.playback

import dev.gruebling.navi.core.model.ReplayGain
import dev.gruebling.navi.core.model.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGainMathTest {
    private val gain = ReplayGain(trackGain = -6f, albumGain = -9f, trackPeak = 0.5f, albumPeak = 0.9f)

    private fun volume(mode: ReplayGainMode, g: ReplayGain?, album: Boolean = false) = ReplayGainMath.volume(mode, g, album)

    @Test
    fun `off leaves the volume alone`() {
        assertEquals(1f, volume(ReplayGainMode.Off, gain), 0f)
        assertEquals(1f, volume(ReplayGainMode.Off, null), 0f)
    }

    @Test
    fun `decibels turn into a linear factor`() {
        assertEquals(0.501f, volume(ReplayGainMode.Track, gain), 0.001f)
        assertEquals(0.355f, volume(ReplayGainMode.Album, gain, album = true), 0.001f)
    }

    @Test
    fun `album gain falls back to track gain and back`() {
        assertEquals(0.501f, volume(ReplayGainMode.Album, gain.copy(albumGain = null), album = true), 0.001f)
        assertEquals(0.355f, volume(ReplayGainMode.Track, gain.copy(trackGain = null)), 0.001f)
    }

    @Test
    fun `a boost is capped at full volume and by the peak`() {
        assertEquals(1f, volume(ReplayGainMode.Track, ReplayGain(trackGain = 4f)), 0f)
        // -1 dB would be 0.891, but a peak of 1.25 only leaves room for 0.8.
        assertEquals(0.8f, volume(ReplayGainMode.Track, ReplayGain(trackGain = -1f, trackPeak = 1.25f)), 0.001f)
    }

    @Test
    fun `untagged songs play at full volume`() {
        assertEquals(1f, volume(ReplayGainMode.Auto, null), 0f)
    }
}
