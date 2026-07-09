package com.example.adhocondor.ui.chat

import android.net.Uri

data class ChatMessage(
    val userId: String,
    val nickname: String,
    val message: String,
    val time: String,
    val isMine: Boolean,
    val isDocument: Boolean = false,
    val documentUri: Uri? = null,
    val mimeType: String? = null,
    val isSeen: Boolean = false
)


