package dev.gruebling.navi.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Lives as long as the process; for work that must outlive a screen (session restore, sync). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
