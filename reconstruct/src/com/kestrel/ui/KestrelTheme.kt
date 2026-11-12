package com.kestrel.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue

/**
 * Kestrel design tokens — the mono-graphite identity.
 *
 * One palette, two type voices: system sans for prose, monospace for anything
 * the engine "says" (wordmark, status line, tool output).  The single accent
 * is electric cyan, used sparingly: wordmark cursor, primary actions, the
 * ready status dot.
 *
 * Values mirror the patched resource layer: [BG] equals the rebranded
 * `window_background` color so the activity and the shell read as one surface.
 */
object KestrelTheme {
    // graphite surfaces
    const val BG = 0xFF14171C.toInt()
    const val SURFACE = 0xFF1A1F26.toInt()
    const val SURFACE_HI = 0xFF212831.toInt()
    const val HAIRLINE = 0xFF272D36.toInt()

    // ink
    const val TEXT = 0xFFE8ECF1.toInt()
    const val MUTED = 0xFF8A93A0.toInt()
    const val DIM = 0xFF5A6472.toInt()

    // the single accent + status colors
    const val ACCENT = 0xFF3FD9FF.toInt()
    const val ACCENT_DIM = 0xFF2A7E96.toInt()
    const val AMBER = 0xFFFFB454.toInt()
    const val RED = 0xFFFF6B6B.toInt()

    val MONO: Typeface = Typeface.MONOSPACE
    val PROSE: Typeface = Typeface.SANS_SERIF

    fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }
}
