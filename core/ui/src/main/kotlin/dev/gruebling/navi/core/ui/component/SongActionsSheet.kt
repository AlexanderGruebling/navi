package dev.gruebling.navi.core.ui.component

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.model.EnqueuePosition
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors

/** Long-press menu of a song: play it next or add it to the end of the queue. Confirms with a toast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    title: String,
    subtitle: String?,
    coverArtId: String?,
    onEnqueue: (EnqueuePosition) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    fun enqueue(position: EnqueuePosition, confirmation: Int) {
        onEnqueue(position)
        Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverArt(coverArtId, 48.dp, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { MonoLabel(it) }
            }
        }
        HorizontalDivider(color = NaviColors.Outline)
        Action(R.drawable.ic_skip_next, stringResource(R.string.song_play_next)) {
            enqueue(EnqueuePosition.Next, R.string.song_queued_next)
        }
        Action(R.drawable.ic_queue_music, stringResource(R.string.song_add_to_queue)) {
            enqueue(EnqueuePosition.End, R.string.song_queued_end)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Action(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
