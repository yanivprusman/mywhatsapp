package com.automatelinux.mywhatsapp

/** iOS isn't built yet (no Mac); playback says so rather than pretending. */
actual object VoicePlayer {
    actual val current: String? = null
    actual val playing: Boolean = false
    actual val positionMs: Int = 0
    actual val durationMs: Int = 0
    actual fun play(key: String, bytes: ByteArray) { throw UnsupportedOperationException("Voice playback isn't available on iOS yet") }
    actual fun pause() {}
    actual fun resume() {}
    actual fun stop() {}
}
