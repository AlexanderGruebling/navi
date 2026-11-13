package dev.gruebling.navi.feature.widget.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.playback.PlayerWatcher
import dev.gruebling.navi.feature.widget.NowPlayingWidgets

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {
    @Binds @IntoSet abstract fun playerWatcher(impl: NowPlayingWidgets): PlayerWatcher

    @Binds @IntoSet abstract fun accountCache(impl: NowPlayingWidgets): AccountCache
}
