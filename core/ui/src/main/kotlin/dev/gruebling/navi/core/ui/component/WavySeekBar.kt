package dev.gruebling.navi.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Seek bar in the style of Android's media controls: while playing, the played part is a travelling wave; on pause
 * the wave flattens into a line. Tap to jump, drag to scrub ([onScrub] reports the preview position, [onSeek] fires
 * on release).
 *
 * [positionMs] is read only in the draw phase, so position updates redraw the bar without recomposition. The wave
 * animates frame by frame only while [isPlaying]; a paused bar costs nothing.
 */
@Composable
fun WavySeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: (Long?) -> Unit = {},
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
) {
    val amplitude by animateFloatAsState(if (isPlaying) 1f else 0f, tween(durationMillis = 600), label = "amplitude")
    val phase = remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentDuration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)

    // Move the wave only while it is visible.
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                phase.floatValue = (phase.floatValue + (now - last) / 1e9f * WAVE_SPEED) % (2f * PI.toFloat())
                last = now
            }
        }
    }

    val progressFraction = { dragFraction ?: if (currentDuration > 0) (positionMs().toFloat() / currentDuration).coerceIn(0f, 1f) else 0f }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
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
            },
    ) {
        val stroke = 4.dp.toPx()
        val waveHeight = 3.dp.toPx() * amplitude
        val wavelength = 24.dp.toPx()
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = 20.dp.toPx()
        val gap = 4.dp.toPx()
        val cy = size.height / 2
        val x = progressFraction() * size.width

        // Remaining part: straight line.
        val restStart = x + thumbWidth / 2 + gap
        if (restStart < size.width) {
            drawLine(inactiveColor, Offset(restStart, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        }

        // Played part: wave (or a line once flattened).
        val playedEnd = x - thumbWidth / 2 - gap
        if (playedEnd > 0f) {
            if (waveHeight < 0.5f) {
                drawLine(activeColor, Offset(0f, cy), Offset(playedEnd, cy), stroke, StrokeCap.Round)
            } else {
                val path = Path()
                val step = 2.dp.toPx()
                var px = 0f
                val k = 2f * PI.toFloat() / wavelength
                path.moveTo(0f, cy + waveHeight * sin(-phase.floatValue))
                while (px < playedEnd) {
                    px = (px + step).coerceAtMost(playedEnd)
                    path.lineTo(px, cy + waveHeight * sin(k * px - phase.floatValue))
                }
                drawPath(path, activeColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Thumb: a short vertical pill, taller while scrubbing.
        val h = if (dragFraction != null) thumbHeight * 1.3f else thumbHeight
        drawRoundRect(
            activeColor,
            topLeft = Offset(x - thumbWidth / 2, cy - h / 2),
            size = Size(thumbWidth, h),
            cornerRadius = CornerRadius(thumbWidth / 2),
        )
    }
}

/** Radians per second: one wavelength roughly every 1.6 s, calm like the system player. */
private const val WAVE_SPEED = 4f
