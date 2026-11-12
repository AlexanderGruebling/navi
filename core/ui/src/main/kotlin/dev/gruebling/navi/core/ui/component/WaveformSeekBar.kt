package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.theme.NaviColors
import kotlin.math.abs
import kotlin.math.sin

/**
 * Seek bar drawn as a row of waveform-like bars, as in the design: bars before the position are in the accent
 * color, the rest grey. The bar heights are decorative (not the real waveform) but stable per [seed], so every song
 * has its own shape. Tap to jump, drag to scrub ([onScrub] reports the preview position, [onSeek] fires on release).
 *
 * [positionMs] is read only in the draw phase, so position updates redraw the bar without recomposition.
 */
@Composable
fun WaveformSeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    seed: String? = null,
    onScrub: (Long?) -> Unit = {},
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = NaviColors.Outline,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentDuration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val phase = remember(seed) { ((seed?.hashCode() ?: 0) and 0xFFFF) / 0xFFFF.toFloat() * 20f }

    val progressFraction = { dragFraction ?: if (currentDuration > 0) (positionMs().toFloat() / currentDuration).coerceIn(0f, 1f) else 0f }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progressFraction(), 0f..1f)
                setProgress { target ->
                    seek((target.coerceIn(0f, 1f) * currentDuration).toLong()); true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    seek(((offset.x / size.width).coerceIn(0f, 1f) * currentDuration).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrub((dragFraction!! * currentDuration).toLong())
                    },
                    onDragEnd = {
                        dragFraction?.let { seek((it * currentDuration).toLong()) }
                        dragFraction = null
                        scrub(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        scrub(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrub((dragFraction!! * currentDuration).toLong())
                    },
                )
            }
            // Bar geometry depends only on size and seed: built once, then each frame only picks colors.
            .drawWithCache {
                val barWidth = 4.dp.toPx()
                val gap = 3.dp.toPx()
                val count = ((size.width + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)
                // Spread the leftover pixels over the gaps so the bars fill the whole width.
                val step = if (count > 1) (size.width - barWidth) / (count - 1) else 0f
                val maxH = size.height * 0.9f
                val minH = 6.dp.toPx()
                val radius = CornerRadius(2.dp.toPx())
                val markerWidth = 2.dp.toPx()
                val tops = FloatArray(count)
                val heights = FloatArray(count)
                for (i in 0 until count) {
                    val wave = abs(sin((i + phase) * 0.9f) * 14f + sin((i + phase) * 0.37f) * 10f)
                    heights[i] = (minH + wave / 24f * (maxH - minH)).coerceAtMost(maxH)
                    tops[i] = (size.height - heights[i]) / 2
                }
                onDrawBehind {
                    val played = progressFraction() * size.width
                    for (i in 0 until count) {
                        val x = i * step
                        drawRoundRect(
                            if (x + barWidth / 2 <= played) activeColor else inactiveColor,
                            topLeft = Offset(x, tops[i]),
                            size = Size(barWidth, heights[i]),
                            cornerRadius = radius,
                        )
                    }
                    // While scrubbing: a thin white marker at the finger.
                    if (dragFraction != null) {
                        drawRect(Color.White, topLeft = Offset(played - markerWidth / 2, 0f), size = Size(markerWidth, size.height))
                    }
                }
            },
    )
}
