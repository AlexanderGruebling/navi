package dev.gruebling.navi.feature.detail

import dev.gruebling.navi.core.download.Downloads
import dev.gruebling.navi.core.model.DownloadStatus
import dev.gruebling.navi.core.model.DownloadTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun Downloads.ui(target: DownloadTarget): Flow<DownloadUi> =
    combine(status(target), allowMobileData, ::DownloadUi)

/** Download button: starts the download; removing goes through a confirmation, see [removeDownload]. */
internal fun CoroutineScope.toggleDownload(downloads: Downloads, target: DownloadTarget) {
    launch { if (downloads.status(target).first() == DownloadStatus.None) downloads.add(target) }
}

internal fun CoroutineScope.removeDownload(downloads: Downloads, target: DownloadTarget) {
    launch { downloads.remove(target) }
}
