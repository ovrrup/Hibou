package com.agy.log

import org.json.JSONArray
import java.util.LinkedList

/**
 * Bounded history of [CommandRecord]s for the gateway command-log endpoint.
 *
 * Ported 1:1 from decompiled `com/agy/log/CommandHistory.java`.
 */
class CommandHistory {
    private val history = LinkedList<CommandRecord>()
    private val capacity: Int

    constructor() : this(DEFAULT_CAPACITY)

    constructor(capacity: Int) {
        this.capacity = maxOf(10, capacity)
    }

    @Synchronized
    fun record(record: CommandRecord?) {
        if (record == null) return
        if (history.size >= capacity) {
            history.removeFirst()
        }
        history.addLast(record)
    }

    @Synchronized
    fun getRecent(count: Int): List<CommandRecord> {
        val recent = minOf(maxOf(1, count), history.size)
        return history.toList().takeLast(recent)
    }

    @Synchronized
    fun getById(id: String?): CommandRecord? {
        if (id == null) return null
        for (record in history) {
            if (id == record.id) return record
        }
        return null
    }

    companion object {
        const val DEFAULT_CAPACITY = 100

        @JvmStatic
        fun toJsonArray(records: List<CommandRecord>?): JSONArray {
            val array = JSONArray()
            if (records != null) {
                for (record in records) {
                    array.put(record.toJson())
                }
            }
            return array
        }
    }
}
