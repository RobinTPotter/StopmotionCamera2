package com.robin.stopmotioncamera2

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
import com.robin.stopmotioncamera2.utils.saveImageToPublicPictures
import com.robin.stopmotioncamera2.utils.updateOnionSkins
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var onionSkinView: ImageView
    private lateinit var imageCapture: ImageCapture
    private lateinit var label: TextView
    private var treeUri: Uri? = null
    private var savedImages: MutableList<Uri?> = mutableListOf()
    private var currentScene: Int = 0
    private var onionSkins: Int = 2

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        treeUri = getSavedStopMotionTreeUri(this@MainActivity)

        if (treeUri==null) {
            launchStopMotionPicker(this)
        }


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

                    val sub = outputFolder(currentScene, false)

//                    MediaScannerConnection.scanFile(
//                        this@MainActivity,
//                        arrayOf(File(Environment.getExternalStorageDirectory(), "Pictures/$sub").toString()),
//                        null, //arrayOf("image/jpeg"), // or null for all types
//                        null
//                    )
                    if (treeUri==null) {
                        treeUri = getSavedStopMotionTreeUri(this@MainActivity)
                    }
                    var subUri =
                        treeUri?.let { getSubfolderUriFromTreeUri(this@MainActivity, it, sub) }

                    val thread = Thread() {
                      //  lifecycleScope.launch {
                            if (subUri != null) {
                                renameAllJpgsWithSAF(this@MainActivity, subUri)
                            }
                        Thread.sleep(1000)
                    //    }
                    }
                    thread.start()
                    thread.join()

                    val sub2 = outputFolder(currentScene)


                    val next = nextFile(this@MainActivity, sub2)
                    val uri = saveImageToPublicPictures(
                        this@MainActivity, temp, sub2, next
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
        private const val REQUEST_TREE = 222     // any number you like
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun launchStopMotionPicker(activity: Activity) {
        val pictures = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val stopMotionDir = File(pictures, "StopMotion")

        // Convert that real path to a content URI that SAF understands
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

        // Navigate or create the subfolder
        val target = baseDir.findFile(subfolder)
            ?: baseDir.createDirectory(subfolder)

        return target?.uri
    }


     fun renameAllJpgsWithSAF(context: Context, folderUri: Uri)  {

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


                // 2. save it for later
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


}


