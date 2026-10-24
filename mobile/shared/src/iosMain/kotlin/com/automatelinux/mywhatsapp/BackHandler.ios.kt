package com.automatelinux.mywhatsapp

import androidx.compose.runtime.Composable

// iOS has no system back button; navigation is the top bar's back arrow.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
