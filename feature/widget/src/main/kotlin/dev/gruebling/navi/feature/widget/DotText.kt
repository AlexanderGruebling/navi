package dev.gruebling.navi.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.ceil
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Doto titles as bitmaps. Launchers inflate widgets in a restricted context, where TextView ignores font resources,
 * so the dot-matrix type can only reach a widget as an image.
 */
internal object DotText {

    /** [text] in upper case, wrapped into at most [maxLines] lines of [widthDp], the last one ellipsized. */
    fun render(context: Context, text: String, sizeSp: Float, widthDp: Int, maxLines: Int, @ColorInt color: Int): Bitmap? {
        val metrics = context.resources.displayMetrics
        val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), metrics).toInt()
        if (width <= 0) return null
        val typeface = runCatching { ResourcesCompat.getFont(context, UiR.font.doto) }.getOrNull() ?: return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            fontVariationSettings = "'wght' 900"
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            this.color = color
        }
        val upper = text.uppercase()
        val layout = StaticLayout.Builder.obtain(upper, 0, upper.length, paint, width)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        val textWidth = ceil((0 until layout.lineCount).maxOf { layout.getLineWidth(it) }).toInt().coerceIn(1, width)
        return createBitmap(textWidth, layout.height.coerceAtLeast(1)).apply {
            density = metrics.densityDpi
            android.graphics.Canvas(this).withTranslation { layout.draw(this) }
        }
    }

    /**
     * The widget's size in dp as the launcher last reported it (portrait: min width × max height), or [fallback]
     * before it has.
     */
    fun sizeDp(manager: AppWidgetManager, widgetId: Int, fallback: Pair<Int, Int>): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(widgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) width to height else fallback
    }
}
