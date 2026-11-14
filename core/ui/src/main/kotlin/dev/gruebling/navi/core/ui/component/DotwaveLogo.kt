package dev.gruebling.navi.core.ui.component

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.theme.NaviColors
import kotlin.math.abs

/**
 * The Dotwave mark: a 9×9 dot-matrix waveform with a red centre column, as in the launcher icon
 * (`app/src/main/res/drawable/ic_launcher_foreground.xml`). Just the dots, no background circle.
 */
@Composable
fun DotwaveLogo(modifier: Modifier = Modifier, size: Dp = 48.dp, showUnlit: Boolean = true) {
    val label = stringResource(R.string.app_name)
    Spacer(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .drawWithCache {
                // Design grid: 9 px dots on a 12 px pitch, 105 px wide.
                val unit = this.size.width / 105f
                val radius = 4.5f * unit
                onDrawBehind {
                    for (row in 0 until 9) {
                        for (col in 0 until 9) {
                            val lit = abs(row - 4) <= WAVE[col]
                            if (!lit && !showUnlit) continue
                            val color = when {
                                !lit -> UNLIT
                                col == 4 -> NaviColors.Accent
                                else -> NaviColors.White
                            }
                            drawCircle(color, radius, Offset((col * 12 + 4.5f) * unit, (row * 12 + 4.5f) * unit))
                        }
                    }
                }
            },
    )
}

/**
 * [DotwaveLogo] as a bitmap of [sizePx] square, for places outside Compose (home screen widgets): lit dots only, on
 * a transparent background.
 */
fun dotwaveLogoBitmap(sizePx: Int): Bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = android.graphics.Canvas(bitmap)
    val unit = sizePx / 105f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (row in 0 until 9) {
        for (col in 0 until 9) {
            if (abs(row - 4) > WAVE[col]) continue
            paint.color = (if (col == 4) NaviColors.Accent else NaviColors.White).toArgb()
            canvas.drawCircle((col * 12 + 4.5f) * unit, (row * 12 + 4.5f) * unit, 4.5f * unit, paint)
        }
    }
}

/** Half-height of the lit run per column. */
private val WAVE = intArrayOf(0, 1, 3, 2, 4, 2, 3, 1, 0)
private val UNLIT = Color(0xFF222222)
