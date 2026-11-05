package ru.funpaymobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Локальное сохранение созданных на устройстве заказов, диалогов и сообщений. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_state", Context.MODE_PRIVATE)

    data class State(
        val orders: List<Order>,
        val chats: List<ChatPreview>,
        val messages: Map<String, List<Message>>
    )

    fun load(): State = try {
        val root = JSONObject(prefs.getString(KEY, null) ?: "{}")
        State(
            orders = root.optJSONArray("orders").objects().map {
                Order(
                    it.getString("id"), it.getString("title"), it.getString("buyer"), it.getString("date"),
                    it.getInt("amountRub"), OrderStatus.valueOf(it.getString("status")),
                    it.getString("description"), OrderKind.valueOf(it.getString("kind"))
                )
            },
            chats = root.optJSONArray("chats").objects().map {
                ChatPreview(it.getString("id"), it.getString("name"), it.getString("lastMessage"), it.getString("time"), it.getInt("unread"))
            },
            messages = root.optJSONObject("messages")?.let { m ->
                m.keys().asSequence().associateWith { key ->
                    m.getJSONArray(key).objects().map {
                        Message(
                            it.getLong("id"), it.getString("text"), it.getBoolean("fromMe"), it.getString("time"),
                            it.optBoolean("localOnly", false), if (it.has("author")) it.getString("author") else null
                        )
                    }
                }
            } ?: emptyMap()
        )
    } catch (e: Exception) {
        State(emptyList(), emptyList(), emptyMap())
    }

    fun save(orders: List<Order>, chats: List<ChatPreview>, messages: Map<String, List<Message>>) {
        try {
            val root = JSONObject()
            root.put("orders", JSONArray().apply {
                orders.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("buyer", it.buyer).put("date", it.date)
                        .put("amountRub", it.amountRub).put("status", it.status.name)
                        .put("description", it.description).put("kind", it.kind.name))
                }
            })
            root.put("chats", JSONArray().apply {
                chats.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("lastMessage", it.lastMessage)
                        .put("time", it.time).put("unread", it.unread))
                }
            })
            root.put("messages", JSONObject().apply {
                messages.forEach { (key, list) ->
                    put(key, JSONArray().apply {
                        list.forEach {
                            val o = JSONObject().put("id", it.id).put("text", it.text).put("fromMe", it.fromMe)
                                .put("time", it.time).put("localOnly", it.localOnly)
                            if (it.author != null) o.put("author", it.author)
                            put(o)
                        }
                    })
                }
            })
            prefs.edit().putString(KEY, root.toString()).apply()
        } catch (e: Exception) {
            // Сохранение — вспомогательная функция, сбой не должен ронять приложение.
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val KEY = "state"
    }
}
