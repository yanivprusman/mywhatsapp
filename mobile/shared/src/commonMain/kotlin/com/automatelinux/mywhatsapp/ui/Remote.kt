package com.automatelinux.mywhatsapp.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.automatelinux.mywhatsapp.data.Api

val LocalApi = staticCompositionLocalOf<Api> { error("Api not provided") }

/** Decoded images by message id, kept for the life of the process. */
class ImageCache {
    private val map = LinkedHashMap<String, ImageBitmap>()
    operator fun get(id: String): ImageBitmap? = map[id]
    operator fun set(id: String, img: ImageBitmap) {
        map[id] = img
        if (map.size > 60) map.remove(map.keys.first())
    }
}

val LocalImages = staticCompositionLocalOf { ImageCache() }
