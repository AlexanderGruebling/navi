package dev.gruebling.navi.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.network.CredentialsSource

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun credentialsSource(impl: SessionManager): CredentialsSource
}
