package dev.gruebling.navi.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {
    @Test
    fun `token matches the example from the Subsonic API docs`() {
        // http://www.subsonic.org/pages/api.jsp: password "sesame", salt "c19b2d"
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicAuth.token("sesame", "c19b2d"))
    }

    @Test
    fun `token handles non-ASCII passwords as UTF-8`() {
        assertEquals(32, SubsonicAuth.token("Grüße€", "abc").length)
        assertNotEquals(SubsonicAuth.token("Grüße", "abc"), SubsonicAuth.token("Grusse", "abc"))
    }

    @Test
    fun `salts are random hex`() {
        val a = SubsonicAuth.newSalt()
        val b = SubsonicAuth.newSalt()
        assertNotEquals(a, b)
        assertTrue(a.matches(Regex("[0-9a-f]{16}")))
    }
}
