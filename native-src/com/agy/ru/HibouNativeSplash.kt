package com.agy.ru

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * High-performance, 100% native Kotlin splash screen and fatal error surface for Hibou.
 * Replaces SplashLoadingHtml and SplashOverlayHtml WebView rendering with zero-overhead native Views.
 * Strictly adheres to Material You dark aesthetics (Electric Blue #38bdf8, Night Sky #0b0e17, 0 emojis).
 */
object HibouNativeSplash {
    private const val TAG_SPLASH = "HIBOU_NATIVE_SPLASH"

    private fun dp(ctx: Context, value: Int): Int {
        return try {
            val d = ctx.resources.displayMetrics.density
            (value * (if (d > 0f) d else 2.5f)).toInt()
        } catch (_: Exception) {
            value * 2
        }
    }

    @JvmStatic
    fun show(parent: ViewGroup?, foregroundHex: String?, backgroundHex: String?): View? {
        if (parent == null) return null
        val ctx = parent.context

        // Remove any existing splash
        hide(parent, animated = false)

        val splash = FrameLayout(ctx).apply {
            tag = TAG_SPLASH
            val bg = try {
                Color.parseColor(backgroundHex ?: "#0b0e17")
            } catch (_: Exception) {
                Color.parseColor("#0b0e17")
            }
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Ambient pulsing halo glow
        val haloView = View(ctx).apply {
            val haloDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(ctx, 160).toFloat()
                setColor(Color.parseColor("#2238bdf8"))
            }
            background = haloDrawable
            val s = dp(ctx, 280)
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
            alpha = 0.6f
        }
        splash.addView(haloView)

        // 2. Central vertical column
        val centerCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // App Icon squircle
        val iconBox = FrameLayout(ctx).apply {
            val size = dp(ctx, 68)
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 20).toFloat()
                setColor(Color.parseColor("#152238"))
                setStroke(dp(ctx, 2), Color.parseColor("#38bdf8"))
            }
            background = iconBg
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(ctx, 18)
            }
        }

        // Geometric owl eye symbol inside icon (no emojis)
        val iconGlyph = TextView(ctx).apply {
            text = "H"
            textSize = 28f
            setTextColor(Color.parseColor("#38bdf8"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        iconBox.addView(iconGlyph)
        centerCol.addView(iconBox)

        // App title
        val titleText = TextView(ctx).apply {
            text = "Hibou"
            textSize = 22f
            setTextColor(Color.parseColor("#f8fafc"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(ctx, 20)
            }
        }
        centerCol.addView(titleText)

        // Indeterminate Progress Bar
        val progressBar = ProgressBar(ctx).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 140), dp(ctx, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        centerCol.addView(progressBar)

        splash.addView(centerCol)
        parent.addView(splash)
        splash.bringToFront()

        // Subtle entry pulse animation
        centerCol.alpha = 0f
        centerCol.translationY = dp(ctx, 12).toFloat()
        centerCol.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320)
            .start()

        haloView.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .alpha(0.85f)
            .setDuration(1200)
            .start()

        return splash
    }

    @JvmStatic
    fun hide(parent: ViewGroup?, animated: Boolean = true) {
        if (parent == null) return
        val splash = parent.findViewWithTag<View>(TAG_SPLASH) ?: return

        if (!animated) {
            parent.removeView(splash)
            return
        }

        splash.animate()
            .alpha(0f)
            .setDuration(240)
            .withEndAction {
                parent.removeView(splash)
            }
            .start()
    }

    @JvmStatic
    fun showFatalError(
        parent: ViewGroup?,
        title: String?,
        errorText: String?,
        retryAction: Runnable?
    ) {
        if (parent == null) return
        val ctx = parent.context

        // Remove loading state if present
        hide(parent, animated = false)

        val errLayout = FrameLayout(ctx).apply {
            tag = TAG_SPLASH
            setBackgroundColor(Color.parseColor("#0b0e17"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Material You dark card container
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 20).toFloat()
                setColor(Color.parseColor("#0f172a"))
                setStroke(dp(ctx, 1), Color.parseColor("#475569"))
            }
            background = cardBg
            setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 20))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val m = dp(ctx, 20)
                setMargins(m, m, m, m)
            }
        }

        // Header: Error title with red accent
        val titleView = TextView(ctx).apply {
            text = if (title.isNullOrBlank()) "Hibou Failed to Start" else title
            textSize = 18f
            setTextColor(Color.parseColor("#f87171"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 12)
            }
        }
        card.addView(titleView)

        // Monospace log box in scroll view
        val logScroll = ScrollView(ctx).apply {
            val logBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(Color.parseColor("#030712"))
                setStroke(dp(ctx, 1), Color.parseColor("#1e293b"))
            }
            background = logBg
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(ctx, 220)
            ).apply {
                bottomMargin = dp(ctx, 16)
            }
        }

        val logText = TextView(ctx).apply {
            text = errorText ?: "No error output captured."
            textSize = 12f
            setTextColor(Color.parseColor("#cbd5e1"))
            typeface = Typeface.MONOSPACE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        logScroll.addView(logText)
        card.addView(logScroll)

        // Action buttons row
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Copy button
        val copyBtn = Button(ctx).apply {
            text = "Copy Logs"
            textSize = 13f
            setTextColor(Color.parseColor("#94a3b8"))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 10).toFloat()
                setColor(Color.parseColor("#1e293b"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(ctx, 40)
            ).apply {
                rightMargin = dp(ctx, 10)
            }
            setOnClickListener {
                try {
                    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cb?.setPrimaryClip(ClipData.newPlainText("Hibou Error", errorText ?: ""))
                    Toast.makeText(ctx, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }
        btnRow.addView(copyBtn)

        // Retry button
        if (retryAction != null) {
            val retryBtn = Button(ctx).apply {
                text = "Retry"
                textSize = 13f
                setTextColor(Color.parseColor("#031427"))
                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(ctx, 10).toFloat()
                    setColor(Color.parseColor("#38bdf8"))
                }
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(ctx, 40)
                )
                setOnClickListener {
                    hide(parent, animated = true)
                    retryAction.run()
                }
            }
            btnRow.addView(retryBtn)
        }

        card.addView(btnRow)
        errLayout.addView(card)
        parent.addView(errLayout)
        errLayout.bringToFront()
    }
}
