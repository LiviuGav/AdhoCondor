package com.example.adhocondor.ui.chat

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.adhocondor.R
import com.example.adhocondor.manager.ChatManager
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.google.GoogleEmojiProvider
import com.vanniktech.emoji.EmojiView
import com.vanniktech.emoji.recent.RecentEmojiManager
import com.vanniktech.emoji.search.SearchEmojiManager
import com.vanniktech.emoji.variant.VariantEmojiManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.adhocondor.ui.photo.PhotoEditorActivity


class ChatActivity : AppCompatActivity() {

    lateinit var chatManager: ChatManager
    lateinit var recyclerView: RecyclerView
    val messages = mutableListOf<ChatMessage>()
    lateinit var chatAdapter: ChatAdapter

    private var pendingDocumentMimeType: String? = null

    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var emojiButton: ImageButton
    private lateinit var emojiPopup: EmojiPopup

    private lateinit var userId: String
    private lateinit var nickname: String
    private var isGroupOwner: Boolean = false
    private var groupOwnerIP: String? = null

    // Trust request UI
    lateinit var trustRequestLayout: LinearLayout
    lateinit var trustYesButton: Button
    lateinit var trustNoButton: Button

    // Header UI
    private lateinit var backButton: ImageButton
    private lateinit var profileBackground: View
    private lateinit var initialsText: TextView
    private lateinit var otherUserName: TextView
    private lateinit var onlineDot: View
    private lateinit var onlineStatusText: TextView
    private lateinit var searchButton: ImageButton
    private lateinit var moreButton: ImageButton

    private lateinit var emojiContainer: LinearLayout
    private lateinit var emojiView: EmojiView
    private var isEmojiVisible = false

    private lateinit var attachButton: ImageButton
    private lateinit var attachOptionsLayout: LinearLayout

    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var documentPreviewLayout: LinearLayout
    private lateinit var documentPreviewIcon: ImageView
    private lateinit var documentPreviewName: TextView
    private lateinit var documentPreviewRemove: ImageButton
    private var pendingDocumentUri: android.net.Uri? = null

    private lateinit var openCameraLauncher: ActivityResultLauncher<Uri>
    private var cameraImageUri: Uri? = null

    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var editLauncher: ActivityResultLauncher<Intent>



    // Other user info
    private var otherNickname: String = "Anonymous"
    var otherUserId: String = ""
    private var isOtherOnline: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Intent extras
        userId = intent.getStringExtra("USER_ID") ?: "unknown"
        nickname = intent.getStringExtra("NICKNAME") ?: "Anonymous"
        isGroupOwner = intent.getBooleanExtra("IS_GROUP_OWNER", false)
        groupOwnerIP = intent.getStringExtra("GROUP_OWNER_IP")

        // Find views
        recyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        emojiButton = findViewById(R.id.emojiButton)

        trustRequestLayout = findViewById(R.id.trustRequestLayout)
        trustYesButton = findViewById(R.id.trustYesButton)
        trustNoButton = findViewById(R.id.trustNoButton)

        backButton = findViewById(R.id.backButton)
        profileBackground = findViewById(R.id.profileBackground)
        initialsText = findViewById(R.id.initialsText)
        otherUserName = findViewById(R.id.otherUserName)
        onlineDot = findViewById(R.id.onlineDot)
        onlineStatusText = findViewById(R.id.onlineStatusText)
        searchButton = findViewById(R.id.searchButton)
        moreButton = findViewById(R.id.moreButton)

        emojiContainer = findViewById(R.id.emojiContainer)
        emojiView = findViewById(R.id.emojiView)

        attachButton = findViewById(R.id.attachButton)
        attachOptionsLayout = findViewById(R.id.attachOptionsLayout)

        documentPreviewLayout = findViewById(R.id.documentPreviewLayout)
        documentPreviewIcon = findViewById(R.id.documentPreviewIcon)
        documentPreviewName = findViewById(R.id.documentPreviewName)
        documentPreviewRemove = findViewById(R.id.documentPreviewRemove)

        chatAdapter = ChatAdapter(messages) { uri, mimeType ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(Intent.createChooser(intent, "Deschide fișier"))
        }


        recyclerView.adapter = chatAdapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = false }

        // Header buttons
        backButton.setOnClickListener { finish() }
        searchButton.setOnClickListener { Toast.makeText(this, "Search coming soon", Toast.LENGTH_SHORT).show() }
        moreButton.setOnClickListener { }

        updateChatHeader()

        // Initialize ChatManager
        chatManager = ChatManager(
            activity = this,
            userId = userId,
            nickname = nickname,
            adapter = chatAdapter,
            recyclerView = recyclerView,
            messageInput = messageInput,
            sendButton = sendButton
        )

        // Start chat
        if (isGroupOwner) {
            chatManager.startChatAsServer()
        } else if (groupOwnerIP != null) {
            chatManager.startChatAsClient(groupOwnerIP!!)
        } else {
            finish()
        }

        EmojiManager.install(GoogleEmojiProvider())

        emojiView.setUp(
            rootView = findViewById(android.R.id.content),
            editText = messageInput,
            recentEmoji = RecentEmojiManager(this),
            searchEmoji = SearchEmojiManager(),
            variantEmoji = VariantEmojiManager(this),
            onEmojiClickListener = null,
            onEmojiBackspaceClickListener = null,
            pageTransformer = null
        )


        emojiButton.setOnClickListener {
            toggleEmoji()
        }
        
        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isEmojiVisible) {
                hideEmoji()
            }
        }
        messageInput.setOnClickListener {
            if (isEmojiVisible) {
                hideEmoji()
            }
        }

        attachButton.setOnClickListener {
            toggleAttachOptions()
        }

        setupAttachOptions()

        openDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                handleSelectedDocument(uri)
            }
        }


        openCameraLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && cameraImageUri != null) {
                // În loc de UCrop:
                val editIntent = Intent(this, PhotoEditorActivity::class.java)
                editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                editIntent.putExtra("imageUri", cameraImageUri)
                editLauncher.launch(editIntent)
            }
        }


        editLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val editedUri = result.data?.getParcelableExtra<Uri>("editedImageUri")
                editedUri?.let { showImagePreview(it) }
            }
        }


        cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    openCameraInternal()
                } else {
                    Toast.makeText(
                        this,
                        "Camera permission is required to take photos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        documentPreviewRemove.setOnClickListener {
            pendingDocumentUri = null
            documentPreviewLayout.visibility = View.GONE
        }

        sendButton.setOnClickListener {
            if (pendingDocumentUri != null) {
                val uri = pendingDocumentUri!!
                val fileName = documentPreviewName.text.toString()  // numele afișat în preview
                val mimeType = pendingDocumentMimeType ?: "*/*"

                // Salvează o copie locală pentru tine (ca la receptor)
                val localUri = saveSentFileLocally(uri, fileName)

                chatManager.sendDocument(uri, fileName, mimeType)

                // Adaugă mesajul cu URI-ul local (care va supraviețui)
                chatAdapter.addMessage(
                    ChatMessage(
                        userId = userId,
                        nickname = nickname,
                        message = fileName,
                        time = ChatUtils.getCurrentTime(),
                        isMine = true,
                        isDocument = true,
                        documentUri = localUri,
                        mimeType = mimeType
                    )
                )
                recyclerView.scrollToPosition(chatAdapter.itemCount - 1)

                // Resetează preview
                pendingDocumentUri = null
                pendingDocumentMimeType = null
                documentPreviewLayout.visibility = View.GONE
            } else {
                chatManager.sendMessage()
            }
        }


    }
    private fun toggleAttachOptions() {
        if (attachOptionsLayout.visibility == View.VISIBLE) {
            attachOptionsLayout.visibility = View.GONE
        } else {
            hideEmoji()
            attachOptionsLayout.visibility = View.VISIBLE
        }
    }

    private fun setupAttachOptions() {
        setupOption(
            findViewById(R.id.optionDocument),
            R.drawable.ic_document,
            "Document",
            "#0059FF"
        ) { openDocumentPicker() }

        setupOption(
            findViewById(R.id.optionPicture),
            R.drawable.ic_image,
            "Picture",
            "#DB4BFF"
        ) { openGallery() }

        setupOption(
            findViewById(R.id.optionCamera),
            R.drawable.ic_camera_white,
            "Camera",
            "#FF4B84"
        ) { openCamera() }

        setupOption(
            findViewById(R.id.optionContacts),
            R.drawable.ic_contacts,
            "Contacts",
            "#30D5C8"
        ) { openContacts() }
    }

    private fun setupOption(
        view: View,
        iconRes: Int,
        text: String,
        colorHex: String,
        onClick: () -> Unit
    ) {
        val icon = view.findViewById<ImageView>(R.id.attachIcon)
        val label = view.findViewById<TextView>(R.id.attachText)
        val bg = icon.parent as View

        icon.setImageResource(iconRes)
        label.text = text
        bg.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))


        view.setOnClickListener {
            attachOptionsLayout.visibility = View.GONE
            onClick()
        }
    }
    private fun openDocumentPicker() {
        openDocumentLauncher.launch(arrayOf("*/*"))  // tip general document
    }


    private fun handleSelectedDocument(uri: Uri) {
        pendingDocumentUri = uri

        // Obține numele fișierului în mod sigur
        val fileName = getFileNameFromUri(uri) ?: "unknown_file_${System.currentTimeMillis()}"

        documentPreviewName.text = fileName
        documentPreviewLayout.visibility = View.VISIBLE

        // Detectează MIME type după extensie (folosind numele corect)
        val extension = fileName.substringAfterLast('.').lowercase().takeIf { it.isNotEmpty() } ?: ""
        val mimeType = if (extension.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension) ?: "*/*"
        } else {
            "*/*"
        }

        pendingDocumentMimeType = mimeType

        // Opțional: setează iconița corectă în preview (dacă vrei)
        // de exemplu, pentru imagini arată un icon de poză
        if (mimeType.startsWith("image/")) {
            documentPreviewIcon.setImageResource(R.drawable.ic_image)
        } else {
            documentPreviewIcon.setImageResource(R.drawable.ic_document)
        }
    }

    /**
     * Funcție helper sigură pentru a obține numele fișierului din orice URI
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        // Caz special: URI-uri de tip content:// (din Document Picker, Gallery etc.)
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatActivity", "Eroare la query pentru nume fișier", e)
            }
        }

        // Fallback: încearcă să extragi din path-ul URI-ului
        return uri.pathSegments.lastOrNull()
            ?: uri.lastPathSegment
            ?: uri.toString().substringAfterLast('/').takeIf { it.isNotBlank() }
    }



    private fun openGallery() {
        Toast.makeText(this, "Gallery", Toast.LENGTH_SHORT).show()
    }

    private fun openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            openCameraInternal()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }



    private fun openContacts() {
        Toast.makeText(this, "Contacts", Toast.LENGTH_SHORT).show()
    }

    private fun openCameraInternal() {
        // Creează fișier temporar pentru camera
        val imageFile = kotlin.io.path.createTempFile(prefix = "camera_", suffix = ".jpg").toFile()
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )

        // Lansează camera
        openCameraLauncher.launch(cameraImageUri)
    }

    private fun saveSentFileLocally(originalUri: Uri, fileName: String): Uri {
        val tempFile = kotlin.io.path.createTempFile(
            prefix = fileName.take(10),
            suffix = "." + fileName.substringAfterLast(".", "jpg")
        ).toFile()

        contentResolver.openInputStream(originalUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            tempFile
        )
    }

    private fun showImagePreview(uri: Uri) {
        pendingDocumentUri = uri
        pendingDocumentMimeType = "image/jpeg"

        // Generăm un nume pentru fișierul crop-uit
        val fileName = "AdhoCondor_Photo_${System.currentTimeMillis()}.jpg"

        documentPreviewIcon.setImageResource(R.drawable.ic_image)
        documentPreviewName.text = fileName
        documentPreviewLayout.visibility = View.VISIBLE
    }

    private fun toggleEmoji() {
        if (isEmojiVisible) {
            hideEmoji()
            showKeyboard()
        } else {
            hideKeyboard()
            showEmoji()
        }
    }

    private fun showEmoji() {
        emojiContainer.visibility = View.VISIBLE
        emojiButton.setImageResource(R.drawable.ic_keyboard)
        isEmojiVisible = true
        scrollToBottom()
    }

    private fun hideEmoji() {
        emojiContainer.visibility = View.GONE
        emojiButton.setImageResource(R.drawable.ic_emoji)
        isEmojiVisible = false
    }

    private fun showKeyboard() {
        messageInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
    }

    private fun scrollToBottom() {
        recyclerView.post {
            recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun updateChatHeader() {
        otherUserName.text = otherNickname
        val initials = if (otherNickname.isBlank()) "?" else
            otherNickname.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(2)
        initialsText.text = initials
        initialsText.visibility = View.VISIBLE

        if (isOtherOnline) {
            onlineDot.visibility = View.VISIBLE
            onlineStatusText.text = "Online"
        } else {
            onlineDot.visibility = View.GONE
            onlineStatusText.text = "Offline"
        }
    }

    fun setOtherUserInfo(nickname: String, userId: String) {
        this.otherNickname = nickname
        this.otherUserId = userId
        this.isOtherOnline = true
        runOnUiThread { updateChatHeader() }
    }

    fun setOtherUserOnline(online: Boolean) {
        this.isOtherOnline = online
        runOnUiThread { updateChatHeader() }
    }

    override fun onBackPressed() {
        if (isEmojiVisible) {
            hideEmoji()
        } else {
            super.onBackPressed()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        emojiView.tearDown()
        if (::emojiPopup.isInitialized && emojiPopup.isShowing) emojiPopup.dismiss()
        chatManager.stopChat()

        // Remove WiFi P2P group
        val manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
    }
}
