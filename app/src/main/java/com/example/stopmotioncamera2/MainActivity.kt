package com.example.stopmotioncamera2

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import com.example.stopmotioncamera2.utils.getLastImagesByName
import com.example.stopmotioncamera2.utils.hasCameraPermission
import com.example.stopmotioncamera2.utils.nextFile
import com.example.stopmotioncamera2.utils.outputFolder
import com.example.stopmotioncamera2.utils.renameImagesInMediaStore
import com.example.stopmotioncamera2.utils.saveImageToPublicPictures
import com.example.stopmotioncamera2.utils.updateOnionSkins
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var onionSkinView: ImageView
    private lateinit var imageCapture: ImageCapture
    private lateinit var label: TextView
    private var savedImages: MutableList<Uri?> = mutableListOf()
    private var currentScene: Int = 0
    private var onionSkins: Int = 2

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        previewView = findViewById(R.id.previewView)
        onionSkinView = findViewById(R.id.onionSkinView)
        label = findViewById(R.id.label)


        val captureButton = findViewById<Button>(R.id.captureButton)
        captureButton.setOnClickListener {
            takePicture()
            label.text = "!"
        }


        val upSceneButton = findViewById<Button>(R.id.upFolder)
        upSceneButton.setOnClickListener {
            currentScene += 1
            label.text = String.format("%d", currentScene)
            updateSavedImages()
        }


        val downSceneButton = findViewById<Button>(R.id.downFolder)
        downSceneButton.setOnClickListener {
            if (currentScene > 0) currentScene -= 1
            label.text = String.format("%d", currentScene)
            updateSavedImages()
        }

        if (hasCameraPermission(this)) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun updateSavedImages() {
        val sub = outputFolder(currentScene)
        savedImages = getLastImagesByName(this@MainActivity, sub, numImages = onionSkins)

        val resultBitmap: Bitmap = if (savedImages.size > 0) {
            updateOnionSkins(this@MainActivity, savedImages, onionSkins)
        } else {
            Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        }
        onionSkinView.setImageBitmap(resultBitmap)

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

                    val sub = outputFolder(currentScene)
                    renameImagesInMediaStore(this@MainActivity, sub, "temp_")
                    renameImagesInMediaStore(this@MainActivity, sub)

                    val next = nextFile(this@MainActivity, sub)
                    val uri = saveImageToPublicPictures(
                        this@MainActivity, temp, sub, next
                    )

                    savedImages.add(uri)
                    label.text = photoFile.absolutePath
                    onionSkinView.setImageBitmap(
                        updateOnionSkins(
                            this@MainActivity,
                            savedImages,
                            onionSkins
                        )
                    )
                    Log.i("CameraX", "saved image if you're lucky to $photoFile")
                    Toast.makeText(this@MainActivity, uri.toString(), Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Failed to save photo: ${exc.message}", exc)
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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

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
