package dev.gruebling.navi.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gruebling.navi.core.datastore.CredentialStore
import dev.gruebling.navi.core.datastore.EncryptedCredentialStore
import dev.gruebling.navi.core.datastore.KeystoreSecretCipher
import dev.gruebling.navi.core.datastore.SecretCipher
import dev.gruebling.navi.core.datastore.SessionPreferences
import dev.gruebling.navi.core.datastore.SettingsPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds abstract fun credentialStore(impl: EncryptedCredentialStore): CredentialStore
    @Binds abstract fun secretCipher(impl: KeystoreSecretCipher): SecretCipher

    companion object {
        @Provides
        @Singleton
        @SessionPreferences
        fun sessionPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }

        @Provides
        @Singleton
        @SettingsPreferences
        fun settingsPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
    }
}
