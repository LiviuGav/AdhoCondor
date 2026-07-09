package com.example.adhocondor.ui.main

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.adhocondor.R
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

class StarterPage : ComponentActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var imgCamera: ImageView
    private lateinit var profileContainer: FrameLayout

    private lateinit var selectImageLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starter)

        val animalImage: ImageView = findViewById(R.id.condorImage)
        val discoverButton: Button = findViewById(R.id.discoverButton)
        val nicknameInput: EditText = findViewById(R.id.nicknameInput)

        imgProfile = findViewById(R.id.imgProfile)
        imgCamera = findViewById(R.id.imgCamera)
        profileContainer = findViewById(R.id.profileContainer)

        // Animatie condor
        animalImage.animate()
            .translationX(800f)
            .setDuration(4000)
            .setStartDelay(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // UUID
        val prefs = getSharedPreferences("AdHocPrefs", MODE_PRIVATE)
        var userId = prefs.getString("USER_ID", null)
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit().putString("USER_ID", userId).apply()
        }

        // Permisiuni și discover button (la fel ca înainte)
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val granted = perms["android.permission.ACCESS_FINE_LOCATION"] == true &&
                    perms["android.permission.NEARBY_WIFI_DEVICES"] == true
            if (!granted) {
                Toast.makeText(
                    this,
                    "Trebuie acordate permisiunile Wi-Fi și locație!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        permissionLauncher.launch(
            arrayOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.NEARBY_WIFI_DEVICES"
            )
        )

        discoverButton.setOnClickListener {
            val fineLocation = ContextCompat.checkSelfPermission(
                this, "android.permission.ACCESS_FINE_LOCATION"
            ) == PackageManager.PERMISSION_GRANTED

            val wifiNearby = ContextCompat.checkSelfPermission(
                this, "android.permission.NEARBY_WIFI_DEVICES"
            ) == PackageManager.PERMISSION_GRANTED

            if (fineLocation && wifiNearby) {
                val nickname = nicknameInput.text.toString().ifEmpty { "Anonim" }

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("autoDiscover", true)
                    putExtra("USER_ID", userId)
                    putExtra("NICKNAME", nickname)
                }
                startActivity(intent)
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.NEARBY_WIFI_DEVICES"
                    )
                )
            }
        }

        // --- Selectare și crop imagine circular ---
        selectImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { sourceUri ->
                val destinationUri = Uri.fromFile(
                    File(
                        cacheDir,
                        "cropped_profile_${System.currentTimeMillis()}.jpg"
                    )
                )

                val options = UCrop.Options().apply {
                    setCircleDimmedLayer(true)
                    setHideBottomControls(false)
                    setShowCropFrame(false)
                    setShowCropGrid(false)
                }

                UCrop.of(sourceUri, destinationUri)
                    .withOptions(options)
                    .withAspectRatio(1f, 1f)
                    .start(this)
            }
        }

        profileContainer.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let {
                imgProfile.setImageURI(it)
                imgProfile.visibility = View.VISIBLE
                imgCamera.visibility = View.GONE
                profileContainer.setBackgroundColor(Color.TRANSPARENT)
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(this, "Eroare crop: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }
}