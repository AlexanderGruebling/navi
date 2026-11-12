package dev.gruebling.navi.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dev.gruebling.navi.core.data.repository.LyricsRepository
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.network.CredentialsSource

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun credentialsSource(impl: SessionManager): CredentialsSource

    @Multibinds abstract fun accountCaches(): Set<AccountCache>

    @Binds @IntoSet abstract fun lyricsCache(impl: LyricsRepository): AccountCache
}
