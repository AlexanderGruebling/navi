package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.toDomainOrNull
import dev.gruebling.navi.core.data.mapping.toDto
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.dao.NewReleaseDao
import dev.gruebling.navi.core.datastore.NewReleaseStore
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.network.ReleaseFeedApi
import dev.gruebling.navi.core.network.model.ReleaseDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * New releases from the release feed, stale-while-revalidate like the other repositories: [observeReleases] reads
 * the Room copy of the feed plus the user's marks; [refresh] downloads the feed and replaces that copy.
 */
@Singleton
class NewReleaseRepository @Inject constructor(
    private val api: ReleaseFeedApi,
    private val dao: NewReleaseDao,
    private val store: NewReleaseStore,
    private val json: Json,
) : AccountCache {
    /** False when the user cleared the feed URL: the feature is hidden then. */
    val enabled: Flow<Boolean> = store.feedUrl.map { it.isNotBlank() }.distinctUntilChanged()

    val feedUrl: Flow<String> = store.feedUrl

    /** Feed releases plus saved ones the feed has dropped, newest release first. Empty while the feature is off. */
    fun observeReleases(): Flow<List<NewRelease>> =
        combine(dao.observeAll(), store.seen, store.saved, enabled) { rows, seen, saved, on ->
            if (!on) return@combine emptyList()
            val feed = rows.mapNotNull { it.toDomainOrNull(json) }
            val inFeed = feed.mapTo(HashSet()) { it.id }
            val dropped = saved.filterKeys { it !in inFeed }.values.mapNotNull(::decodeSaved)
            (feed + dropped)
                .map { it.copy(seen = it.id in seen, saved = it.id in saved) }
                .sortedWith(NEWEST_FIRST)
        }.distinctUntilChanged()

    fun observeRelease(id: String): Flow<NewRelease?> =
        observeReleases().map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    /** Downloads the feed. Does nothing while the feature is off. */
    suspend fun refresh(): Result<Unit> = suspendRunCatching {
        val url = store.feedUrl.first()
        if (url.isBlank()) return@suspendRunCatching
        val releases = api.feed(url).releases.mapNotNull { dto -> dto.toDomainOrNull()?.let { dto to it } }
        dao.replaceAll(releases.mapIndexed { i, (_, release) -> release.toEntity(i, json) })
        store.refreshSaved(releases.associate { (dto, _) -> dto.id to encode(dto) })
        store.retainSeen(releases.mapTo(HashSet()) { it.second.id } + store.saved.first().keys)
    }

    suspend fun setSeen(ids: Collection<String>, seen: Boolean) = store.setSeen(ids, seen)

    suspend fun setSaved(release: NewRelease, saved: Boolean) {
        if (saved) store.save(release.id, encode(release.toDto())) else store.unsave(release.id)
    }

    suspend fun setFeedUrl(url: String) = store.setFeedUrl(url)

    /** Logout: the Room copy goes with the database; the marks go here. */
    override suspend fun clear() = store.clearMarks()

    private fun encode(dto: ReleaseDto): String = json.encodeToString(ReleaseDto.serializer(), dto)

    private fun decodeSaved(payload: String): NewRelease? =
        try { json.decodeFromString(ReleaseDto.serializer(), payload).toDomainOrNull() } catch (_: SerializationException) { null }

    private companion object {
        val NEWEST_FIRST = compareByDescending<NewRelease> { it.releaseDate }.thenByDescending { it.firstSeen }.thenBy { it.title }
    }
}
