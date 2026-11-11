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
    val at: Long
)

class HibouConversation(
    val id: String,
    var title: String,
    var updatedAt: Long,
    val messages: MutableList<HibouChatMessage>
)

/**
 * Local conversation store for the native shell.
 *
 * The format is intentionally small and versioned so it can be migrated to the
 * core trajectory store in M2 without losing local drafts.  The web layer keeps
 * its own history until the protocol client takes over.
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

    fun save() {
        try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("activeId", activeId ?: "")
            val array = JSONArray()
            for (conversation in conversations) {
                val item = JSONObject()
                item.put("id", conversation.id)
                item.put("title", conversation.title)
                item.put("updatedAt", conversation.updatedAt)
                val messages = JSONArray()
                for (message in conversation.messages) {
                    val record = JSONObject()
                    record.put("id", message.id)
                    record.put("role", message.role)
                    record.put("text", message.text)
                    record.put("at", message.at)
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
                        messages.add(HibouChatMessage(
                            record.optString("id", UUID.randomUUID().toString()),
                            record.optString("role", "assistant"),
                            record.optString("text", ""),
                            record.optLong("at", 0L)
                        ))
                    }
                }
                conversations.add(HibouConversation(
                    item.optString("id", UUID.randomUUID().toString()),
                    item.optString("title", "New chat"),
                    item.optLong("updatedAt", 0L),
                    messages
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
