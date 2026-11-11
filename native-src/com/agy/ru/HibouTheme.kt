package com.agy.ru

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

/**
 * Shared visual language for the native Kotlin UI.
 *
 * Values mirror the Electric Blue / Night Sky palette already used by
 * HibouNativeSplash and the web control center so the migration does not
 * introduce a second theme.
 */
object HibouTheme {
    const val BG = "#0b0e17"
    const val SURFACE = "#0f172a"
    const val SURFACE_HIGH = "#152238"
    const val BORDER = "#1e293b"
    const val BORDER_STRONG = "#334155"
    const val TEXT = "#f8fafc"
    const val TEXT_MUTED = "#94a3b8"
    const val TEXT_DIM = "#64748b"
    const val ACCENT = "#38bdf8"
    const val ACCENT_DARK = "#0284c7"
    const val ACCENT_INK = "#031427"
    const val DANGER = "#f87171"
    const val SUCCESS = "#34d399"

    fun dp(ctx: Context, value: Int): Int {
        return try {
            val density = ctx.resources.displayMetrics.density
            (value * (if (density > 0f) density else 2.5f)).toInt()
        } catch (_: Exception) {
            value * 2
        }
    }

    fun color(value: String): Int {
        return try {
            Color.parseColor(value)
        } catch (_: Exception) {
            Color.parseColor(BG)
        }
    }

    fun rounded(fill: String, radiusDp: Int, ctx: Context,
                strokeDp: Int = 0, stroke: String? = null): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = dp(ctx, radiusDp).toFloat()
        drawable.setColor(color(fill))
        if (strokeDp > 0 && stroke != null) {
            drawable.setStroke(dp(ctx, strokeDp), color(stroke))
        }
        return drawable
    }

    fun pill(fill: String, ctx: Context, stroke: String? = null): GradientDrawable {
        return rounded(fill, 999, ctx, if (stroke == null) 0 else 1, stroke)
    }

    fun bold(): Typeface = Typeface.DEFAULT_BOLD
    fun mono(): Typeface = Typeface.MONOSPACE
}
