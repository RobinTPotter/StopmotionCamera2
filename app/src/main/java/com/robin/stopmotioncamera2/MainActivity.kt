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
import android.graphics.Color
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
import com.robin.stopmotioncamera2.utils.*
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
    private var treeUri: Uri? = null
    private var savedImages: MutableList<Uri?> = mutableListOf()
    private var currentScene: Int = 0
    private var camera: Camera? = null
    private var onionSkins: Int = 2
    private var opacityStart: Float = 0.5f
    private var opacityEnd: Float = 0.35f
    private var opacityTotal: Float = 0.5f
    private var showCrosshair: Boolean = true
    private var showThirds: Boolean = false

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        loadSettings()
        
        treeUri = getSavedStopMotionTreeUri(this@MainActivity)
        if (treeUri == null) {
            launchStopMotionPicker(this)
        }

        window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        previewView = findViewById(R.id.previewView)
        onionSkinView = findViewById(R.id.onionSkinView)
        label = findViewById(R.id.label)

        // Tap to focus
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                tapToFocus(event.x, event.y)
                true
            } else false
        }

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

        val downSceneButton = findViewById<Button>(R.id.downFolder)
        downSceneButton.setOnClickListener {
            if (currentScene > 0) currentScene -= 1
            label.text = String.format("Scene %d", currentScene)
            updateSavedImages()
        }

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

        if (hasCameraPermission(this)) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        label.text = String.format("Scene %d", currentScene)
        updateSavedImages()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        onionSkins = prefs.getInt("onion_skins", 2)
        opacityStart = prefs.getFloat("opacity_start", 0.5f)
        opacityTotal = prefs.getFloat("opacity_total", 0.5f)
        opacityEnd = prefs.getFloat("opacity_end", 0.35f)
        showCrosshair = prefs.getBoolean("show_crosshair", true)
        showThirds = prefs.getBoolean("show_thirds", false)
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        
        // Set onion skin view opacity
        onionSkinView.alpha = opacityTotal
        if (opacityTotal >= 1.0f) {
            onionSkinView.setBackgroundColor(Color.BLACK)
        } else {
            onionSkinView.background = null
        }
        
        // Rebuild MediaStore if needed (optional, for gallery app compatibility)
        lifecycleScope.launch {
            if (needsMediaStoreRebuild(this@MainActivity, treeUri)) {
                Log.i("MainActivity", "MediaStore needs rebuild")
                rebuildMediaStoreForStopMotion(this@MainActivity)
            }
            
            // Always update saved images using SAF
            withContext(Dispatchers.Main) {
                updateSavedImages()
            }
        }
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
        
        // Get subfolder URI for SAF access
        val subUri = treeUri?.let { getSubfolderUriFromTreeUri(this@MainActivity, it, sub) }
        
        // Use SAF to load images instead of MediaStore
        savedImages = if (subUri != null) {
            getLastImagesByNameWithSAF(this@MainActivity, subUri, onionSkins)
        } else {
            Log.w("MainActivity", "No SAF access, can't load images")
            mutableListOf()
        }

        val resultBitmap: Bitmap = if (savedImages.size > 0) {
            updateOnionSkins(
                this@MainActivity,
                savedImages,
                onionSkins,
                showCrosshair,
                showThirds,
                opacityStart,
                opacityEnd
            )
        } else {
            Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        }

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

                    val sub = outputFolder(currentScene)
                    Log.i("MainActivity", "Taking picture for folder: $sub")

                    lifecycleScope.launch {
                        try {
                            // Get subfolder URI for SAF
                            val subUri = treeUri?.let { getSubfolderUriFromTreeUri(this@MainActivity, it, sub) }

                            if (subUri != null) {
                                // Rename existing files on IO thread
                                withContext(Dispatchers.IO) {
                                    Log.i("MainActivity", "Renaming files in $sub")
                                    renameAllJpgsWithSAF(this@MainActivity, subUri)
                                }

                                // Use SAF to get next filename
                                val next = nextFileWithSAF(this@MainActivity, subUri)
                                Log.i("MainActivity", "Next file will be: $next")

                                // Save the new image
                                val uri = saveImageToPublicPictures(
                                    this@MainActivity, temp, sub, next
                                )

                                if (uri != null) {
                                    Log.i("MainActivity", "Saved new image: $uri")
                                    updateSavedImages()
                                    label.text = "Frame: $next"
                                } else {
                                    Log.e("MainActivity", "Failed to save image")
                                    Toast.makeText(this@MainActivity, "Error saving frame", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.e("MainActivity", "No SAF access")
                                Toast.makeText(this@MainActivity, "No folder access", Toast.LENGTH_SHORT).show()
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun launchStopMotionPicker(activity: Activity) {
        val pictures = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val stopMotionDir = File(pictures, "StopMotion")

        val initUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            stopMotionDir
        )

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initUri)
        }
        activity.startActivityForResult(intent, REQUEST_TREE)
    }

    fun getSubfolderUriFromTreeUri(context: Context, treeUri: Uri, subfolder: String): Uri? {
        val baseDir = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val target = baseDir.findFile(subfolder)
            ?: baseDir.createDirectory(subfolder)
        return target?.uri
    }

    fun old_renameAllJpgsWithSAF(context: Context, folderUri: Uri) {
        val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return
        val jpgs = dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".jpg", true) == true }
            .sortedBy { it.name!!.lowercase() }

        jpgs.forEachIndexed { index, doc ->
            val newName = "%05d.jpg".format(index)
            if (doc.name != newName) {
                val ok = doc.renameTo(newName)
                Log.i("SAFRename", "Renamed ${doc.name} → $newName : $ok")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TREE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { treeUri ->
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(treeUri, flags)

                getSharedPreferences("saf_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("stopmotion_tree", treeUri.toString())
                    .apply()
            }
        }
    }

    fun getSavedStopMotionTreeUri(context: Context): Uri? {
        val uriStr = context
            .getSharedPreferences("saf_prefs", Context.MODE_PRIVATE)
            .getString("stopmotion_tree", null)
        return uriStr?.let { Uri.parse(it) }
    }

    companion object {
        private const val REQUEST_TREE = 222
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
