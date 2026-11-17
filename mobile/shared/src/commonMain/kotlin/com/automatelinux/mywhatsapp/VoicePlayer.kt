package com.automatelinux.mywhatsapp

/**
 * Plays voice messages, one at a time — starting one stops whatever played before,
 * as in WhatsApp. The UI reads [current]/[playing]/[positionMs]/[durationMs] on a
 * short tick while something plays; nothing here calls back into Compose.
 */
expect object VoicePlayer {
    /** The message loaded now (its id), or null. */
    val current: String?
    val playing: Boolean
    val positionMs: Int
    val durationMs: Int

    /** Load [bytes] (WhatsApp's Ogg Opus) as message [key] and play it. Throws if it can't be played. */
    fun play(key: String, bytes: ByteArray)
    fun pause()
    fun resume()
    fun stop()
}
