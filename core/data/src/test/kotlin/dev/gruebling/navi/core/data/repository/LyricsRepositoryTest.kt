package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.parsePlainLyrics
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.error
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.model.Lyrics
import dev.gruebling.navi.core.model.LyricsLine
import dev.gruebling.navi.core.model.SongId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LyricsRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var repo: LyricsRepository

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        repo = LyricsRepository(server.api)
    }

    @After
    fun tearDown() = server.close()

    private fun structured(vararg variants: String) = ok(""""lyricsList":{"structuredLyrics":[${variants.joinToString(",")}]}""")

    private fun variant(synced: Boolean, lang: String = "eng", offset: Long = 0, lines: List<Pair<Long?, String>>) =
        """{"lang":"$lang","synced":$synced,"offset":$offset,"line":[${
            lines.joinToString(",") { (start, text) -> (if (start != null) """{"start":$start,"value":"$text"}""" else """{"value":"$text"}""") }
        }]}"""

    @Test
    fun `synced variant wins over unsynced, offset is applied`() = runTest {
        server.on(
            "getLyricsBySongId",
            structured(
                variant(synced = false, lines = listOf(null to "plain")),
                variant(synced = true, offset = 500, lines = listOf(1_000L to "one", 3_000L to "two")),
            ),
        )
        val lyrics = repo.lyrics(SongId("s1"), "A", "T").getOrThrow()!!
        assertTrue(lyrics.synced)
        assertEquals(listOf(LyricsLine(500, "one"), LyricsLine(2_500, "two")), lyrics.lines)
    }

    @Test
    fun `song without lyrics is null and cached`() = runTest {
        server.on("getLyricsBySongId", ok(""""lyricsList":{}"""))
        assertNull(repo.lyrics(SongId("s1"), "A", "T").getOrThrow())
        assertNull(repo.lyrics(SongId("s1"), "A", "T").getOrThrow())
        assertEquals(1, server.requestsTo("getLyricsBySongId").size)
        assertTrue(server.requestsTo("getLyrics").isEmpty())
    }

    @Test
    fun `clear forgets cached lyrics (logout)`() = runTest {
        server.on("getLyricsBySongId", structured(variant(true, lines = listOf(0L to "hi"))))
        repo.lyrics(SongId("s1"), "A", "T").getOrThrow()
        repo.clear()
        repo.lyrics(SongId("s1"), "A", "T").getOrThrow()
        assertEquals(2, server.requestsTo("getLyricsBySongId").size)
    }

    @Test
    fun `server without the extension falls back to getLyrics`() = runTest {
        server.on("getLyricsBySongId", error(0, "unknown endpoint"))
        server.on("getLyrics", ok(""""lyrics":{"artist":"A","title":"T","value":"[00:01.50]hello\n[00:04.00]world"}"""))
        val lyrics = repo.lyrics(SongId("s1"), "A", "T").getOrThrow()!!
        assertEquals(listOf(LyricsLine(1_500, "hello"), LyricsLine(4_000, "world")), lyrics.lines)
        assertEquals("A", server.requestsTo("getLyrics").single().queryParameter("artist"))
    }

    @Test
    fun `network errors fail and are not cached`() = runTest {
        server.close()
        assertTrue(repo.lyrics(SongId("s1"), "A", "T").isFailure)

        server = FakeSubsonicServer().apply { on("getLyricsBySongId", structured(variant(true, lines = listOf(0L to "hi")))) }
        repo = LyricsRepository(server.api)
        assertEquals("hi", repo.lyrics(SongId("s1"), "A", "T").getOrThrow()!!.lines.single().text)
    }

    @Test
    fun `plain text stays unsynced, LRC tags and repeated stamps are handled`() {
        val plain = parsePlainLyrics("\n[ar:Someone]\nfirst line\n\nsecond line\n\n")!!
        assertFalse(plain.synced)
        assertEquals(listOf("first line", "", "second line"), plain.lines.map { it.text })

        val chorus = parsePlainLyrics("[ti:Song]\n[00:10.00][00:30.00]la la\n[00:20]verse")!!
        assertEquals(listOf(10_000L to "la la", 20_000L to "verse", 30_000L to "la la"), chorus.lines.map { it.startMs to it.text })
    }

    @Test
    fun `lineAt finds the current line`() {
        val lyrics = Lyrics(true, listOf(LyricsLine(1_000, "a"), LyricsLine(2_000, "b"), LyricsLine(5_000, "c")))
        assertEquals(-1, lyrics.lineAt(500))
        assertEquals(0, lyrics.lineAt(1_000))
        assertEquals(1, lyrics.lineAt(4_999))
        assertEquals(2, lyrics.lineAt(60_000))
        assertEquals(-1, lyrics.copy(synced = false).lineAt(60_000))
    }
}
