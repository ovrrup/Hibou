package com.agy.ru

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * Native chat shell.
 *
 * M1 renders navigation, transcript and composer from platform widgets.  M2
 * added [HibouChatClient]; the composer now drives real cascades on the local
 * core and renders the streamed `AgentStateUpdate` steps.  When the core is not
 * reachable yet the view degrades to a local echo plus the WebView fallback in
 * the drawer, so the migration never blocks the app.
 */
class HibouChatView(context: Context) : FrameLayout(context) {

    var onOpenWeb: (() -> Unit)? = null
    var onNewConversation: (() -> Unit)? = null

    private val store = HibouChatStore(context)
    private val client = HibouChatClient(context)
    private val root = LinearLayout(context)
    private val transcript = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val input = EditText(context)
    private val drawer = FrameLayout(context)
    private val drawerPanel = LinearLayout(context)
    private val modelPill = TextView(context)
    private val statusLabel = TextView(context)
    private val sendButton = Button(context)

    private var drawerOpen = false
    @Volatile private var stream: HibouStreamHandle? = null
    private var streamingMessage: HibouChatMessage? = null
    private val liveText = StringBuilder()
    private var coreConversations: List<HibouConversationSummary> = emptyList()

    init {
        setBackgroundColor(HibouTheme.color(HibouTheme.BG))
        buildRoot()
        buildDrawer()
        render()
        refreshStatus()
    }

    // ---------------------------------------------------------------------
    // Shell
    // ---------------------------------------------------------------------

    private fun buildRoot() {
        root.orientation = LinearLayout.VERTICAL
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        root.addView(buildTopBar())

        scroll.setBackgroundColor(HibouTheme.color(HibouTheme.BG))
        transcript.orientation = LinearLayout.VERTICAL
        transcript.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(transcript, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(buildComposer())
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(HibouTheme.color(HibouTheme.BG))
        bar.setPadding(dp(10), dp(10), dp(10), dp(8))
        bar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val menu = iconButton("\u2261")
        menu.contentDescription = "Open navigation"
        menu.setOnClickListener { toggleDrawer() }
        bar.addView(menu)

        val titleBlock = LinearLayout(context)
        titleBlock.orientation = LinearLayout.VERTICAL
        titleBlock.setPadding(dp(10), 0, 0, 0)
        titleBlock.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val title = label("Hibou", 18f, HibouTheme.TEXT, true)
        titleBlock.addView(title)
        statusLabel.setTextColor(HibouTheme.color(HibouTheme.TEXT_MUTED))
        statusLabel.textSize = 11f
        titleBlock.addView(statusLabel)
        bar.addView(titleBlock)

        val newChat = iconButton("+")
        newChat.contentDescription = "New chat"
        newChat.setOnClickListener {
            stopStreaming()
            store.newConversation()
            render()
            onNewConversation?.invoke()
        }
        bar.addView(newChat)
        return bar
    }

    private fun buildComposer(): View {
        val wrap = LinearLayout(context)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(HibouTheme.color(HibouTheme.BG))
        wrap.setPadding(dp(12), dp(8), dp(12), dp(12))
        wrap.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val metaRow = LinearLayout(context)
        metaRow.orientation = LinearLayout.HORIZONTAL
        metaRow.gravity = Gravity.CENTER_VERTICAL

        modelPill.text = "Auto \u00b7 local core"
        modelPill.setTextColor(HibouTheme.color(HibouTheme.ACCENT))
        modelPill.textSize = 12f
        modelPill.setTypeface(HibouTheme.bold())
        modelPill.setPadding(dp(12), dp(6), dp(12), dp(6))
        modelPill.background = HibouTheme.pill(HibouTheme.SURFACE_HIGH, context, HibouTheme.ACCENT_DARK)
        modelPill.setOnClickListener {
            Toast.makeText(context, "Model picker arrives with M3", Toast.LENGTH_SHORT).show()
        }
        metaRow.addView(modelPill)

        metaRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val providerButton = label("Providers", 12f, HibouTheme.TEXT_MUTED, false)
        providerButton.setPadding(dp(8), dp(6), dp(8), dp(6))
        providerButton.setOnClickListener {
            HibouControlCenterDialog.show(activity())
        }
        metaRow.addView(providerButton)
        wrap.addView(metaRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(8) })

        val inputRow = LinearLayout(context)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.BOTTOM

        input.hint = "Ask Hibou to build something\u2026"
        input.setTextColor(HibouTheme.color(HibouTheme.TEXT))
        input.setHintTextColor(HibouTheme.color(HibouTheme.TEXT_DIM))
        input.textSize = 15f
        input.setSingleLine(false)
        input.setMaxLines(5)
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        input.background = HibouTheme.rounded(HibouTheme.SURFACE, 18, context, 1, HibouTheme.BORDER)
        input.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        inputRow.addView(input)

        sendButton.text = "Send"
        sendButton.setAllCaps(false)
        sendButton.setTextColor(HibouTheme.color(HibouTheme.ACCENT_INK))
        sendButton.textSize = 14f
        sendButton.setTypeface(HibouTheme.bold())
        sendButton.background = HibouTheme.rounded(HibouTheme.ACCENT, 16, context)
        sendButton.setPadding(dp(16), dp(10), dp(16), dp(10))
        sendButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.leftMargin = dp(8) }
        sendButton.setOnClickListener { submit() }
        inputRow.addView(sendButton)

        wrap.addView(inputRow)
        return wrap
    }

    // ---------------------------------------------------------------------
    // Transcript
    // ---------------------------------------------------------------------

    private fun render() {
        transcript.removeAllViews()
        val conversation = store.active()
        if (conversation.messages.isEmpty()) {
            transcript.addView(buildEmptyState())
        } else {
            for (message in conversation.messages) {
                transcript.addView(buildMessage(message))
            }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun buildEmptyState(): View {
        val wrap = LinearLayout(context)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(8), dp(28), dp(8), dp(24))

        val badge = label("NATIVE KOTLIN UI", 10f, HibouTheme.ACCENT, true)
        badge.setPadding(dp(10), dp(4), dp(10), dp(4))
        badge.background = HibouTheme.pill(HibouTheme.SURFACE_HIGH, context, HibouTheme.ACCENT_DARK)
        wrap.addView(badge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(18) })

        val headline = label("What should we build?", 28f, HibouTheme.TEXT, true)
        headline.setLineSpacing(0f, 1.05f)
        wrap.addView(headline)

        val sub = label(
            if (client.isReady())
                "Connected to the local core. Messages stream straight from the agent."
            else
                "The core is still starting. You can draft a message now; the drawer keeps the web UI one tap away.",
            14f, HibouTheme.TEXT_MUTED, false
        )
        sub.setLineSpacing(dp(2).toFloat(), 1.15f)
        sub.setPadding(0, dp(8), 0, dp(22))
        wrap.addView(sub)

        for (suggestion in listOf(
            "Explain this codebase",
            "Fix a failing test",
            "Plan a feature"
        )) {
            val chip = label(suggestion, 13f, HibouTheme.TEXT, false)
            chip.setPadding(dp(14), dp(10), dp(14), dp(10))
            chip.background = HibouTheme.rounded(HibouTheme.SURFACE, 14, context, 1, HibouTheme.BORDER)
            chip.setOnClickListener {
                input.setText(suggestion)
                input.setSelection(input.text.length)
            }
            wrap.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) })
        }
        return wrap
    }

    private fun buildMessage(message: HibouChatMessage): View {
        val wrap = LinearLayout(context)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(0, dp(6), 0, dp(10))

        val user = message.role == "user"
        val meta = label(if (user) "YOU" else "HIBOU", 10f,
            if (user) HibouTheme.ACCENT else HibouTheme.SUCCESS, true)
        meta.setPadding(0, 0, 0, dp(4))
        wrap.addView(meta)

        val bubble = TextView(context)
        val text = message.text
        bubble.text = if (!user && text.isBlank()) "Thinking\u2026" else text
        bubble.setTextColor(HibouTheme.color(
            if (!user && text.isBlank()) HibouTheme.TEXT_DIM else HibouTheme.TEXT))
        bubble.textSize = 15f
        bubble.setLineSpacing(dp(2).toFloat(), 1.15f)
        bubble.setTextIsSelectable(true)
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12))
        bubble.background = HibouTheme.rounded(
            if (user) HibouTheme.SURFACE_HIGH else HibouTheme.SURFACE,
            16, context, 1,
            if (user) HibouTheme.ACCENT_DARK else HibouTheme.BORDER
        )
        wrap.addView(bubble, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        for (tool in message.tools) {
            val chip = label(tool, 11f, HibouTheme.TEXT_MUTED, false)
            chip.setPadding(dp(10), dp(6), dp(10), dp(6))
            chip.background = HibouTheme.rounded(HibouTheme.BG, 10, context, 1, HibouTheme.BORDER)
            wrap.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6) })
        }
        return wrap
    }

    // ---------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------

    private fun submit() {
        if (streamingMessage != null) {
            stopStreaming()
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")

        val conversation = store.active()
        store.append("user", text)
        val assistant = store.append("assistant", "")
        streamingMessage = assistant
        liveText.setLength(0)
        render()

        if (!client.isReady()) {
            postDelayed({
                store.update(assistant,
                    "The core is not reachable yet. Wait for the status to show \"Core ready\", " +
                        "or open the web UI from the drawer.")
                streamingMessage = null
                render()
                refreshStatus()
            }, 400)
            return
        }

        setStreaming(true)
        val existing = conversation.cascadeId
        Thread({
            val cascade = existing ?: client.newCascadeId()
            try {
                if (existing == null) {
                    client.startCascade(cascade)
                    post {
                        conversation.cascadeId = cascade
                        store.save()
                    }
                }
                client.sendMessage(cascade, text)
                stream = client.streamUpdates(
                    cascade,
                    onUpdate = { update -> applyUpdate(update) },
                    onDone = { error -> finishStreaming(error) }
                )
            } catch (error: Throwable) {
                finishStreaming(error)
            }
        }, "hibou-send").start()
    }

    private fun applyUpdate(update: JSONObject) {
        val steps = client.stepsFromUpdate(update)
        if (steps.isEmpty() && !update.optBoolean("fullyIdle", false)) return
        post {
            val message = streamingMessage ?: return@post
            for (step in steps) {
                if (step.text.isNotBlank()) {
                    liveText.setLength(0)
                    liveText.append(step.text)
                }
                val tool = step.tool
                if (tool != null && !message.tools.contains(tool)) {
                    message.tools.add(tool)
                }
            }
            if (liveText.isNotEmpty()) {
                message.text = liveText.toString()
            }
            if (update.optBoolean("fullyIdle", false)) {
                statusLabel.text = "Core ready \u00b7 idle"
            } else {
                statusLabel.text = "Core ready \u00b7 streaming"
            }
            render()
        }
    }

    private fun finishStreaming(error: Throwable?) {
        post {
            val message = streamingMessage
            if (message != null) {
                if (error != null && message.text.isBlank()) {
                    message.text = "Stream ended: " +
                        (error.message ?: error.javaClass.simpleName)
                } else if (message.text.isBlank()) {
                    message.text = "No response was produced. Check the core log or open the web UI."
                }
                store.save()
            }
            streamingMessage = null
            stream = null
            setStreaming(false)
            render()
            refreshStatus()
        }
    }

    private fun stopStreaming() {
        val handle = stream
        stream = null
        handle?.cancel()
        post {
            val message = streamingMessage
            if (message != null && message.text.isBlank()) {
                message.text = "Stopped."
                store.save()
            }
            streamingMessage = null
            setStreaming(false)
            render()
            refreshStatus()
        }
    }

    private fun setStreaming(active: Boolean) {
        sendButton.text = if (active) "Stop" else "Send"
        sendButton.background = HibouTheme.rounded(
            if (active) HibouTheme.DANGER else HibouTheme.ACCENT, 16, context)
        sendButton.setTextColor(HibouTheme.color(
            if (active) HibouTheme.TEXT else HibouTheme.ACCENT_INK))
    }

    private fun refreshStatus() {
        if (stream != null) {
            statusLabel.text = "Core ready \u00b7 streaming"
            statusLabel.setTextColor(HibouTheme.color(HibouTheme.SUCCESS))
        } else if (client.isReady()) {
            statusLabel.text = "Core ready \u00b7 local"
            statusLabel.setTextColor(HibouTheme.color(HibouTheme.SUCCESS))
        } else {
            statusLabel.text = "Core starting \u00b7 web fallback available"
            statusLabel.setTextColor(HibouTheme.color(HibouTheme.TEXT_MUTED))
        }
    }

    // ---------------------------------------------------------------------
    // Drawer
    // ---------------------------------------------------------------------

    private fun buildDrawer() {
        drawer.setBackgroundColor(HibouTheme.color("#99000000"))
        drawer.visibility = View.GONE
        drawer.setOnClickListener { closeDrawer() }
        addView(drawer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        drawerPanel.orientation = LinearLayout.VERTICAL
        drawerPanel.setBackgroundColor(HibouTheme.color(HibouTheme.SURFACE))
        drawerPanel.setPadding(dp(18), dp(22), dp(18), dp(18))
        drawerPanel.setOnClickListener { /* consume taps */ }
        drawer.addView(drawerPanel, FrameLayout.LayoutParams(
            dp(300),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.START
        ))
        refreshDrawer()
    }

    private fun refreshDrawer() {
        drawerPanel.removeAllViews()

        val brand = label("Hibou", 24f, HibouTheme.TEXT, true)
        drawerPanel.addView(brand)
        val tagline = label("Kotlin control surface", 12f, HibouTheme.TEXT_MUTED, false)
        tagline.setPadding(0, dp(2), 0, dp(18))
        drawerPanel.addView(tagline)

        drawerItem("New chat", true) {
            stopStreaming()
            store.newConversation()
            render()
            closeDrawer()
        }
        drawerItem("Refresh from core", false) { refreshCoreConversations() }
        drawerItem("Providers", false) {
            closeDrawer()
            HibouControlCenterDialog.show(activity())
        }
        drawerItem("Tools & console", false) { openWeb("Tools") }
        drawerItem("MCP & skills", false) { openWeb("MCP") }
        drawerItem("Settings", false) { openWeb("Settings") }
        drawerItem("Updates", false) { openWeb("Updates") }

        val divider = View(context)
        divider.setBackgroundColor(HibouTheme.color(HibouTheme.BORDER))
        drawerPanel.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).also { it.topMargin = dp(12); it.bottomMargin = dp(12) })

        val coreTitle = label("FROM CORE", 10f, HibouTheme.TEXT_DIM, true)
        coreTitle.setPadding(0, 0, 0, dp(6))
        drawerPanel.addView(coreTitle)
        if (coreConversations.isEmpty()) {
            drawerPanel.addView(label(
                if (client.isReady()) "No trajectories yet" else "Core offline",
                13f, HibouTheme.TEXT_DIM, false))
        } else {
            for (conversation in coreConversations.take(5)) {
                val item = label(conversation.title, 13f, HibouTheme.TEXT_MUTED, false)
                item.setPadding(dp(2), dp(8), dp(2), dp(8))
                item.setOnClickListener { openCascade(conversation.id) }
                drawerPanel.addView(item)
            }
        }

        val recentTitle = label("LOCAL DRAFTS", 10f, HibouTheme.TEXT_DIM, true)
        recentTitle.setPadding(0, dp(12), 0, dp(6))
        drawerPanel.addView(recentTitle)
        val recent = store.conversations.take(4)
        if (recent.isEmpty()) {
            drawerPanel.addView(label("No conversations yet", 13f, HibouTheme.TEXT_DIM, false))
        } else {
            for (conversation in recent) {
                val item = label(conversation.title, 13f, HibouTheme.TEXT_MUTED, false)
                item.setPadding(dp(2), dp(8), dp(2), dp(8))
                item.setOnClickListener {
                    store.select(conversation.id)
                    render()
                    closeDrawer()
                }
                drawerPanel.addView(item)
            }
        }

        drawerPanel.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))

        drawerItem("Open web UI (fallback)", false) {
            closeDrawer()
            onOpenWeb?.invoke()
        }
        val footer = label("M2 \u00b7 core client", 11f, HibouTheme.TEXT_DIM, false)
        footer.setPadding(dp(2), dp(10), 0, 0)
        drawerPanel.addView(footer)
    }

    private fun refreshCoreConversations() {
        if (!client.isReady()) {
            Toast.makeText(context, "Core is still starting", Toast.LENGTH_SHORT).show()
            return
        }
        Thread({
            val conversations = try {
                client.conversations()
            } catch (_: Throwable) {
                emptyList()
            }
            post {
                coreConversations = conversations
                refreshDrawer()
                Toast.makeText(context, conversations.size.toString() + " trajectories", Toast.LENGTH_SHORT).show()
            }
        }, "hibou-history").start()
    }

    private fun openCascade(cascadeId: String) {
        closeDrawer()
        Thread({
            val steps = try {
                client.steps(cascadeId)
            } catch (_: Throwable) {
                emptyList()
            }
            post {
                val conversation = store.findByCascade(cascadeId)
                    ?: store.newConversation().also { it.cascadeId = cascadeId }
                conversation.messages.clear()
                for (step in steps) {
                    val text = step.text
                    val role = when {
                        step.type.endsWith("USER_INPUT") -> "user"
                        text.isNotBlank() -> "assistant"
                        else -> continue
                    }
                    val message = HibouChatMessage(
                        java.util.UUID.randomUUID().toString(), role, text,
                        System.currentTimeMillis())
                    step.tool?.let { message.tools.add(it) }
                    conversation.messages.add(message)
                }
                conversation.updatedAt = System.currentTimeMillis()
                store.save()
                store.select(conversation.id)
                render()
            }
        }, "hibou-load").start()
    }

    private fun drawerItem(text: String, accent: Boolean, action: () -> Unit) {
        val item = label(text, 15f,
            if (accent) HibouTheme.ACCENT else HibouTheme.TEXT, accent)
        item.setPadding(dp(2), dp(12), dp(2), dp(12))
        item.setOnClickListener { action() }
        drawerPanel.addView(item)
    }

    private fun openWeb(feature: String) {
        closeDrawer()
        Toast.makeText(context, feature + " is available in the web fallback until M5",
            Toast.LENGTH_SHORT).show()
    }

    fun toggleDrawer() {
        if (drawerOpen) closeDrawer() else openDrawer()
    }

    fun openDrawer() {
        refreshStatus()
        refreshDrawer()
        drawerOpen = true
        drawer.visibility = View.VISIBLE
        drawerPanel.translationX = -dp(40).toFloat()
        drawerPanel.alpha = 0f
        drawerPanel.animate().translationX(0f).alpha(1f).setDuration(180).start()
        refreshCoreConversations()
    }

    fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawer.animate().alpha(0f).setDuration(140).withEndAction {
            drawer.visibility = View.GONE
            drawer.alpha = 1f
        }.start()
    }

    fun handleBack(): Boolean {
        if (drawerOpen) {
            closeDrawer()
            return true
        }
        if (stream != null) {
            stopStreaming()
            return true
        }
        return false
    }

    fun onDestroy() {
        stopStreaming()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun dp(value: Int): Int = HibouTheme.dp(context, value)

    private fun activity(): android.app.Activity? = context as? android.app.Activity

    private fun label(text: String, size: Float, color: String, bold: Boolean): TextView {
        val view = TextView(context)
        view.text = text
        view.setTextColor(HibouTheme.color(color))
        view.textSize = size
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD)
        return view
    }

    private fun iconButton(glyph: String): TextView {
        val view = TextView(context)
        view.text = glyph
        view.setTextColor(HibouTheme.color(HibouTheme.TEXT))
        view.textSize = 22f
        view.gravity = Gravity.CENTER
        view.setTypeface(HibouTheme.bold())
        view.background = HibouTheme.rounded(HibouTheme.SURFACE, 14, context, 1, HibouTheme.BORDER)
        view.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        return view
    }
}
