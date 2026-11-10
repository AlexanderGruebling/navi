package dev.gruebling.navi

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NaviApplication : Application(), SingletonImageLoader.Factory {
    // Lazy: the image loader is built on the first image, not at app start.
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
