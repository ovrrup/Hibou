package com.agy.ru

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class HibouChatMessage(
    val id: String,
    val role: String,
    var text: String,
    val at: Long,
    val tools: MutableList<String> = mutableListOf(),
    var thinking: String = ""
)

class HibouConversation(
    val id: String,
    var title: String,
    var updatedAt: Long,
    val messages: MutableList<HibouChatMessage>,
    var cascadeId: String? = null,
    var model: String? = null
)

/**
 * Local conversation cache for the native shell.
 *
 * The core stays authoritative: [cascadeId] points at the trajectory that
 * `GetCascadeTrajectory` and `StreamAgentStateUpdates` read.  This file only
 * keeps drafts, titles and the last rendered transcript so a cold start shows
 * something before the core answers.
 */
class HibouChatStore(ctx: Context) {
    private val file = File(ctx.filesDir ?: File("/data/data/agy.hibou/files"), "hibou_native_chat.json")
    val conversations = mutableListOf<HibouConversation>()
    var activeId: String? = null
        private set

    init {
        load()
    }

    fun active(): HibouConversation {
        val current = activeId?.let { id -> conversations.firstOrNull { it.id == id } }
        if (current != null) return current
        if (conversations.isNotEmpty()) {
            activeId = conversations.first().id
            return conversations.first()
        }
        return newConversation()
    }

    fun newConversation(): HibouConversation {
        val conversation = HibouConversation(
            UUID.randomUUID().toString(),
            "New chat",
            System.currentTimeMillis(),
            mutableListOf()
        )
        conversations.add(0, conversation)
        activeId = conversation.id
        save()
        return conversation
    }

    fun select(id: String): HibouConversation? {
        val conversation = conversations.firstOrNull { it.id == id } ?: return null
        activeId = id
        return conversation
    }

    fun findByCascade(cascadeId: String): HibouConversation? {
        return conversations.firstOrNull { it.cascadeId == cascadeId }
    }

    fun append(role: String, text: String): HibouChatMessage {
        val conversation = active()
        val message = HibouChatMessage(
            UUID.randomUUID().toString(),
            role,
            text,
            System.currentTimeMillis()
        )
        conversation.messages.add(message)
        conversation.updatedAt = message.at
        if (role == "user" && (conversation.title == "New chat" || conversation.title.isBlank())) {
            conversation.title = text.trim().replace("\n", " ").take(48)
        }
        save()
        return message
    }

    fun update(message: HibouChatMessage, text: String) {
        message.text = text
        val conversation = active()
        conversation.updatedAt = System.currentTimeMillis()
        save()
    }

    fun addTool(message: HibouChatMessage, tool: String) {
        if (message.tools.contains(tool)) return
        message.tools.add(tool)
        save()
    }

    fun save() {
        try {
            val root = JSONObject()
            root.put("version", 3)
            root.put("activeId", activeId ?: "")
            val array = JSONArray()
            for (conversation in conversations) {
                val item = JSONObject()
                item.put("id", conversation.id)
                item.put("title", conversation.title)
                item.put("updatedAt", conversation.updatedAt)
                item.put("cascadeId", conversation.cascadeId ?: "")
                item.put("model", conversation.model ?: "")
                val messages = JSONArray()
                for (message in conversation.messages) {
                    val record = JSONObject()
                    record.put("id", message.id)
                    record.put("role", message.role)
                    record.put("text", message.text)
                    record.put("at", message.at)
                    record.put("thinking", message.thinking)
                    val tools = JSONArray()
                    for (tool in message.tools) tools.put(tool)
                    record.put("tools", tools)
                    messages.put(record)
                }
                item.put("messages", messages)
                array.put(item)
            }
            root.put("conversations", array)
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("conversations") ?: return
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val messages = mutableListOf<HibouChatMessage>()
                val records = item.optJSONArray("messages")
                if (records != null) {
                    for (messageIndex in 0 until records.length()) {
                        val record = records.optJSONObject(messageIndex) ?: continue
                        val tools = mutableListOf<String>()
                        val toolRecords = record.optJSONArray("tools")
                        if (toolRecords != null) {
                            for (toolIndex in 0 until toolRecords.length()) {
                                val tool = toolRecords.optString(toolIndex, "")
                                if (tool.isNotBlank()) tools.add(tool)
                            }
                        }
                        messages.add(HibouChatMessage(
                            record.optString("id", UUID.randomUUID().toString()),
                            record.optString("role", "assistant"),
                            record.optString("text", ""),
                            record.optLong("at", 0L),
                            tools,
                            record.optString("thinking", "")
                        ))
                    }
                }
                val cascade = item.optString("cascadeId", "")
                val model = item.optString("model", "")
                conversations.add(HibouConversation(
                    item.optString("id", UUID.randomUUID().toString()),
                    item.optString("title", "New chat"),
                    item.optLong("updatedAt", 0L),
                    messages,
                    cascade.ifBlank { null },
                    model.ifBlank { null }
                ))
            }
            val saved = root.optString("activeId", "")
            activeId = saved.ifBlank { conversations.firstOrNull()?.id }
        } catch (_: Exception) {
            conversations.clear()
            activeId = null
        }
    }
}
