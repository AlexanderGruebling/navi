package dev.gruebling.navi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NaviApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    // Lazy: the image loader is built on the first image, not at app start.
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    // WorkManager starts on first use (downloads), with workers that get their dependencies from Hilt.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
