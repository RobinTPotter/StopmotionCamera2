package com.example.stopmotioncamera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var onionSkinView: ImageView
    private lateinit var imageCapture: ImageCapture
    private var savedImages: MutableList<File> = mutableListOf()
    private var dateFolder: File? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        previewView = findViewById(R.id.previewView)
        onionSkinView = findViewById(R.id.onionSkinView)
        val captureButton = findViewById<Button>(R.id.captureButton)
        captureButton.setOnClickListener {
            takePicture()
        }

        val overlay = BitmapFactory.decodeResource(resources, R.drawable.overlay_guide)
        onionSkinView.setImageBitmap(overlay)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }


    }

    private fun takePicture() {
        if (dateFolder == null) {
            val date = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
            dateFolder = File(date)
            if (dateFolder!!.exists()) {
                dateFolder!!.mkdirs()
            }
        }
        val photoFile = createPhotoFile(this)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("CameraX", "Saved to ${photoFile.absolutePath}")
                    // Optional: update your onion skin list here
                    savedImages.add(photoFile)
                    updateOnionSkins()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Failed to save photo: ${exc.message}", exc)
                }
            }
        )


    }

    private fun updateOnionSkins() {
        val lastImage = savedImages.lastOrNull() ?: return
        val bitmap = BitmapFactory.decodeFile(lastImage.absolutePath)
        Log.i("CameraX", lastImage.absolutePath)
        onionSkinView.setImageBitmap(bitmap)
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
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                imageCapture = ImageCapture.Builder().setResolutionSelector(resolutionSelector).build()
                val camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)


            } catch (e: Exception) {
                Log.e("CameraX", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))

    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    fun createPhotoFile(context: Context): File {
        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dateFolder = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
        val folder = File(picturesDir, "StopMotion/$dateFolder")

        if (!folder.exists()) {
            folder.mkdirs()
        }

        // Find next available number
        val nextNumber = folder.listFiles()?.size ?: 0
        val fileName = String.format("%05d.jpg", nextNumber)

        return File(folder, fileName)
    }


    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}