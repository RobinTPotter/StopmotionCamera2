package com.robin.stopmotioncamera2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.robin.stopmotioncamera2.utils.getLastImagesByName
import com.robin.stopmotioncamera2.utils.outputFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FramePreviewActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var frameCounter: TextView
    private lateinit var playButton: Button
    private lateinit var closeButton: Button
    
    private var frames: MutableList<Uri?> = mutableListOf()
    private var lowResFrames: MutableList<Bitmap?> = mutableListOf()
    private var currentFrame = 0
    private var isPlaying = false
    private var fps = 12 // frames per second
    
    private val playbackHandler = Handler(Looper.getMainLooper())
    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && frames.isNotEmpty()) {
                currentFrame = (currentFrame + 1) % frames.size
                showFrame(currentFrame)
                seekBar.progress = currentFrame
                playbackHandler.postDelayed(this, 1000L / fps)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frame_preview)
        
        val sceneNumber = intent.getIntExtra("SCENE_NUMBER", 0)
        
        imageView = findViewById(R.id.previewImageView)
        seekBar = findViewById(R.id.frameSeekBar)
        frameCounter = findViewById(R.id.frameCounter)
        playButton = findViewById(R.id.playButton)
        closeButton = findViewById(R.id.closeButton)
        
        val fpsButtons = mapOf(
            R.id.fps8Button to 8,
            R.id.fps12Button to 12,
            R.id.fps24Button to 24
        )
        
        fpsButtons.forEach { (buttonId, fpsValue) ->
            findViewById<Button>(buttonId).setOnClickListener {
                fps = fpsValue
                Log.i("FramePreview", "FPS set to $fps")
            }
        }
        
        playButton.setOnClickListener {
            togglePlayback()
        }
        
        closeButton.setOnClickListener {
            finish()
        }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentFrame = progress
                    showFrame(currentFrame)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (isPlaying) togglePlayback()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Load frames in background
        loadFrames(sceneNumber)
    }
    
    private fun loadFrames(sceneNumber: Int) {
        val folderPath = outputFolder(sceneNumber)
        
        lifecycleScope.launch {
            try {
                // Get all frame URIs (in DESC order, so reverse them)
                val allFrameUris = withContext(Dispatchers.IO) {
                    getLastImagesByName(this@FramePreviewActivity, folderPath, numImages = 1000)
                }
                frames = allFrameUris.reversed().toMutableList()
                
                if (frames.isEmpty()) {
                    frameCounter.text = "No frames in scene $sceneNumber"
                    return@launch
                }
                
                // Load all frames at low resolution
                lowResFrames = withContext(Dispatchers.IO) {
                    frames.map { uri ->
                        try {
                            uri?.let { 
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 4 // 1/4 resolution (480x270 from 1920x1080)
                                }
                                val inputStream = contentResolver.openInputStream(it)
                                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                                inputStream?.close()
                                bitmap
                            }
                        } catch (e: Exception) {
                            Log.e("FramePreview", "Error loading frame: ${e.message}")
                            null
                        }
                    }.toMutableList()
                }
                
                // Setup UI
                seekBar.max = frames.size - 1
                showFrame(0)
                
                Log.i("FramePreview", "Loaded ${frames.size} frames at low resolution")
                
            } catch (e: Exception) {
                Log.e("FramePreview", "Error loading frames: ${e.message}", e)
                frameCounter.text = "Error loading frames"
            }
        }
    }
    
    private fun showFrame(index: Int) {
        if (index >= 0 && index < lowResFrames.size) {
            lowResFrames[index]?.let { bitmap ->
                imageView.setImageBitmap(bitmap)
                frameCounter.text = "Frame ${index + 1} / ${frames.size}"
            }
        }
    }
    
    private fun togglePlayback() {
        isPlaying = !isPlaying
        if (isPlaying) {
            playButton.text = "⏸ Pause"
            playbackHandler.post(playbackRunnable)
        } else {
            playButton.text = "▶ Play"
            playbackHandler.removeCallbacks(playbackRunnable)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        playbackHandler.removeCallbacks(playbackRunnable)
        // Free memory
        lowResFrames.forEach { it?.recycle() }
        lowResFrames.clear()
    }
}
