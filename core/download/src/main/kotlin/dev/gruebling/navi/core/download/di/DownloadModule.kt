package dev.gruebling.navi.core.download.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.gruebling.navi.core.data.di.IoDispatcher
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.download.DownloadFiles
import dev.gruebling.navi.core.download.DownloadLibrary
import dev.gruebling.navi.core.download.DownloadsCleaner
import dev.gruebling.navi.core.download.HttpSongFetcher
import dev.gruebling.navi.core.download.RepositoryDownloadLibrary
import dev.gruebling.navi.core.download.SongFetcher
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    /** In app storage, not the cache dir: Android may clear caches, downloads must stay. */
    @Provides
    @Singleton
    fun downloadFiles(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher) =
        DownloadFiles(File(context.filesDir, "downloads"), io)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadBindings {
    @Binds abstract fun library(impl: RepositoryDownloadLibrary): DownloadLibrary
    @Binds abstract fun fetcher(impl: HttpSongFetcher): SongFetcher
    @Binds @IntoSet abstract fun cleaner(impl: DownloadsCleaner): AccountCache
}
