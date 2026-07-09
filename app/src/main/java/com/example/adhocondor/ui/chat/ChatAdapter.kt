package com.example.adhocondor.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adhocondor.R

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val openDocumentCallback: (uri: android.net.Uri, mimeType: String?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MINE = 1
        private const val TYPE_OTHER = 2
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].isMine) TYPE_MINE else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MINE) {
            MineVH(inflater.inflate(R.layout.item_chat_message_mine, parent, false))
        } else {
            OtherVH(inflater.inflate(R.layout.item_chat_message_other, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]

        when (holder) {
            is MineVH -> {
                bindMessage(holder.message, msg, isMine = true)
                holder.time.text = msg.time
                holder.seen.visibility = if (msg.isSeen) View.VISIBLE else View.GONE
            }
            is OtherVH -> {
                bindMessage(holder.message, msg, isMine = false)
                holder.time.text = msg.time
            }
        }
    }

    private fun bindMessage(textView: TextView, msg: ChatMessage, isMine: Boolean) {
        textView.text = if (msg.isDocument) "📄 ${msg.message}" else msg.message

        if (msg.isDocument && msg.documentUri != null) {
            textView.setOnClickListener {
                openDocumentCallback(msg.documentUri, msg.mimeType ?: "*/*")
            }
        } else {
            textView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    class MineVH(view: View) : RecyclerView.ViewHolder(view) {
        val message: TextView = view.findViewById(R.id.messageText)
        val time: TextView = view.findViewById(R.id.timeText)
        val seen: ImageView = view.findViewById(R.id.seenIcon)
    }

    class OtherVH(view: View) : RecyclerView.ViewHolder(view) {
        val message: TextView = view.findViewById(R.id.messageText)
        val time: TextView = view.findViewById(R.id.timeText)
    }
}
