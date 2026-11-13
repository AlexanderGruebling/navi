package dev.gruebling.navi.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.ScreenTitle
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviTheme
import javax.inject.Inject
import dev.gruebling.navi.core.ui.R as UiR

/** Opened by the launcher when a playlist widget is placed or reconfigured: pick the playlist it plays. */
@AndroidEntryPoint
class PlaylistWidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var playlistWidgets: PlaylistWidgets

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Backing out without a choice must not leave an empty widget behind.
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            NaviTheme {
                val all by remember { playlists.observePlaylists() }.collectAsStateWithLifecycle(initialValue = null)
                PlaylistChooser(all) { playlist ->
                    playlistWidgets.choose(widgetId, playlist)
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun PlaylistChooser(playlists: List<Playlist>?, onChoose: (Playlist) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            ScreenTitle(stringResource(R.string.widget_choose_playlist))
            if (playlists?.isEmpty() == true) {
                Text(
                    stringResource(R.string.widget_no_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NaviColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp),
                )
            }
            LazyColumn(contentPadding = WindowInsets.navigationBars.asPaddingValues().let { PaddingValues(top = 8.dp, bottom = it.calculateBottomPadding() + 16.dp) }) {
                items(playlists.orEmpty(), key = { it.id.value }) { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(playlist) }
                            .padding(horizontal = ScreenPadding, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CoverArt(playlist.coverArt?.value, 56.dp, Modifier.size(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MonoLabel(pluralStringResource(UiR.plurals.song_count, playlist.songCount, playlist.songCount))
                        }
                    }
                }
            }
        }
    }
}
