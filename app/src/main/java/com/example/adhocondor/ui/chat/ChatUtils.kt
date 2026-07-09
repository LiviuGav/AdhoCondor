package com.example.adhocondor.ui.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatUtils {
    fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}