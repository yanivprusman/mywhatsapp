package com.automatelinux.mywhatsapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** The desktop's Back is Escape: the window hands it here and the innermost enabled handler takes it. */
object DesktopBack {
    private class Entry(val enabled: () -> Boolean, val onBack: () -> Unit)
    private val entries = mutableListOf<Entry>()

    /** True when a screen consumed it. */
    fun back(): Boolean {
        val e = entries.lastOrNull { it.enabled() } ?: return false
        e.onBack()
        return true
    }

    @Composable
    internal fun Register(enabled: Boolean, onBack: () -> Unit) {
        val isOn by rememberUpdatedState(enabled)
        val action by rememberUpdatedState(onBack)
        DisposableEffect(Unit) {
            val e = Entry({ isOn }, { action() })
            entries.add(e)
            onDispose { entries.remove(e) }
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = DesktopBack.Register(enabled, onBack)
