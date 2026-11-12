package com.agy.ru

import android.app.Activity
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
import android.widget.TextView

/**
 * 100% Native Kotlin permission panel for Hibou, replacing HTML/CSS permission banner.
 * Styled in Material You (Electric Blue #38bdf8, Dark Matte #0f172a, 0 emojis).
 * Slides down smoothly from the top of the screen when required permissions are missing.
 */
object HibouPermissionSheet {
    private const val TAG_PERM_PANEL = "HIBOU_NATIVE_PERM_PANEL"

    private fun dp(ctx: Context, value: Int): Int {
        return try {
            val d = ctx.resources.displayMetrics.density
            (value * (if (d > 0f) d else 2.5f)).toInt()
        } catch (_: Exception) {
            value * 2
        }
    }

    @JvmStatic
    fun show(activity: Activity?, permissionType: String?) {
        if (activity == null || activity.isFinishing) return
        val type = (permissionType ?: "storage").lowercase().trim()

        activity.runOnUiThread {
            try {
                val decor = (activity.window?.decorView as? ViewGroup)
                    ?: (activity.findViewById(android.R.id.content) as? ViewGroup)
                    ?: return@runOnUiThread

                // Dismiss existing panel if any
                dismiss(activity, animated = false)

                val panel = FrameLayout(activity).apply {
                    tag = TAG_PERM_PANEL
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    ).apply {
                        val m = dp(activity, 14)
                        setMargins(m, dp(activity, 42), m, 0)
                    }
                }

                // Card background
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val cardBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(activity, 18).toFloat()
                        setColor(Color.parseColor("#0f172a"))
                        setStroke(dp(activity, 1), Color.parseColor("#38bdf8"))
                    }
                    background = cardBg
                    setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Top row with icon badge and title/description
                val topRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(activity, 12)
                    }
                }

                // Badge icon box (no emojis)
                val badgeText = when (type) {
                    "storage", "files" -> "FS"
                    "notifications", "notif" -> "NT"
                    "microphone", "mic" -> "MC"
                    else -> "PR"
                }

                val iconBox = TextView(activity).apply {
                    text = badgeText
                    textSize = 13f
                    setTextColor(Color.parseColor("#38bdf8"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    val boxBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(activity, 12).toFloat()
                        setColor(Color.parseColor("#172554"))
                        setStroke(dp(activity, 1), Color.parseColor("#38bdf8"))
                    }
                    background = boxBg
                    val size = dp(activity, 38)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        rightMargin = dp(activity, 12)
                    }
                }
                topRow.addView(iconBox)

                // Text block
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                }

                val (title, description) = when (type) {
                    "storage", "files" -> Pair(
                        "Storage Access Required",
                        "Kestrel needs storage access to read project workspaces, git trees, and run commands."
                    )
                    "notifications", "notif" -> Pair(
                        "Notification Access",
                        "Enables background build completion and agent task alerts."
                    )
                    "microphone", "mic" -> Pair(
                        "Microphone Access",
                        "Enables instant voice prompt input in chat."
                    )
                    else -> Pair("Permission Required", "This feature requires additional Android permissions.")
                }

                val titleView = TextView(activity).apply {
                    text = title
                    textSize = 14.5f
                    setTextColor(Color.parseColor("#f8fafc"))
                    typeface = Typeface.DEFAULT_BOLD
                }
                textCol.addView(titleView)

                val descView = TextView(activity).apply {
                    text = description
                    textSize = 12f
                    setTextColor(Color.parseColor("#94a3b8"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(activity, 2)
                    }
                }
                textCol.addView(descView)
                topRow.addView(textCol)
                card.addView(topRow)

                // Buttons row
                val btnRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val laterBtn = Button(activity).apply {
                    text = "Later"
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#94a3b8"))
                    background = null
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(activity, 36)
                    ).apply {
                        rightMargin = dp(activity, 8)
                    }
                    setOnClickListener {
                        dismiss(activity, animated = true)
                    }
                }
                btnRow.addView(laterBtn)

                val grantBtn = Button(activity).apply {
                    text = "Grant Access"
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#031427"))
                    typeface = Typeface.DEFAULT_BOLD
                    val btnBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(activity, 10).toFloat()
                        setColor(Color.parseColor("#38bdf8"))
                    }
                    background = btnBg
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(activity, 36)
                    )
                    setOnClickListener {
                        dismiss(activity, animated = true)
                        HibouBridge.requestPermission(activity, type)
                    }
                }
                btnRow.addView(grantBtn)
                card.addView(btnRow)

                panel.addView(card)
                decor.addView(panel)
                panel.bringToFront()

                // Entry animation: slide down from top with fade
                panel.alpha = 0f
                panel.translationY = -dp(activity, 30).toFloat()
                panel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .start()

            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun dismiss(activity: Activity?, animated: Boolean = true) {
        if (activity == null) return
        activity.runOnUiThread {
            try {
                val decor = (activity.window?.decorView as? ViewGroup)
                    ?: (activity.findViewById(android.R.id.content) as? ViewGroup)
                    ?: return@runOnUiThread

                val panel = decor.findViewWithTag<View>(TAG_PERM_PANEL) ?: return@runOnUiThread
                if (!animated) {
                    decor.removeView(panel)
                    return@runOnUiThread
                }

                panel.animate()
                    .alpha(0f)
                    .translationY(-dp(activity, 20).toFloat())
                    .setDuration(200)
                    .withEndAction {
                        decor.removeView(panel)
                    }
                    .start()
            } catch (_: Exception) {}
        }
    }
}
