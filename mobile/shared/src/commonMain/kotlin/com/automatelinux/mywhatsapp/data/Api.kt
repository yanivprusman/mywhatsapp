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
    // Local-only: a message the phone sent that the bridge has not echoed back yet.
    val pending: Boolean = false,
    val failed: Boolean = false,
)

@Serializable
data class Chat(val jid: String, val name: String, val isGroup: Boolean, val last: Message? = null)

@Serializable private data class ChatsResponse(val chats: List<Chat>)
@Serializable data class MessagesPage(val messages: List<Message>, val hasMore: Boolean)
@Serializable private data class SendBody(val text: String)

/** The app's backend (Next.js on the dev host), which reads the leader's WhatsApp bridge. */
class Api(private val baseUrl: String, private val token: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun url(path: String) = baseUrl.trimEnd('/') + path
    private fun enc(s: String) = s.replace("@", "%40").replace("+", "%2B").replace(":", "%3A")

    init {
        require(token.isNotBlank()) { "API_TOKEN is missing — set it in mobile/.env and rebuild" }
    }

    suspend fun chats(): List<Chat> =
        json.decodeFromString<ChatsResponse>(httpRequest("GET", url("/api/chats"), token, null).decodeToString()).chats

    suspend fun messages(jid: String, before: String? = null, after: String? = null, limit: Int = 50): MessagesPage {
        var q = "/api/chats/${enc(jid)}/messages?limit=$limit"
        if (before != null) q += "&before=${enc(before)}"
        if (after != null) q += "&after=${enc(after)}"
        return json.decodeFromString(httpRequest("GET", url(q), token, null).decodeToString())
    }

    suspend fun send(jid: String, text: String) {
        httpRequest("POST", url("/api/chats/${enc(jid)}/send"), token, json.encodeToString(SendBody(text)))
    }

    suspend fun media(jid: String, id: String): ByteArray =
        httpRequest("GET", url("/api/chats/${enc(jid)}/media/${enc(id)}"), token, null)
}
