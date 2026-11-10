package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.model.PlaylistGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistGroupingTest {
    private val g = PlaylistGrouping()

    @Test fun `smart playlists are For You`() =
        assertEquals(PlaylistGroup.ForYou, g.groupOf("90s Mix", "From Austropop to Eurodance.", isSmart = true, override = null))

    @Test fun `chart names are Charts`() {
        assertEquals(PlaylistGroup.Charts, g.groupOf("FM4 Charts", null, isSmart = false, override = null))
        assertEquals(PlaylistGroup.Charts, g.groupOf("Weekly-Exploration", null, isSmart = false, override = null))
        assertEquals(PlaylistGroup.Charts, g.groupOf("Ö3 Top 40", null, isSmart = false, override = null))
    }

    @Test fun `smart wins over a chart-like name`() =
        assertEquals(PlaylistGroup.ForYou, g.groupOf("Top 25 Most Played", null, isSmart = true, override = null))

    @Test fun `everything else is Mine`() =
        assertEquals(PlaylistGroup.Mine, g.groupOf("On Repeat", "The songs you just can't stop playing.", isSmart = false, override = null))

    @Test fun `comment tag beats the heuristics`() {
        assertEquals(PlaylistGroup.ForYou, g.groupOf("On Repeat", "Most played [für-dich]", isSmart = false, override = null))
        assertEquals(PlaylistGroup.Charts, g.groupOf("Hype", "[Charts] weekly hype", isSmart = true, override = null))
    }

    @Test fun `manual override beats everything`() =
        assertEquals(PlaylistGroup.Mine, g.groupOf("FM4 Charts", "[charts]", isSmart = false, override = PlaylistGroup.Mine))

    @Test fun `display comment drops tags`() {
        assertEquals("Most played", PlaylistGrouping.displayComment("Most played [für-dich]"))
        assertEquals("weekly hype", PlaylistGrouping.displayComment("[charts]  weekly hype"))
        assertNull(PlaylistGrouping.displayComment("[mine]"))
        assertNull(PlaylistGrouping.displayComment(null))
    }

    @Test fun `signature depends on content and order`() {
        val a = PlaylistGrouping.signature(listOf("1", "2", "3"))
        assertEquals(a, PlaylistGrouping.signature(listOf("1", "2", "3")))
        assertNotEquals(a, PlaylistGrouping.signature(listOf("3", "2", "1")))
        assertNotEquals(PlaylistGrouping.signature(listOf("12", "3")), PlaylistGrouping.signature(listOf("1", "23")))
    }
}
