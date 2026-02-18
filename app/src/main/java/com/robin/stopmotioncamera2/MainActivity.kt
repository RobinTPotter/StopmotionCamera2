package com.robin.stopmotioncamera2

import androidx.camera.core.Camera
import android.view.MotionEvent
import androidx.camera.core.FocusMeteringAction

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.util.Size

import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio

import androidx.camera.core.CameraSelector

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.robin.stopmotioncamera2.utils.getLastImagesByName
import com.robin.stopmotioncamera2.utils.hasCameraPermission
import com.robin.stopmotioncamera2.utils.nextFile
import com.robin.stopmotioncamera2.utils.outputFolder
import com.robin.stopmotioncamera2.utils.renameAllJpgImagesAlphabetically
import com.robin.stopmotioncamera2.utils.saveImageToPublicPictures
import com.robin.stopmotioncamera2.utils.updateOnionSkins
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var onionSkinView: ImageView
    private lateinit var imageCapture: ImageCapture
    private lateinit var label: TextView
    private var savedImages: MutableList<Uri?> = mutableListOf()
    private var currentScene: Int = 0
    private var camera: Camera? = null
    private var onionSkins: Int = 2
    private var opacityStart: Float = 0.5f
    private var opacityEnd: Float = 0.35f
    private var showCrosshair: Boolean = true
    private var showThirds: Boolean = false

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
loadSettings();
        window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        previewView = findViewById(R.id.previewView)
        onionSkinView = findViewById(R.id.onionSkinView)
        label = findViewById(R.id.label)

        val captureButton = findViewById<Button>(R.id.captureButton)
        captureButton.setOnClickListener {
            takePicture()
        }

        val upSceneButton = findViewById<Button>(R.id.upFolder)
        upSceneButton.setOnClickListener {
            currentScene += 1
            label.text = String.format("Scene %d", currentScene)
            updateSavedImages()
        }

        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                tapToFocus(event.x, event.y)
                true
            } else false
        }

        val downSceneButton = findViewById<Button>(R.id.downFolder)
        downSceneButton.setOnClickListener {
            if (currentScene > 0) currentScene -= 1
            label.text = String.format("Scene %d", currentScene)
            updateSavedImages()
        }

        if (hasCameraPermission(this)) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Add this to your MainActivity.kt onCreate() method:

        val previewButton = findViewById<Button>(R.id.previewButton)
        previewButton.setOnClickListener {
            val intent = Intent(this, FramePreviewActivity::class.java)
            intent.putExtra("SCENE_NUMBER", currentScene)
            startActivity(intent)
        }

        val settingsButton = findViewById<Button>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Initialize onion skins for the default scene
        label.text = String.format("Scene %d", currentScene)
        updateSavedImages()
    }


    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        onionSkins = prefs.getInt("onion_skins", 2)
        opacityStart = prefs.getFloat("opacity_start", 0.5f)
        opacityEnd = prefs.getFloat("opacity_end", 0.35f)
        showCrosshair = prefs.getBoolean("show_crosshair", true)
        showThirds = prefs.getBoolean("show_thirds", false)
    }


    override fun onResume() {
        super.onResume()
        loadSettings()
        updateSavedImages()  // Refresh with new settings
    }

    private fun tapToFocus(x: Float, y: Float) {
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .addPoint(point, FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun updateSavedImages() {
        val sub = outputFolder(currentScene)
        Log.i("MainActivity", "updateSavedImages for folder: $sub")
        savedImages = getLastImagesByName(this@MainActivity, sub, numImages = onionSkins)

        val resultBitmap: Bitmap = updateOnionSkins(                                                               this@MainActivity,
    savedImages,                                                                onionSkins,
    showCrosshair,
    showThirds,
    opacityStart,
    opacityEnd
)



//updateOnionSkins(this@MainActivity, savedImages, onionSkins)
        
        onionSkinView.setImageBitmap(resultBitmap)
        Log.i("MainActivity", "Onion skin updated with ${savedImages.size} images")
    }

    private fun takePicture() {
        val photoFile = File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("CameraX", "Saved to ${photoFile.absolutePath}")
                    val temp = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // Use consistent folder path
                    val sub = outputFolder(currentScene)
                    Log.i("MainActivity", "Taking picture for folder: $sub")

                    // Use coroutine for async operations
                    lifecycleScope.launch {
                        try {
                            // Rename existing files on IO thread
                            withContext(Dispatchers.IO) {
                                Log.i("MainActivity", "Renaming files in $sub")
                                renameAllJpgImagesAlphabetically(this@MainActivity, sub)
                            }

                            // After renaming, get the next available file number
                            val next = nextFile(this@MainActivity, sub)
                            Log.i("MainActivity", "Next file will be: $next")

                            // Save the new image
                            val uri = saveImageToPublicPictures(
                                this@MainActivity, temp, sub, next
                            )

                            if (uri != null) {
                                Log.i("MainActivity", "Saved new image: $uri")

                                // Update the saved images list and refresh onion skins
                                updateSavedImages()

                                label.text = "Frame: $next"
                                // Toast.makeText(this@MainActivity, "Frame $next saved", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("MainActivity", "Failed to save image")
                                Toast.makeText(this@MainActivity, "Error saving frame", Toast.LENGTH_SHORT).show()
                            }

                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in save/rename process: ${e.message}", e)
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Failed to save photo: ${exc.message}", exc)
                    Toast.makeText(this@MainActivity, "Camera error: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_16_9,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                ).setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                imageCapture =
                    ImageCapture.Builder().setResolutionSelector(resolutionSelector).build()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            } catch (e: Exception) {
                Log.e("CameraX", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (hasCameraPermission(this)) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
