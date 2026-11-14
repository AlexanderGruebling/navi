package dev.gruebling.navi.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One release of the new-releases feed, as last fetched. Cache only: the feed is the source, and what the user marks
 * (seen, saved) lives in DataStore (NewReleaseStore).
 */
@Entity(tableName = "new_releases")
data class NewReleaseEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** "album", "ep", "single" as in the feed. */
    val type: String,
    /** JSON array of names. */
    val artistsJson: String,
    /** ISO date. */
    val releaseDate: String,
    val firstSeen: String,
    val link: String,
    val coverUrl: String?,
    val coverUrlLarge: String?,
    val trackCount: Int?,
    val duration: Int?,
    val label: String?,
    /** JSON array of {title, duration}. */
    val tracksJson: String,
    val inLibrary: Boolean,
    val libraryAlbumId: String?,
    /** Position in the feed (newest first). */
    val position: Int,
)
