package com.automatelinux.mywhatsapp.ui

// A pushed screen on top of the four home tabs.
sealed interface Screen {
    val title: String
    data object SelectContact : Screen { override val title = "Select contact" }
    data class Conversation(val chat: com.automatelinux.mywhatsapp.data.Chat) : Screen { override val title = chat.name }
    data class Empty(override val title: String, val kind: String) : Screen
}
