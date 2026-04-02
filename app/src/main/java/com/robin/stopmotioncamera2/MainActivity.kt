package com.robin.stopmotioncamera2

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
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
import com.robin.stopmotioncamera2.utils.getFrameFiles
import com.robin.stopmotioncamera2.utils.hasCameraPermission
import com.robin.stopmotioncamera2.utils.saveFrame
import com.robin.stopmotioncamera2.utils.updateOnionSkins
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var onionSkinView: ImageView
    private lateinit var imageCapture: ImageCapture
    private lateinit var label: TextView

    private var currentScene: Int = 0
    private val onionSkins: Int = 2

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.insetsController?.hide(
            android.view.WindowInsets.Type.statusBars() or
            android.view.WindowInsets.Type.navigationBars()
        )
        window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        previewView   = findViewById(R.id.previewView)
        onionSkinView = findViewById(R.id.onionSkinView)
        label         = findViewById(R.id.label)

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            takePicture()
        }

        findViewById<Button>(R.id.upFolder).setOnClickListener {
            currentScene++
            label.text = currentScene.toString()
            refreshOnionSkin()
        }

        findViewById<Button>(R.id.downFolder).setOnClickListener {
            if (currentScene > 0) currentScene--
            label.text = currentScene.toString()
            refreshOnionSkin()
        }

        // ── NEW: open the preview / editor screen ──────────────────────────
        findViewById<Button>(R.id.previewButton).setOnClickListener {
            startActivity(
                Intent(this, FramePreviewActivity::class.java)
                    .putExtra("SCENE_NUMBER", currentScene)
            )
        }

        if (hasCameraPermission(this)) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    // ── Onion skin ────────────────────────────────────────────────────────────

    private fun refreshOnionSkin() {
        val frames = getFrameFiles(this, currentScene)
        val bitmap = if (frames.isNotEmpty()) {
            updateOnionSkins(frames, onionSkins)
        } else {
            Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        }
        onionSkinView.setImageBitmap(bitmap)
        label.text = if (frames.isNotEmpty()) "Scene $currentScene  [${frames.size} frames]"
                     else "Scene $currentScene"
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    private fun takePicture() {
        val tempFile = File(cacheDir, "capture_temp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath) ?: run {
                        Log.e("MainActivity", "Failed to decode captured image")
                        return
                    }
                    // Write directly to app-private external storage — no SAF, no MediaStore
                    val saved = saveFrame(this@MainActivity, bitmap, currentScene)
                    bitmap.recycle()
                    tempFile.delete()

                    Log.i("MainActivity", "Frame saved: ${saved.absolutePath}")
                    refreshOnionSkin()
                    Toast.makeText(this@MainActivity, "Saved ${saved.name}", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("MainActivity", "Capture failed: ${exc.message}", exc)
                    Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ── Camera setup ──────────────────────────────────────────────────────────

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_16_9,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                )
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(selector).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            try {
                provider.unbindAll()
                imageCapture = ImageCapture.Builder()
                    .setResolutionSelector(selector).build()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (hasCameraPermission(this)) startCamera() else finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
