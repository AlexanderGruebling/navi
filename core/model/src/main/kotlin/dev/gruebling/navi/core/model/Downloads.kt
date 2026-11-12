package dev.gruebling.navi.core.model

/** An album or playlist the user keeps on the device. Playlists stay in sync with the server. */
data class DownloadTarget(val kind: Kind, val id: String) {
    enum class Kind { Album, Playlist }

    /** Stable string form for storage. */
    val key: String get() = "${kind.name.lowercase()}:$id"

    companion object {
        fun album(id: AlbumId) = DownloadTarget(Kind.Album, id.value)
        fun playlist(id: PlaylistId) = DownloadTarget(Kind.Playlist, id.value)

        fun parse(key: String): DownloadTarget? {
            val kind = Kind.entries.firstOrNull { key.startsWith(it.name.lowercase() + ":") } ?: return null
            return DownloadTarget(kind, key.substringAfter(':'))
        }
    }
}

/** How far a download target is. [done] and [total] count songs; [total] is 0 until the song list is known. */
sealed interface DownloadStatus {
    data object None : DownloadStatus

    data class Partial(val done: Int, val total: Int, val phase: Phase) : DownloadStatus

    data class Complete(val songCount: Int) : DownloadStatus

    enum class Phase {
        /** The download job is working on it. */
        Running,

        /** Waiting for a network that downloads may use (Wi-Fi unless mobile data is allowed). */
        WaitingForNetwork,

        /** The job finished, but some songs could not be downloaded. */
        Incomplete,
    }
}
