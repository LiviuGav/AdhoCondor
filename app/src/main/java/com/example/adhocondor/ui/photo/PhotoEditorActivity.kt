package com.example.adhocondor.ui.photo

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.adhocondor.R
import com.yalantis.ucrop.UCrop
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.PhotoFilter
import java.io.File

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var photoEditorView: PhotoEditorView
    private lateinit var photoEditor: PhotoEditor

    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_editor)

        photoEditorView = findViewById(R.id.photoEditorView)

        imageUri = intent.getParcelableExtra("imageUri")
        imageUri?.let {
            photoEditorView.source.setImageURI(it)
        }

        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .build()

        // Btn setup
        findViewById<ImageButton>(R.id.btnBrush).setOnClickListener {
            photoEditor.setBrushDrawingMode(true)
        }
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { photoEditor.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { photoEditor.redo() }
        findViewById<ImageButton>(R.id.btnText).setOnClickListener {
            photoEditor.addText("Text", ContextCompat.getColor(this, android.R.color.black))
        }
        findViewById<ImageButton>(R.id.btnEmoji).setOnClickListener { photoEditor.addEmoji("😊") }
        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            photoEditor.setFilterEffect(PhotoFilter.SEPIA)
        }
        findViewById<ImageButton>(R.id.btnCrop).setOnClickListener {
            imageUri?.let { startUCrop(it) }
        }
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
            try {
                saveToFile()
            } catch (e: SecurityException) {
                Toast.makeText(
                    this,
                    "Storage permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    private fun startUCrop(uri: Uri) {
        val destUri = Uri.fromFile(
            File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        )
        UCrop.of(uri, destUri)
            .withAspectRatio(1f, 1f)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let {
                imageUri = it
                photoEditorView.source.setImageURI(it)
            }
        }
    }

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private fun saveToFile() {
        val folder = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "AdhoCondor"
        )
        folder.mkdirs()

        val filename = "AdhoCondor_${System.currentTimeMillis()}.jpg"
        val file = File(folder, filename)

        photoEditor.saveAsFile(file.absolutePath, object : PhotoEditor.OnSaveListener {
            override fun onSuccess(imagePath: String) {
                Toast.makeText(this@PhotoEditorActivity, "Saved to file!", Toast.LENGTH_SHORT).show()
                // Optional: adaugă în galerie MediaStore
                addToGallery(file)
            }

            override fun onFailure(exception: Exception) {
                Toast.makeText(
                    this@PhotoEditorActivity,
                    "Save failed: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun addToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/AdhoCondor")
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                file.inputStream().copyTo(out)
            }
            Toast.makeText(this, "Added to gallery!", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("editedImageUri", it)
            })
            finish()
        }
    }
}
