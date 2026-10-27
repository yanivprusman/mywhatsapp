package com.automatelinux.mywhatsapp.data

import com.automatelinux.mywhatsapp.httpRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Serializable
data class Message(
    val id: String,
    val text: String,
    val ts: String,
    val fromMe: Boolean,
    val sender: String,
    val mediaType: String,
    val filename: String,
    val downloadable: Boolean,
    val senderName: String? = null,
    val kind: String? = null, // "wanted" | "available" | null — set by the server's classifier
    val chatJid: String? = null,   // the real group it came from
    val chatName: String? = null,  // merged chats: that group's name
    val repeats: Int? = null,      // merged chats: times this person posted this text
    val alsoIn: List<String>? = null,
    // Local-only: a message the phone sent that the bridge has not echoed back yet.
    val pending: Boolean = false,
    val failed: Boolean = false,
)

@Serializable
data class Chat(
    val jid: String,
    val name: String,
    val isGroup: Boolean,
    val last: Message? = null,
    val unread: Int = 0,
    val merged: Boolean = false,
    val canSend: Boolean = false,
    val sources: List<Source> = emptyList(),
    val clearedAt: String? = null, // the chat was cleared up to here; older messages are hidden, not deleted
    val filter: String = "all",    // the filter it is open on; last + unread are of matching messages only
)

@Serializable data class Source(val jid: String, val name: String)

@Serializable data class WantedPost(val chatJid: String, val chatName: String, val message: Message)
@Serializable data class WantedResponse(val posts: List<WantedPost>, val latest: String)

@Serializable private data class ChatsResponse(val chats: List<Chat>)
@Serializable data class MessagesPage(
    val messages: List<Message>,
    val hasMore: Boolean,
    val counts: Map<String, Int>? = null, // first page only: matches per filter over the whole chat
    val clearedAt: String? = null,        // set when the chat was cleared: the page starts after it
)
@Serializable private data class SendBody(val text: String)
@Serializable private data class ClearBody(val upTo: String?)
@Serializable private data class StateBody(val filter: String? = null, val seen: String? = null)

/** The app's backend (Next.js on the dev host), which reads the leader's WhatsApp bridge. */
class Api(private val baseUrl: String, private val token: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun url(path: String) = baseUrl.trimEnd('/') + path
    private fun enc(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") append(c)
            else append('%').append((b.toInt() and 0xff).toString(16).uppercase().padStart(2, '0'))
        }
    }

    init {
        require(token.isNotBlank()) { "API_TOKEN is missing — set it in mobile/.env and rebuild" }
    }

    /** Each chat with its filter and unread count — both kept on the server, so every device shows the same. */
    suspend fun chats(): List<Chat> =
        json.decodeFromString<ChatsResponse>(httpRequest("GET", url("/api/chats"), token, null).decodeToString()).chats

    /** Open [jid] on [filter] from now on, on every device. */
    suspend fun setFilter(jid: String, filter: String) {
        httpRequest("PUT", url("/api/chats/${enc(jid)}/state"), token, json.encodeToString(StateBody(filter = filter)))
    }

    /** [ts] is the newest message shown in [jid] — unread counts after it, on every device. */
    suspend fun markSeen(jid: String, ts: String) {
        httpRequest("PUT", url("/api/chats/${enc(jid)}/state"), token, json.encodeToString(StateBody(seen = ts)))
    }

    /** New Wanted posts after [after]; with null, only `latest` (where a fresh watcher starts). */
    suspend fun wanted(after: String?): WantedResponse {
        val q = "/api/wanted" + (if (after != null) "?after=${enc(after)}" else "")
        return json.decodeFromString(httpRequest("GET", url(q), token, null).decodeToString())
    }

    suspend fun messages(jid: String, filter: String = "all", before: String? = null, after: String? = null, limit: Int = 50): MessagesPage {
        var q = "/api/chats/${enc(jid)}/messages?limit=$limit&filter=$filter"
        if (before != null) q += "&before=${enc(before)}"
        if (after != null) q += "&after=${enc(after)}"
        return json.decodeFromString(httpRequest("GET", url(q), token, null).decodeToString())
    }

    suspend fun send(jid: String, text: String) {
        httpRequest("POST", url("/api/chats/${enc(jid)}/send"), token, json.encodeToString(SendBody(text)))
    }

    /** Hide everything up to [upTo] (null = the newest stored message). Nothing is deleted in WhatsApp. */
    suspend fun clear(jid: String, upTo: String?) {
        httpRequest("POST", url("/api/chats/${enc(jid)}/clear"), token, json.encodeToString(ClearBody(upTo)))
    }

    /** Undo [clear]: the whole history shows again. */
    suspend fun restoreHistory(jid: String) {
        httpRequest("DELETE", url("/api/chats/${enc(jid)}/clear"), token, null)
    }

    suspend fun media(jid: String, id: String): ByteArray =
        httpRequest("GET", url("/api/chats/${enc(jid)}/media/${enc(id)}"), token, null)
}
