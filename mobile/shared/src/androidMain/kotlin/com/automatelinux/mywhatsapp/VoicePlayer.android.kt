package com.automatelinux.mywhatsapp

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

actual object VoicePlayer {
    private var player: MediaPlayer? = null
    private var finished = false

    private var loaded: String? = null
    actual val current: String? get() = loaded
    actual val playing: Boolean get() = player?.isPlaying == true
    actual val positionMs: Int get() = player?.let { if (finished) it.duration else it.currentPosition } ?: 0
    actual val durationMs: Int get() = player?.duration?.coerceAtLeast(0) ?: 0

    actual fun play(key: String, bytes: ByteArray) {
        stop()
        // MediaPlayer reads a file or a URL, not a byte array; one file is enough since only one note plays.
        val file = File(AndroidPlatform.context.cacheDir, "voice-playing.ogg")
        file.writeBytes(bytes)
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            p.setDataSource(file.absolutePath)
            p.prepare()
        } catch (e: Exception) {
            p.release()
            throw IllegalStateException("This voice message can't be played: ${e.message}", e)
        }
        p.setOnCompletionListener { finished = true }
        player = p
        loaded = key
        finished = false
        p.start()
    }

    actual fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    actual fun resume() {
        val p = player ?: return
        if (finished) {
            p.seekTo(0)
            finished = false
        }
        p.start()
    }

    actual fun stop() {
        player?.release()
        player = null
        loaded = null
        finished = false
    }
}
