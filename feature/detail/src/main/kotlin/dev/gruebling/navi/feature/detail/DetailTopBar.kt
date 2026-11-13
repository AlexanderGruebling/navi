package dev.gruebling.navi.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Top bar of the detail pages: back chevron and a centered mono [label] ("PLAYLIST"). Once the header has scrolled
 * away ([scrolled] goes to 1), the bar turns black and the label gives way to the [title].
 * [scrolled] is read in the draw phase only.
 */
@Composable
internal fun DetailTopBar(
    label: String,
    title: String,
    scrolled: () -> Float,
    onBack: () -> Unit,
    /** Optional button on the right, e.g. "edit"; 48 dp wide like the back button. */
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(NaviColors.Black.copy(alpha = scrolled())) }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(UiR.drawable.ic_arrow_back), contentDescription = stringResource(UiR.string.cd_back))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MonoLabel(label, Modifier.graphicsLayer { alpha = 1f - scrolled() })
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = scrolled() },
            )
        }
        // Same width as the back button, so the label stays centered.
        if (action != null) action() else Spacer(Modifier.size(48.dp))
    }
}
