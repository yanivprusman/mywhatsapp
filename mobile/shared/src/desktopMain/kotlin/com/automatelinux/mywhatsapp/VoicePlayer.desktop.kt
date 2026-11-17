package com.automatelinux.mywhatsapp

import java.io.File

/**
 * The JVM has no Opus decoder, so the desktop plays through ffplay (installed with
 * ffmpeg) and measures the note with ffprobe. Pause is SIGSTOP/SIGCONT on ffplay;
 * position is wall time played, which is what ffplay is doing.
 */
actual object VoicePlayer {
    private var process: Process? = null
    private var file: File? = null
    private var startedAt = 0L   // wall time when the current stretch of playing began
    private var playedMs = 0L    // played before that stretch
    private var paused = false

    actual var current: String? = null
        private set
    actual var durationMs: Int = 0
        private set
    actual val playing: Boolean get() = process?.isAlive == true && !paused
    actual val positionMs: Int
        get() {
            val p = process ?: return 0
            val now = if (p.isAlive && !paused) playedMs + (System.currentTimeMillis() - startedAt) else playedMs
            return if (!p.isAlive) durationMs else now.toInt().coerceAtMost(durationMs)
        }

    actual fun play(key: String, bytes: ByteArray) {
        stop()
        val f = File.createTempFile("mywhatsapp-voice-", ".ogg").apply { deleteOnExit(); writeBytes(bytes) }
        durationMs = probeMs(f)
        file = f
        current = key
        start(0)
    }

    private fun start(fromMs: Long) {
        val f = file ?: return
        process = ProcessBuilder("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", "-ss", "%.3f".format(fromMs / 1000.0), f.absolutePath)
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        playedMs = fromMs
        startedAt = System.currentTimeMillis()
        paused = false
    }

    private fun signal(sig: String) {
        process?.takeIf { it.isAlive }?.let { ProcessBuilder("kill", "-$sig", it.pid().toString()).start().waitFor() }
    }

    actual fun pause() {
        if (!playing) return
        signal("STOP")
        playedMs += System.currentTimeMillis() - startedAt
        paused = true
    }

    actual fun resume() {
        val p = process ?: return
        if (!p.isAlive) { start(0); return } // finished: play again from the start
        signal("CONT")
        startedAt = System.currentTimeMillis()
        paused = false
    }

    actual fun stop() {
        process?.let { if (paused) signal("CONT"); it.destroy() }
        process = null
        file?.delete()
        file = null
        current = null
        durationMs = 0
        playedMs = 0
        paused = false
    }

    private fun probeMs(f: File): Int = runCatching {
        val p = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f.absolutePath)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        (out.toDouble() * 1000).toInt()
    }.getOrElse { throw IllegalStateException("This voice message can't be played: ffprobe failed (${it.message})") }
}
