package dev.gruebling.navi.core.ui.image

/**
 * Image model for Subsonic cover art. The app's ImageLoader maps it to an authenticated URL at load time and caches
 * it under an auth-free key, so the model stays stable across recompositions and logins.
 * [sizePx] is rounded up to a few fixed sizes, so the same cover in similar sizes shares one cache entry.
 */
@ConsistentCopyVisibility
data class CoverArtRequest private constructor(val id: String, val sizePx: Int) {
    val cacheKey: String get() = "cover:$id:$sizePx"

    companion object {
        private val SIZES = intArrayOf(150, 300, 600, 1200)

        operator fun invoke(id: String, requestedPx: Int): CoverArtRequest =
            CoverArtRequest(id, SIZES.firstOrNull { it >= requestedPx } ?: SIZES.last())
    }
}
