package dev.gruebling.navi.core.data.session

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cleans up what people type or paste as server address:
 * `music.example.com` → `https://music.example.com`,
 * `https://music.example.com/app/#/album/123` (Navidrome web UI) → `https://music.example.com`,
 * `https://example.com/navidrome/` → `https://example.com/navidrome`.
 */
object ServerUrl {
    fun normalize(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "https://$text"
        val url = text.toHttpUrlOrNull() ?: return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        // Drop the Navidrome web UI / API suffixes if pasted.
        val cut = segments.indexOfFirst { it == "app" || it == "rest" }
        if (cut >= 0) while (segments.size > cut) segments.removeAt(segments.lastIndex)
        val builder = url.newBuilder().query(null).fragment(null).encodedPath("/")
        segments.forEach(builder::addPathSegment)
        return builder.build().toString().removeSuffix("/")
    }
}
