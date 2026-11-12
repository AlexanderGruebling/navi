package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.model.DownloadStatus
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.theme.NaviColors

/** One line about a download, for mono labels; null when nothing is downloaded. */
@Composable
fun downloadStatusText(status: DownloadStatus, allowMobileData: Boolean): String? = when (status) {
    DownloadStatus.None -> null
    is DownloadStatus.Complete -> stringResource(R.string.download_done)
    is DownloadStatus.Partial -> when {
        status.total == 0 -> stringResource(R.string.download_preparing)
        status.phase == DownloadStatus.Phase.Running -> stringResource(R.string.download_progress, status.done, status.total)
        status.phase == DownloadStatus.Phase.WaitingForNetwork -> stringResource(
            if (allowMobileData) R.string.download_waiting_network else R.string.download_waiting_wifi,
            status.done,
            status.total,
        )
        else -> stringResource(R.string.download_incomplete, status.done, status.total)
    }
}

/** Download toggle: arrow when not downloaded, a progress ring while loading, a check when done. */
@Composable
fun DownloadButton(status: DownloadStatus, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        when (status) {
            DownloadStatus.None -> Icon(
                painterResource(R.drawable.ic_download),
                contentDescription = stringResource(R.string.cd_download),
                tint = NaviColors.TextSecondary,
            )
            is DownloadStatus.Complete -> Icon(
                painterResource(R.drawable.ic_downloaded),
                contentDescription = stringResource(R.string.cd_remove_download),
                tint = NaviColors.White,
            )
            is DownloadStatus.Partial -> Box(contentAlignment = Alignment.Center) {
                val progress = if (status.total > 0) status.done / status.total.toFloat() else 0f
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = NaviColors.Surface3,
                    strokeWidth = 2.dp,
                    gapSize = 0.dp,
                )
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = stringResource(R.string.cd_remove_download),
                    tint = NaviColors.TextSecondary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** Asks before a download is removed: it may have taken long and needs Wi-Fi to come back. */
@Composable
fun RemoveDownloadDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_remove_title)) },
        text = { Text(stringResource(R.string.download_remove_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.download_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
