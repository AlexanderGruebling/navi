package dev.gruebling.navi.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gruebling.navi.core.database.NaviDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NaviDatabase =
        Room.databaseBuilder(context, NaviDatabase::class.java, "navi.db")
            // Pure cache for now. Once local-only data matters (playlist prefs, downloads), write real migrations.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun songDao(db: NaviDatabase) = db.songDao()
    @Provides fun albumDao(db: NaviDatabase) = db.albumDao()
    @Provides fun artistDao(db: NaviDatabase) = db.artistDao()
    @Provides fun playlistDao(db: NaviDatabase) = db.playlistDao()
}
