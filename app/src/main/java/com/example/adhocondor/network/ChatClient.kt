package com.example.adhocondor.network

import android.util.Log
import androidx.core.content.FileProvider
import com.example.adhocondor.ui.chat.ChatActivity
import com.example.adhocondor.ui.chat.ChatMessage
import com.example.adhocondor.ui.chat.ChatUtils
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.createTempFile

class ChatClient(
    private val hostAddress: String,
    private val activity: ChatActivity,
    private val onTextMessageReceived: (String) -> Unit
) {
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startClient() {
        scope.launch {
            try {
                socket = Socket().apply {
                    connect(InetSocketAddress(hostAddress, 8888), 8000)
                }

                input = DataInputStream(socket!!.getInputStream())
                output = DataOutputStream(socket!!.getOutputStream())

                // Loop principal de recepție
                while (scope.isActive) {
                    val length = try {
                        input!!.readInt()
                    } catch (e: Exception) {
                        Log.e("ChatReceiveFile", "Eroare readInt: ${e.message}")
                        break
                    }

                    Log.d("ChatReceiveFile", "Length primit: $length")  // **log important**

                    if (length <= 0) break

                    val buffer = ByteArray(length)
                    input!!.readFully(buffer)

                    val message = String(buffer, Charsets.UTF_8)
                    Log.d("ChatReceiveFile", "Header mesaj: $message")  // **log important**

                    if (message.startsWith("FILE|")) {
                        handleFileTransfer(message)
                    } else {
                        onTextMessageReceived(message)
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatClient", "Eroare client: ${e.message}", e)
            } finally {
                stopClient()
            }
        }
    }

    private suspend fun handleFileTransfer(header: String) {
        try {
            val parts = header.split("|")
            if (parts.size != 3) return

            val fileName = parts[1]
            val fileSize = parts[2].toLong()

            // Creăm fișier temporar în cache
            val tempFile = createTempFile(
                prefix = fileName.take(3),
                suffix = "." + fileName.substringAfterLast(".")
            ).toFile()

            var remaining = fileSize
            val buffer = ByteArray(8192)

            tempFile.outputStream().use { outp ->
                while (remaining > 0 && scope.isActive) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input!!.read(buffer, 0, toRead)
                    if (read <= 0) throw Exception("Fișier incomplet")
                    outp.write(buffer, 0, read)
                    remaining -= read
                }
            }

            // Determinăm extensia și MIME type-ul
            val fileExtension = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileExtension)
                ?: guessMimeType(fileName)

            // Obținem URI pentru FileProvider
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                tempFile
            )

            // Adăugăm fișierul în chat fără a-l deschide automat
            withContext(Dispatchers.Main) {
                activity.chatAdapter.addMessage(
                    ChatMessage(
                        userId = "unknown",
                        nickname = "Other",
                        message = fileName,
                        time = ChatUtils.getCurrentTime(),
                        isMine = false,
                        isDocument = true,
                        documentUri = uri,
                        mimeType = mimeType
                    )
                )
                activity.recyclerView.scrollToPosition(activity.chatAdapter.itemCount - 1)
            }

            Log.d("ChatReceiveFile", "Fișier primit: $fileName ($fileSize bytes)")

        } catch (e: Exception) {
            Log.e("ChatReceiveFile", "Eroare recepție fișier: ${e.message}", e)
        }
    }

    /** Funcție helper pentru MIME type fallback */
    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "*/*"
        }
    }



    fun sendMessage(msg: String) {
        scope.launch {
            try {
                val bytes = msg.toByteArray(Charsets.UTF_8)
                output?.writeInt(bytes.size)
                output?.write(bytes)
                output?.flush()
            } catch (e: Exception) {
                Log.e("ChatClient", "Eroare trimitere mesaj", e)
            }
        }
    }

    fun sendFile(inputStream: InputStream, fileName: String, fileSize: Long) {
        scope.launch {
            try {
                output?.let { out ->
                    val header = "FILE|$fileName|$fileSize"
                    val headerBytes = header.toByteArray(Charsets.UTF_8)
                    out.writeInt(headerBytes.size)
                    out.write(headerBytes)
                    out.flush()

                    val buffer = ByteArray(8192)
                    var remaining = fileSize
                    inputStream.use { inp ->
                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = inp.read(buffer, 0, toRead)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            remaining -= read
                        }
                        out.flush()
                    }
                    Log.d("ChatSendFile", "Fișier trimis: $fileName ($fileSize bytes)")
                } ?: Log.e("ChatSendFile", "OutputStream null!")
            } catch (e: Exception) {
                Log.e("ChatSendFile", "Eroare trimitere fișier", e)
            }
        }
    }

    fun stopClient() {
        try {
            output?.close()
            input?.close()
            socket?.close()
        } catch (_: Exception) {}
        scope.cancel()
    }

}
