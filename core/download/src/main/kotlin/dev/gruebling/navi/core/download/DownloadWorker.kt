package dev.gruebling.navi.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs [DownloadSync] as a foreground job with a progress notification, so long downloads aren't cut off after
 * WorkManager's ten minutes.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: DownloadSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            setForeground(foregroundInfo(0, 0))
        } catch (e: IllegalStateException) {
            // Android 12+ may refuse a foreground start from the background; the job then runs as a normal one and
            // continues in the next run if it is stopped.
        }
        val outcome = sync.run { done, total ->
            notificationManager().notify(NOTIFICATION_ID, notification(done, total))
        }
        return when (outcome) {
            DownloadSync.Outcome.Done, DownloadSync.Outcome.Incomplete -> Result.success()
            DownloadSync.Outcome.Retry -> Result.retry()
            DownloadSync.Outcome.Failed -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification(done, total), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification(done, total))
        }

    private fun notification(done: Int, total: Int) = run {
        ensureChannel()
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .apply {
                if (total > 0) {
                    setContentText(applicationContext.getString(R.string.download_notification_progress, done, total))
                    setProgress(total, done, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun notificationManager() = applicationContext.getSystemService(NotificationManager::class.java)

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 0xD0_17
    }
}
