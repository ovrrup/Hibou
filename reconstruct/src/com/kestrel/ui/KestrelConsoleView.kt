package com.kestrel.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.agy.core.CoreRuntimeState
import com.agy.install.EnvInstaller
import com.agy.ru.HibouBridge
import com.agy.ru.HibouChatClient
import com.agy.ru.HibouChatMessage
import com.agy.ru.HibouChatStore
import com.agy.ru.HibouMarkdown
import com.agy.ru.HibouStreamHandle
import org.json.JSONObject

/**
 * The Kestrel console — the entire visual layer of the redesigned app.
 *
 * Terminal-inspired, mono-graphite: a wordmark with a blinking cursor, a
 * status dot that reports the engine, a transcript that reads like a session
 * log ("you ▸" / "kestrel ▸" prefixes, dim mono tool lines), and a single
 * composer.  Programmatic views only — no XML resources — so the payload
 * resource table stays untouched.
 *
 * Engine behaviour mirrors the ported baseline chat shell: same cascade
 * lifecycle (StartCascade → SendUserCascadeMessage → StreamAgentStateUpdates),
 * same local store, Kestrel strings.
 */
class KestrelConsoleView(context: Context) : FrameLayout(context) {

    private val client = HibouChatClient(context)
    private val store = HibouChatStore(context)

    private lateinit var transcript: LinearLayout
    private lateinit var transcriptScroll: ScrollView
    private lateinit var statusDot: TextView
    private lateinit var statusLabel: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private lateinit var bootOverlay: LinearLayout
    private lateinit var bootStatus: TextView
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var onboardingOverlay: LinearLayout
    private lateinit var historyButton: TextView

    private var streamingMessage: HibouChatMessage? = null
    private var stream: HibouStreamHandle? = null
    private var liveText = StringBuilder()
    private var bootThread: Thread? = null
    private var booted = false
    private var atBottom = true
    private val messageViews = mutableMapOf<String, MessageRow>()
    private var cursorBlinking = false

    private class MessageRow(
        val toolsContainer: LinearLayout,
        val thinkingContainer: TextView,
        val body: TextView,
    )

    init {
        setBackgroundColor(KestrelTheme.BG)
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(buildTopBar(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, KestrelTheme.dp(context, 52)))
        transcriptScroll = ScrollView(context)
        transcriptScroll.isVerticalScrollBarEnabled = false
        transcriptScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            atBottom = !transcriptScroll.canScrollVertically(1)
        }
        transcript = LinearLayout(context)
        transcript.orientation = LinearLayout.VERTICAL
        transcript.setPadding(dip(16), dip(12), dip(16), dip(12))
        transcriptScroll.addView(transcript, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(transcriptScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildComposer(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bootOverlay = buildBootOverlay()
        addView(bootOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        errorOverlay = buildErrorOverlay()
        bootOverlay.visibility = GONE
        errorOverlay.visibility = GONE
        addView(errorOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onboardingOverlay = buildOnboarding()
        onboardingOverlay.visibility = GONE
        addView(onboardingOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        render()
    }

    // -- top bar ----------------------------------------------------------

    private fun buildTopBar(): View {
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dip(16), 0, dip(8), 0)
        bar.setBackgroundColor(KestrelTheme.BG)

        val wordmark = TextView(context)
        wordmark.text = "KESTREL"
        wordmark.typeface = KestrelTheme.MONO
        wordmark textSizeSp 15f
        wordmark.setTextColor(KestrelTheme.TEXT)
        wordmark.setLetterSpacing(0.12f)
        bar.addView(wordmark)

        val cursor = TextView(context)
        cursor.text = "▌"
        cursor.typeface = KestrelTheme.MONO
        cursor textSizeSp 15f
        cursor.setTextColor(KestrelTheme.ACCENT)
        bar.addView(cursor, linear(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).margin(dip(2), 0, 0, 0))
        startCursorBlink(cursor)

        val spring = View(context)
        bar.addView(spring, linear(0, 0).weight(1f))

        statusDot = TextView(context)
        statusDot.text = "●"
        statusDot typeSp 11f
        statusDot.setTextColor(KestrelTheme.AMBER)
        bar.addView(statusDot, linear(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).margin(0, 0, dip(5), 0))

        statusLabel = TextView(context)
        statusLabel.text = "booting"
        statusLabel.typeface = KestrelTheme.MONO
        statusLabel textSizeSp 11f
        statusLabel.setTextColor(KestrelTheme.MUTED)
        bar.addView(statusLabel)

        historyButton = iconButton("≡")
        historyButton.setOnClickListener { openHistory() }
        bar.addView(historyButton, linear(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).margin(dip(8), 0, 0, 0))

        val newChat = iconButton("+")
        newChat.setOnClickListener { newChat() }
        bar.addView(newChat, linear(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).margin(dip(4), 0, 0, 0))

        val rule = View(context)
        rule.setBackgroundColor(KestrelTheme.HAIRLINE)
        val barWrap = LinearLayout(context)
        barWrap.orientation = LinearLayout.VERTICAL
        barWrap.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        barWrap.addView(rule, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dip(1))))
        return barWrap
    }

    // -- composer -----------------------------------------------------------

    private fun buildComposer(): View {
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.BOTTOM
        bar.setPadding(dip(12), dip(8), dip(12), dip(10))
        bar.setBackgroundColor(KestrelTheme.BG)

        val field = EditText(context)
        field.hint = "message kestrel…"
        field.setHintTextColor(KestrelTheme.DIM)
        field.setTextColor(KestrelTheme.TEXT)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        field.background = fieldBackground()
        field.setPadding(dip(14), dip(11), dip(14), dip(11))
        field.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        field.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        field.maxLines = 5
        field.isVerticalScrollBarEnabled = false
        field.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN)
            ) {
                submit()
                true
            } else {
                false
            }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (streamingMessage == null) {
                    sendButton.setTextColor(if (s.isNullOrBlank()) KestrelTheme.DIM else KestrelTheme.ACCENT)
                }
            }
        })
        bar.addView(field, linear(0, ViewGroup.LayoutParams.WRAP_CONTENT).weight(1f))
        input = field

        sendButton = TextView(context)
        sendButton.text = "▶"
        sendButton.typeface = KestrelTheme.MONO
        sendButton textSizeSp 20f
        sendButton.setTextColor(KestrelTheme.DIM)
        sendButton.gravity = Gravity.CENTER
        sendButton.setPadding(dip(14), dip(8), dip(6), dip(8))
        sendButton.setOnClickListener { submit() }
        bar.addView(sendButton, linear(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).margin(dip(4), 0, 0, dip(2)))

        val rule = View(context)
        rule.setBackgroundColor(KestrelTheme.HAIRLINE)
        val wrap = LinearLayout(context)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.addView(rule, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dip(1))))
        wrap.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return wrap
    }

    private fun fieldBackground(): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dip(14).toFloat()
        drawable.setColor(KestrelTheme.SURFACE)
        drawable.setStroke(Math.max(1, dip(1)), KestrelTheme.HAIRLINE)
        return drawable
    }

    // -- boot / error / onboarding overlays ---------------------------------

    private fun buildBootOverlay(): LinearLayout {
        val overlay = LinearLayout(context)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.gravity = Gravity.CENTER
        overlay.setBackgroundColor(KestrelTheme.BG)
        overlay.setPadding(dip(32), 0, dip(32), 0)

        val wordmark = TextView(context)
        wordmark.text = "KESTREL"
        wordmark.typeface = KestrelTheme.MONO
        wordmark textSizeSp 26f
        wordmark.setLetterSpacing(0.22f)
        wordmark.setTextColor(KestrelTheme.TEXT)
        overlay.addView(wordmark)

        val cursor = TextView(context)
        cursor.text = "▌"
        cursor.typeface = KestrelTheme.MONO
        cursor textSizeSp 26f
        cursor.setTextColor(KestrelTheme.ACCENT)
        cursor.gravity = Gravity.CENTER_HORIZONTAL
        overlay.addView(cursor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        startCursorBlink(cursor)

        bootStatus = TextView(context)
        bootStatus.text = "waking the engine…"
        bootStatus.typeface = KestrelTheme.MONO
        bootStatus textSizeSp 12f
        bootStatus.setTextColor(KestrelTheme.MUTED)
        overlay.addView(bootStatus, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(28)))
        return overlay
    }

    private fun buildErrorOverlay(): LinearLayout {
        val overlay = LinearLayout(context)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.gravity = Gravity.CENTER
        overlay.setBackgroundColor(KestrelTheme.BG)
        overlay.setPadding(dip(32), 0, dip(32), 0)

        val mark = TextView(context)
        mark.text = "✕"
        mark.typeface = KestrelTheme.MONO
        mark textSizeSp 24f
        mark.setTextColor(KestrelTheme.RED)
        overlay.addView(mark)

        val title = TextView(context)
        title.text = "engine did not start"
        title.typeface = KestrelTheme.MONO
        title textSizeSp 14f
        title.setTextColor(KestrelTheme.TEXT)
        overlay.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(16)))

        errorText = TextView(context)
        errorText.typeface = KestrelTheme.MONO
        errorText textSizeSp 11f
        errorText.setTextColor(KestrelTheme.MUTED)
        errorText.gravity = Gravity.CENTER
        overlay.addView(errorText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(12)))

        val retry = TextView(context)
        retry.text = "RETRY"
        retry.typeface = KestrelTheme.MONO
        retry textSizeSp 13f
        retry.setTextColor(KestrelTheme.ACCENT)
        retry.setPadding(dip(28), dip(12), dip(28), dip(12))
        retry.background = pillBackground(KestrelTheme.ACCENT_DIM)
        retry.setOnClickListener { retryBoot() }
        overlay.addView(retry, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(28)))
        return overlay
    }

    private fun buildOnboarding(): LinearLayout {
        val overlay = LinearLayout(context)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.setBackgroundColor(KestrelTheme.BG)
        overlay.setPadding(dip(24), dip(28), dip(24), dip(24))
        overlay.gravity = Gravity.CENTER

        val title = TextView(context)
        title.text = "grant access"
        title.typeface = KestrelTheme.MONO
        title textSizeSp 16f
        title.setTextColor(KestrelTheme.TEXT)
        overlay.addView(title)

        val hint = TextView(context)
        hint.text = "Kestrel works on files and notifies you when a task finishes."
        hint typeSp 13f
        hint.setTextColor(KestrelTheme.MUTED)
        hint.setLineSpacing(dip(2).toFloat(), 1.1f)
        overlay.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(10)))

        for (row in arrayOf(
            Triple("storage", "all files", "read and write the files it works on"),
            Triple("notifications", "notifications", "task finished / alerts"),
            Triple("microphone", "microphone", "voice input"),
        )) {
            overlay.addView(permissionRow(row.first, row.second, row.third),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(18)))
        }

        val proceed = TextView(context)
        proceed.text = "CONTINUE"
        proceed.typeface = KestrelTheme.MONO
        proceed textSizeSp 13f
        proceed.setTextColor(KestrelTheme.ACCENT)
        proceed.gravity = Gravity.CENTER
        proceed.setPadding(dip(28), dip(12), dip(28), dip(12))
        proceed.background = pillBackground(KestrelTheme.ACCENT_DIM)
        proceed.setOnClickListener {
            markOnboarded()
            onboardingOverlay.visibility = GONE
        }
        overlay.addView(proceed, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(30)))
        return overlay
    }

    private fun permissionRow(type: String, name: String, why: String): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.VERTICAL
        row.background = panelBackground()
        row.setPadding(dip(16), dip(14), dip(16), dip(14))

        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val label = TextView(context)
        label.text = name
        label typeSp 14f
        label.setTextColor(KestrelTheme.TEXT)
        head.addView(label, linear(0, 0).weight(1f))
        val grant = TextView(context)
        grant.text = "grant"
        grant.typeface = KestrelTheme.MONO
        grant textSizeSp 12f
        grant.setTextColor(KestrelTheme.ACCENT)
        grant.setPadding(dip(10), dip(4), dip(10), dip(4))
        grant.setOnClickListener { HibouBridge.requestPermission(context, type) }
        head.addView(grant)
        row.addView(head)

        val detail = TextView(context)
        detail.text = why
        detail typeSp 12f
        detail.setTextColor(KestrelTheme.DIM)
        row.addView(detail, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(3)))
        return row
    }

    // -- boot -----------------------------------------------------------------

    /** Called by the activity after the core service has been requested. */
    fun beginBoot() {
        if (booted || bootThread?.isAlive == true) return
        bootOverlay.visibility = VISIBLE
        errorOverlay.visibility = GONE
        bootThread = Thread({
            val deadline = System.currentTimeMillis() + 180_000L
            while (System.currentTimeMillis() < deadline) {
                if (CoreRuntimeState.serverUrl != null) {
                    post {
                        bootOverlay.visibility = GONE
                        booted = true
                        refreshStatus()
                        maybeShowOnboarding()
                    }
                    return@Thread
                }
                val stage = when {
                    !EnvInstaller.isInstalled(context) -> "installing environment…"
                    CoreRuntimeState.restarting -> "restarting engine…"
                    else -> "starting engine…"
                }
                post { bootStatus.text = stage }
                try {
                    Thread.sleep(500L)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            post {
                bootOverlay.visibility = GONE
                errorOverlay.visibility = VISIBLE
                errorText.text = CoreRuntimeState.lastError ?: "the engine never reported ready"
            }
        }, "kestrel-boot")
        bootThread?.isDaemon = true
        bootThread?.start()
    }

    private fun retryBoot() {
        com.agy.core.CoreServiceLauncher.start(context)
        beginBoot()
    }

    private fun maybeShowOnboarding() {
        if (isOnboarded()) {
            onboardingOverlay.visibility = GONE
            return
        }
        val state = try {
            JSONObject(HibouBridge.checkPermissions(context))
        } catch (_: Throwable) {
            null
        }
        val missing = state != null && (
            !state.optBoolean("storage", true) ||
                !state.optBoolean("notifications", true) ||
                !state.optBoolean("microphone", true)
            )
        onboardingOverlay.visibility = if (missing) VISIBLE else GONE
    }

    /** Re-checks permissions when the activity resumes (e.g. after settings). */
    fun refreshPermissions() {
        if (booted) maybeShowOnboarding()
    }

    private fun onboardingFile() =
        java.io.File(context.filesDir, ".kestrel_onboarded")

    private fun isOnboarded(): Boolean = onboardingFile().isFile

    private fun markOnboarded() {
        try {
            onboardingFile().writeText("1")
        } catch (_: Throwable) {
        }
    }

    // -- streaming engine ---------------------------------------------------

    private fun submit() {
        if (streamingMessage != null) {
            stopStreaming()
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")

        store.append("user", text)
        val assistant = store.append("assistant", "")
        streamingMessage = assistant
        liveText.setLength(0)
        render()

        if (!client.isReady()) {
            postDelayed({
                store.update(assistant, "The engine is not reachable yet — wait for the ● ready status, then send again.")
                streamingMessage = null
                render()
                refreshStatus()
            }, 400)
            return
        }

        setStreaming(true)
        val conversation = store.active()
        val existing = conversation.cascadeId
        Thread({
            val cascade = existing ?: client.newCascadeId()
            try {
                if (existing == null) {
                    client.startCascade(cascade, requestedModel = conversation.model)
                    post {
                        conversation.cascadeId = cascade
                        store.save()
                    }
                }
                client.sendMessage(cascade, text)
                stream = client.streamUpdates(
                    cascade,
                    onUpdate = { update -> applyUpdate(update) },
                    onDone = { error -> finishStreaming(error) },
                )
            } catch (error: Throwable) {
                finishStreaming(error)
            }
        }, "kestrel-send").start()
    }

    private fun applyUpdate(update: JSONObject) {
        val steps = client.stepsFromUpdate(update)
        if (steps.isEmpty() && !update.optBoolean("fullyIdle", false)) return
        post {
            val message = streamingMessage ?: return@post
            val row = messageViews[message.id]
            for (step in steps) {
                if (step.text.isNotBlank()) {
                    liveText.setLength(0)
                    liveText.append(step.text)
                }
                val tool = step.tool
                if (tool != null && !message.tools.contains(tool)) {
                    message.tools.add(tool)
                    row?.let { addToolLine(it, tool) }
                }
                if (step.thinking.isNotBlank()) {
                    message.thinking = step.thinking
                    row?.let { it.thinkingContainer.text = step.thinking.trim() }
                }
            }
            if (liveText.isNotEmpty()) {
                message.text = liveText.toString()
                row?.body?.text = HibouMarkdown.render(message.text)
            }
            scrollIfAtBottom()
        }
    }

    private fun finishStreaming(error: Throwable?) {
        post {
            val message = streamingMessage
            if (message != null) {
                if (error != null && message.text.isBlank()) {
                    message.text = "Stream ended: " + (error.message ?: error.javaClass.simpleName)
                } else if (message.text.isBlank()) {
                    message.text = "No response was produced."
                }
                store.save()
                render()
            }
            streamingMessage = null
            stream = null
            setStreaming(false)
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
                render()
            }
            streamingMessage = null
            setStreaming(false)
            refreshStatus()
        }
    }

    private fun setStreaming(active: Boolean) {
        if (active) {
            sendButton.text = "■"
            sendButton.setTextColor(KestrelTheme.RED)
        } else {
            sendButton.text = "▶"
            sendButton.setTextColor(if (input.text.isNullOrBlank()) KestrelTheme.DIM else KestrelTheme.ACCENT)
        }
        refreshStatus()
    }

    fun refreshStatus() {
        if (!booted) {
            statusDot.setTextColor(KestrelTheme.AMBER)
            statusLabel.text = "booting"
            return
        }
        val ready = CoreRuntimeState.serverUrl != null
        when {
            streamingMessage != null -> {
                statusDot.setTextColor(KestrelTheme.ACCENT)
                statusLabel.text = "streaming"
            }
            ready -> {
                statusDot.setTextColor(KestrelTheme.ACCENT)
                statusLabel.text = "ready"
            }
            CoreRuntimeState.restarting -> {
                statusDot.setTextColor(KestrelTheme.AMBER)
                statusLabel.text = "restarting"
            }
            else -> {
                statusDot.setTextColor(KestrelTheme.RED)
                statusLabel.text = "offline"
            }
        }
    }

    // -- transcript -----------------------------------------------------------

    private fun render() {
        transcript.removeAllViews()
        messageViews.clear()
        val conversation = store.active()
        if (conversation.messages.isEmpty()) {
            transcript.addView(buildEmptyState())
            return
        }
        for (message in conversation.messages) {
            transcript.addView(buildMessage(message), linear(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).top(dip(6)))
        }
        transcriptScroll.post {
            transcriptScroll.fullScroll(View.FOCUS_DOWN)
            atBottom = true
        }
    }

    private fun buildEmptyState(): View {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dip(8), dip(28), dip(8), dip(8))
        val hello = TextView(context)
        hello.text = "kestrel ▸ ready."
        hello.typeface = KestrelTheme.MONO
        hello textSizeSp 14f
        hello.setTextColor(KestrelTheme.TEXT)
        box.addView(hello)
        val hint = TextView(context)
        hint.text = "Send a task. The engine runs it in a sandboxed environment " +
            "and reports back here — file edits, shell output and all."
        hint typeSp 13f
        hint.setTextColor(KestrelTheme.DIM)
        hint.setLineSpacing(dip(2).toFloat(), 1.15f)
        box.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(10)))
        return box
    }

    private fun buildMessage(message: HibouChatMessage): View {
        val block = LinearLayout(context)
        block.orientation = LinearLayout.VERTICAL
        block.setPadding(dip(12), dip(10), dip(12), dip(10))
        block.background = panelBackground()

        val head = TextView(context)
        head.typeface = KestrelTheme.MONO
        head textSizeSp 10f
        head.setLetterSpacing(0.08f)
        if (message.role == "user") {
            head.text = "YOU ▸"
            head.setTextColor(KestrelTheme.ACCENT)
        } else {
            head.text = "KESTREL ▸"
            head.setTextColor(KestrelTheme.MUTED)
        }
        block.addView(head)

        val tools = LinearLayout(context)
        tools.orientation = LinearLayout.VERTICAL
        block.addView(tools, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(6)))

        val thinking = TextView(context)
        thinking.typeface = KestrelTheme.MONO
        thinking textSizeSp 10f
        thinking.setTextColor(KestrelTheme.DIM)
        thinking.setLineSpacing(dip(2).toFloat(), 1.1f)
        thinking.visibility = GONE
        block.addView(thinking, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(6)))

        val body = TextView(context)
        body typeSp 15f
        body.setTextColor(KestrelTheme.TEXT)
        body.setLineSpacing(dip(2).toFloat(), 1.15f)
        body.setTextIsSelectable(true)
        block.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(6)))

        messageViews[message.id] = MessageRow(tools, thinking, body)
        for (tool in message.tools) addToolLine(messageViews[message.id]!!, tool)
        if (message.thinking.isNotBlank()) {
            thinking.text = message.thinking.trim()
        }
        body.text = if (message.text.isBlank() && message === streamingMessage) "▌"
        else HibouMarkdown.render(message.text)
        return block
    }

    private fun addToolLine(row: MessageRow, tool: String) {
        val line = TextView(context)
        line.typeface = KestrelTheme.MONO
        line textSizeSp 10f
        line.setTextColor(KestrelTheme.DIM)
        line.text = "· " + tool
        line.ellipsize = android.text.TextUtils.TruncateAt.END
        row.toolsContainer.addView(line, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun scrollIfAtBottom() {
        if (atBottom) transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // -- history sheet -------------------------------------------------------

    private fun openHistory() {
        val activity = context as? Activity ?: return
        val sheet = LinearLayout(context)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = panelBackground()
        sheet.setPadding(dip(20), dip(18), dip(20), dip(20))

        val title = TextView(context)
        title.text = "CONVERSATIONS"
        title.typeface = KestrelTheme.MONO
        title textSizeSp 11f
        title.setLetterSpacing(0.12f)
        title.setTextColor(KestrelTheme.MUTED)
        sheet.addView(title)

        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL

        val scroll = ScrollView(context)
        scroll.addView(list)
        sheet.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dip(320)).top(dip(10)))

        var added = 0
        for (conversation in store.conversations) {
            list.addView(historyRow(conversation.id, conversation.title,
                conversation.cascadeId != null, conversation.updatedAt) {
                store.select(conversation.id)
                render()
                dialog?.dismiss()
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(2)))
            added++
        }
        if (added == 0) {
            val empty = TextView(context)
            empty.text = "no conversations yet"
            empty.typeface = KestrelTheme.MONO
            empty textSizeSp 11f
            empty.setTextColor(KestrelTheme.DIM)
            list.addView(empty)
        }

        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.END
        val settings = TextView(context)
        settings.text = "SETTINGS"
        settings.typeface = KestrelTheme.MONO
        settings textSizeSp 12f
        settings.setTextColor(KestrelTheme.ACCENT)
        settings.setPadding(dip(14), dip(10), dip(14), dip(10))
        settings.setOnClickListener {
            dialog?.dismiss()
            com.agy.ru.HibouControlCenterDialog.show(activity)
        }
        val permissions = TextView(context)
        permissions.text = "PERMISSIONS"
        permissions.typeface = KestrelTheme.MONO
        permissions textSizeSp 12f
        permissions.setTextColor(KestrelTheme.ACCENT)
        permissions.setPadding(dip(14), dip(10), dip(14), dip(10))
        permissions.setOnClickListener {
            dialog?.dismiss()
            markOnboardedCleared()
            maybeShowOnboarding()
        }
        val close = TextView(context)
        close.text = "CLOSE"
        close.typeface = KestrelTheme.MONO
        close textSizeSp 12f
        close.setTextColor(KestrelTheme.MUTED)
        close.setPadding(dip(14), dip(10), dip(14), dip(10))
        close.setOnClickListener { dialog?.dismiss() }
        actions.addView(permissions)
        actions.addView(settings)
        actions.addView(close)
        sheet.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(12)))

        dialog = Dialog(activity)
        dialog?.setContentView(sheet)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog?.show()
    }

    private fun historyRow(
        id: String,
        title: String,
        running: Boolean,
        updatedAt: Long,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dip(12), dip(10), dip(12), dip(10))
        row.background = panelBackground()
        row.setOnClickListener { onClick() }
        val name = TextView(context)
        name.text = title
        name typeSp 14f
        name.setTextColor(KestrelTheme.TEXT)
        row.addView(name)
        val meta = TextView(context)
        meta.typeface = KestrelTheme.MONO
        meta textSizeSp 10f
        meta.setTextColor(if (running) KestrelTheme.ACCENT else KestrelTheme.DIM)
        meta.text = (if (running) "● running · " else "") +
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(updatedAt))
        row.addView(meta, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).top(dip(2)))
        return row
    }

    private var dialog: Dialog? = null

    private fun markOnboardedCleared() {
        try {
            onboardingFile().delete()
        } catch (_: Throwable) {
        }
    }

    private fun newChat() {
        if (streamingMessage != null) stopStreaming()
        store.newConversation()
        render()
        input.requestFocus()
    }

    // -- chrome ---------------------------------------------------------------

    private fun iconButton(glyph: String): TextView {
        val button = TextView(context)
        button.text = glyph
        button.typeface = KestrelTheme.MONO
        button textSizeSp 18f
        button.setTextColor(KestrelTheme.MUTED)
        button.gravity = Gravity.CENTER
        button.setPadding(dip(8), dip(6), dip(8), dip(6))
        val ripple = TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        if (ripple.resourceId != 0) {
            button.setBackgroundResource(ripple.resourceId)
        }
        return button
    }

    private fun panelBackground(): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dip(10).toFloat()
        drawable.setColor(KestrelTheme.SURFACE)
        return drawable
    }

    private fun pillBackground(strokeColor: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dip(20).toFloat()
        drawable.setColor(Color.TRANSPARENT)
        drawable.setStroke(Math.max(1, dip(1)), strokeColor)
        return drawable
    }

    private fun startCursorBlink(cursor: TextView) {
        if (cursorBlinking) return
        cursorBlinking = true
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val blink = object : Runnable {
            var on = true
            override fun run() {
                cursor.visibility = if (on) View.VISIBLE else View.INVISIBLE
                on = !on
                handler.postDelayed(this, 530L)
            }
        }
        handler.post(blink)
    }

    // -- activity lifecycle hooks ---------------------------------------------

    fun handleBack(): Boolean {
        dialog?.let {
            if (it.isShowing) {
                it.dismiss()
                return true
            }
        }
        return false
    }

    fun onDestroy() {
        bootThread?.interrupt()
        stream?.cancel()
        messageViews.clear()
    }

    // -- layout micro-DSL ------------------------------------------------------

    private fun dip(value: Int): Int = KestrelTheme.dp(context, value)

    private fun linear(width: Int, height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height)

    private fun LinearLayout.LayoutParams.weight(weight: Float): LinearLayout.LayoutParams {
        this.weight = weight
        return this
    }

    private fun LinearLayout.LayoutParams.margin(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        setMargins(l, t, r, b)
        return this
    }

    private fun LinearLayout.LayoutParams.top(px: Int): LinearLayout.LayoutParams {
        topMargin = px
        return this
    }

    private infix fun TextView.textSizeSp(value: Float) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
    }

    private infix fun TextView.typeSp(value: Float) = this textSizeSp value

    private fun TextView.setLetterSpacing(spacing: Float) {
        letterSpacing = spacing
    }
}
