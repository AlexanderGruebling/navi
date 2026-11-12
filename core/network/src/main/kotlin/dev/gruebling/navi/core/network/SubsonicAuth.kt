package dev.gruebling.navi.core.network

import java.security.MessageDigest
import java.security.SecureRandom

/** Subsonic token authentication: t = md5(password + salt), with a fresh random salt per request. */
object SubsonicAuth {
    const val API_VERSION = "1.16.1"
    const val CLIENT_NAME = "Dotwave"

    private val random = SecureRandom()
    private val hex = "0123456789abcdef".toCharArray()

    fun newSalt(): String {
        val bytes = ByteArray(8).also(random::nextBytes)
        return bytes.toHex()
    }

    fun token(password: String, salt: String): String =
        MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }
}
