package dev.gruebling.navi.feature.player

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.gruebling.navi.core.ui.image.CoverArtRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The cover's most characteristic color (vibrant, else dominant), or null while loading / without cover.
 * Uses the smallest cover size, which the mini player has usually cached already.
 */
@Composable
internal fun rememberCoverColor(coverArtId: String?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(coverArtId) {
        if (coverArtId == null) {
            color = null
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(CoverArtRequest(coverArtId, 150))
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        color = withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap().let {
                if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
            }
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            (palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb?.let(::Color)
        }
    }
    return color
}
