package com.automatelinux.mywhatsapp

import androidx.compose.ui.graphics.ImageBitmap

actual suspend fun httpRequest(method: String, url: String, token: String, jsonBody: String?): ByteArray =
    TODO("iOS networking: add when the iOS target is enabled")

actual fun decodeImage(bytes: ByteArray, maxPx: Int): ImageBitmap? = null
