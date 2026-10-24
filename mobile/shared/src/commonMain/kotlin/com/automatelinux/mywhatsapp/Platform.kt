package com.automatelinux.mywhatsapp

import androidx.compose.ui.graphics.ImageBitmap

/** HTTP on a background thread; throws on any non-2xx with the server's message. */
expect suspend fun httpRequest(method: String, url: String, token: String, jsonBody: String?): ByteArray

/** Decodes an image, downsampled so its longer side is about [maxPx]. Null if undecodable. */
expect fun decodeImage(bytes: ByteArray, maxPx: Int): ImageBitmap?
