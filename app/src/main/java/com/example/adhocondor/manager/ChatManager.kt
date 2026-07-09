package com.example.adhocondor.manager

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.adhocondor.network.ChatClient
import com.example.adhocondor.network.ChatServer
import com.example.adhocondor.ui.chat.ChatActivity
import com.example.adhocondor.ui.chat.ChatAdapter
import com.example.adhocondor.ui.chat.ChatMessage
import com.example.adhocondor.ui.chat.ChatUtils
import java.text.SimpleDateFormat
import java.util.*

class ChatManager(
    private val activity: ChatActivity,
    private val userId: String,
    private val nickname: String,
    private val adapter: ChatAdapter,
    private val recyclerView: RecyclerView,
    private val messageInput: EditText,
    private val sendButton: ImageButton
) {

    private var chatServer: ChatServer? = null
    private var chatClient: ChatClient? = null
    private var chatEnabled = false
    private val blockedDevices = mutableSetOf<String>()

    // Map pentru documentele primite (nume fișier -> URI temporar)
    private val receivedDocuments = mutableMapOf<String, Uri>()

    /* ===================== SERVER ===================== */
    fun startChatAsServer() {
        stopChat()
        chatServer = ChatServer(activity) { msg ->
            handleIncomingTextMessage(msg)
        }
        chatServer?.startServer {
            showTrustRequest(
                onAccept = { enableChat() },
                onRefuse = {
                    stopChat()
                    activity.finish()
                }
            )
        }
    }

    /* ===================== CLIENT ===================== */
    fun startChatAsClient(serverIP: String) {
        if (blockedDevices.contains(serverIP)) return

        stopChat()
        chatClient = ChatClient(serverIP, activity) { msg ->
            handleIncomingTextMessage(msg)
        }
        chatClient?.startClient()

        showTrustRequest(
            onAccept = { enableChat() },
            onRefuse = {
                blockedDevices.add(serverIP)
                stopChat()
                activity.finish()
            }
        )
    }

    /* ===================== RECEIVE TEXT ===================== */
    private fun handleIncomingTextMessage(msg: String) {
        if (!chatEnabled) return

        // Mesaj text normal: userId|nickname|text
        val parts = msg.split("|", limit = 3)
        if (parts.size != 3) return

        val senderId = parts[0]
        val senderNick = parts[1]
        val messageText = parts[2]

        activity.runOnUiThread {
            if (activity.otherUserId.isEmpty()) {
                activity.setOtherUserInfo(senderNick, senderId)
            }

            adapter.addMessage(
                ChatMessage(
                    userId = senderId,
                    nickname = senderNick,
                    message = messageText,
                    time = ChatUtils.getCurrentTime(),
                    isMine = senderId == userId,
                    isSeen = false
                )
            )
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    /* ===================== TRUST REQUEST ===================== */
    private fun showTrustRequest(onAccept: () -> Unit, onRefuse: () -> Unit) {
        activity.runOnUiThread {
            activity.trustRequestLayout.visibility = android.view.View.VISIBLE
            activity.trustYesButton.setOnClickListener {
                activity.trustRequestLayout.visibility = android.view.View.GONE
                onAccept()
            }
            activity.trustNoButton.setOnClickListener {
                activity.trustRequestLayout.visibility = android.view.View.GONE
                onRefuse()
            }
        }
    }

    /* ===================== SEND ===================== */
    fun sendMessage() {
        if (!chatEnabled) return
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val formatted = "$userId|$nickname|$text"

        chatServer?.sendMessage(formatted) ?: chatClient?.sendMessage(formatted)

        adapter.addMessage(
            ChatMessage(
                userId = userId,
                nickname = nickname,
                message = text,
                time = ChatUtils.getCurrentTime(),
                isMine = true,
                isSeen = false
            )
        )
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        messageInput.text.clear()
    }

    fun sendDocument(uri: Uri, fileName: String, mimeType: String?) {
        if (!chatEnabled) return
        try {
            val assetFd = activity.contentResolver.openAssetFileDescriptor(uri, "r") ?: return
            val fileSize = assetFd.length
            assetFd.close()
            if (fileSize <= 0) return

            val inputStream = activity.contentResolver.openInputStream(uri) ?: return

            // Trimite cu numele corect
            chatServer?.sendFile(inputStream, fileName, fileSize)
                ?: chatClient?.sendFile(inputStream, fileName, fileSize)

        } catch (e: Exception) {
            Log.e("ChatManager", "Eroare trimitere document", e)
        }
    }



    private fun getFileName(uri: Uri): String {
        var name = "unknown_file"
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    cursor.getString(index)?.let { name = it }
                }
            }
        }
        return name
    }

    /* ===================== GENERAL HELPERS ===================== */
    private fun enableChat() {
        chatEnabled = true
    }

    fun stopChat() {
        chatEnabled = false
        chatServer?.stopServer()
        chatClient?.stopClient()
        chatServer = null
        chatClient = null
    }
}