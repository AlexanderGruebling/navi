package dev.gruebling.navi.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.datastore.DownloadSettingsStore
import dev.gruebling.navi.core.model.DownloadStatus
import dev.gruebling.navi.core.model.DownloadTarget
import dev.gruebling.navi.core.model.StreamQuality
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/** One downloaded album or playlist, for the downloads list. */
data class DownloadedCollection(
    val target: DownloadTarget,
    val info: TargetInfo?,
    val status: DownloadStatus,
    val sizeBytes: Long,
)

/** Offline downloads for the screens: what is downloaded, adding and removing, settings. */
@Singleton
class Downloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DownloadSettingsStore,
    private val library: DownloadLibrary,
    private val files: DownloadFiles,
) {
    private val workManager get() = WorkManager.getInstance(context)

    val quality: Flow<StreamQuality> = settings.quality
    val allowMobileData: Flow<Boolean> = settings.allowMobileData
    val totalBytes: Flow<Long> = files.downloaded.mapLatest { files.sizeOf() }

    private val phase: Flow<DownloadStatus.Phase> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        when (infos.lastOrNull()?.state) {
            WorkInfo.State.RUNNING -> DownloadStatus.Phase.Running
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Phase.WaitingForNetwork
            else -> DownloadStatus.Phase.Incomplete
        }
    }.distinctUntilChanged()

    fun status(target: DownloadTarget): Flow<DownloadStatus> =
        combine(settings.targets, library.observeSongIds(target), files.downloaded, phase) { targets, ids, have, phase ->
            if (target !in targets) return@combine DownloadStatus.None
            val done = ids.count { it in have }
            if (ids.isNotEmpty() && done == ids.size) DownloadStatus.Complete(ids.size)
            else DownloadStatus.Partial(done, ids.size, phase)
        }.distinctUntilChanged()

    /** Downloaded albums and playlists, albums first, each by name. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val collections: Flow<List<DownloadedCollection>> = settings.targets.flatMapLatest { targets ->
        if (targets.isEmpty()) return@flatMapLatest flowOf(emptyList())
        combine(targets.map(::collection)) { items ->
            items.sortedWith(compareBy({ it.target.kind }, { it.info?.name?.lowercase() }))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collection(target: DownloadTarget): Flow<DownloadedCollection> =
        combine(library.observeInfo(target), status(target), library.observeSongIds(target), files.downloaded) { info, status, ids, _ ->
            Triple(info, status, ids)
        }.mapLatest { (info, status, ids) -> DownloadedCollection(target, info, status, files.sizeOf(ids)) }

    suspend fun add(target: DownloadTarget) {
        settings.add(target)
        sync()
    }

    /** Stops keeping [target]; deletes its songs unless another download still has them. */
    suspend fun remove(target: DownloadTarget) {
        settings.remove(target)
        val stillWanted = settings.targets.first().flatMapTo(HashSet()) { library.songIds(it) }
        files.delete(library.songIds(target).filter { it !in stillWanted })
    }

    suspend fun removeAll() {
        workManager.cancelUniqueWork(WORK_NAME)
        settings.clearTargets()
        files.deleteAll()
    }

    suspend fun setQuality(quality: StreamQuality) = settings.setQuality(quality)

    suspend fun setAllowMobileData(allow: Boolean) {
        settings.setAllowMobileData(allow)
        // The waiting job has the old network requirement.
        sync(replace = true)
    }

    /**
     * Starts a download run if anything is downloaded: picks up playlist changes and finishes interrupted downloads.
     * A run that is already waiting or going on is kept (it notices new targets itself) unless [replace].
     */
    suspend fun sync(replace: Boolean = false) {
        if (settings.targets.first().isEmpty()) return
        val network = if (settings.allowMobileData.first()) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "downloads"
    }
}

/** Logout: downloads belong to the account. */
class DownloadsCleaner @Inject constructor(private val downloads: Downloads) : AccountCache {
    override suspend fun clear() = downloads.removeAll()
}
