package com.agy.ru

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan

/**
 * Small Markdown/GFM renderer for assistant messages.
 *
 * The web UI uses a full CommonMark pipeline; the native transcript only needs
 * the subset the agent actually emits: headings, emphasis, inline code, fenced
 * code, bullet lists and links.  Everything is expressed with platform spans so
 * no HTML view or extra dependency is required.
 */
object HibouMarkdown {

    fun render(text: String): CharSequence {
        val out = SpannableStringBuilder()
        var fenced = false
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw
            if (line.trimStart().startsWith("```")) {
                fenced = !fenced
                continue
            }
            if (fenced) {
                appendCode(out, line)
                out.append('\n')
                continue
            }
            appendLine(out, line)
            out.append('\n')
        }
        if (out.length > 0) out.delete(out.length - 1, out.length)
        return out
    }

    private fun appendCode(out: SpannableStringBuilder, line: String) {
        val start = out.length
        out.append(line)
        val end = out.length
        if (end > start) {
            out.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(BackgroundColorSpan(HibouTheme.color(HibouTheme.SURFACE_HIGH)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(HibouTheme.color(HibouTheme.ACCENT)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun appendLine(out: SpannableStringBuilder, line: String) {
        var body = line
        var heading = 0
        while (heading < body.length && body[heading] == '#') heading++
        if (heading in 1..4 && body.length > heading && body[heading] == ' ') {
            body = body.substring(heading + 1)
        } else {
            heading = 0
        }

        var bullet = false
        if (body.startsWith("- ") || body.startsWith("* ") || body.startsWith("+ ")) {
            bullet = true
            body = "\u2022 " + body.substring(2)
        }

        val start = out.length
        appendInline(out, body)
        val end = out.length

        if (heading > 0 && end > start) {
            val scale = when (heading) {
                1 -> 1.45f
                2 -> 1.3f
                3 -> 1.18f
                else -> 1.08f
            }
            out.setSpan(RelativeSizeSpan(scale), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (bullet && end > start) {
            out.setSpan(ForegroundColorSpan(HibouTheme.color(HibouTheme.ACCENT)),
                start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun appendInline(out: SpannableStringBuilder, text: String) {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '`' -> {
                    val close = text.indexOf('`', index + 1)
                    if (close > index) {
                        appendSpan(out, text.substring(index + 1, close),
                            TypefaceSpan("monospace"),
                            BackgroundColorSpan(HibouTheme.color(HibouTheme.SURFACE_HIGH)))
                        index = close + 1
                    } else {
                        out.append(char)
                        index++
                    }
                }
                text.startsWith("**", index) || text.startsWith("__", index) -> {
                    val marker = text.substring(index, index + 2)
                    val close = text.indexOf(marker, index + 2)
                    if (close > index + 2) {
                        appendSpan(out, text.substring(index + 2, close),
                            StyleSpan(Typeface.BOLD))
                        index = close + 2
                    } else {
                        out.append(marker)
                        index += 2
                    }
                }
                text.startsWith("![", index) -> {
                    val close = text.indexOf(']', index + 2)
                    if (close > 0) {
                        val label = text.substring(index + 2, close)
                        appendSpan(out, "[image: " + label + "]",
                            StyleSpan(Typeface.ITALIC),
                            ForegroundColorSpan(HibouTheme.color(HibouTheme.TEXT_MUTED)))
                        val paren = text.indexOf(')', close)
                        index = if (paren > 0) paren + 1 else close + 1
                    } else {
                        out.append(char)
                        index++
                    }
                }
                char == '[' -> {
                    val close = text.indexOf(']', index + 1)
                    if (close > index && close + 1 < text.length && text[close + 1] == '(') {
                        val end = text.indexOf(')', close + 2)
                        if (end > close) {
                            appendSpan(out, text.substring(index + 1, close),
                                ForegroundColorSpan(HibouTheme.color(HibouTheme.ACCENT)),
                                UnderlineSpan())
                            index = end + 1
                        } else {
                            out.append(char)
                            index++
                        }
                    } else {
                        out.append(char)
                        index++
                    }
                }
                char == '*' || char == '_' -> {
                    val close = text.indexOf(char, index + 1)
                    if (close > index + 1) {
                        appendSpan(out, text.substring(index + 1, close),
                            StyleSpan(Typeface.ITALIC))
                        index = close + 1
                    } else {
                        out.append(char)
                        index++
                    }
                }
                else -> {
                    out.append(char)
                    index++
                }
            }
        }
    }

    private fun appendSpan(out: SpannableStringBuilder, value: String, vararg spans: Any) {
        val start = out.length
        out.append(value)
        val end = out.length
        if (end <= start) return
        for (span in spans) {
            out.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
