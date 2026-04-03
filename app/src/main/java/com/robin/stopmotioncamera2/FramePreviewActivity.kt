package com.robin.stopmotioncamera2

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.robin.stopmotioncamera2.utils.deleteFrame
import com.robin.stopmotioncamera2.utils.getFrameFiles
import com.robin.stopmotioncamera2.utils.moveFrameLeft
import com.robin.stopmotioncamera2.utils.moveFrameRight
import com.robin.stopmotioncamera2.utils.renameFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FramePreviewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var frameCounter: TextView
    private lateinit var playButton: Button

    private var frames: MutableList<File> = mutableListOf()
    private var lowResFrames: MutableList<Bitmap?> = mutableListOf()
    private var currentFrame = 0
    private var isPlaying = false
    private var fps = 12
    private var sceneNumber = 0

    private val handler = Handler(Looper.getMainLooper())
    private val playRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && frames.isNotEmpty()) {
                currentFrame = (currentFrame + 1) % frames.size
                seekBar.progress = currentFrame
                showFrame(currentFrame)
                handler.postDelayed(this, 1000L / fps)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frame_preview)

        sceneNumber = intent.getIntExtra("SCENE_NUMBER", 0)

        imageView    = findViewById(R.id.previewImageView)
        seekBar      = findViewById(R.id.frameSeekBar)
        frameCounter = findViewById(R.id.frameCounter)
        playButton   = findViewById(R.id.playButton)

        // FPS buttons
        mapOf(R.id.fps8Button to 8, R.id.fps12Button to 12, R.id.fps24Button to 24)
            .forEach { (id, value) ->
                findViewById<Button>(id).setOnClickListener { fps = value }
            }

        // Playback
        playButton.setOnClickListener { togglePlayback() }
        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }

        // Edit buttons
        findViewById<Button>(R.id.moveLeftButton).setOnClickListener  { onMoveLeft() }
        findViewById<Button>(R.id.moveRightButton).setOnClickListener { onMoveRight() }
        findViewById<Button>(R.id.deleteButton).setOnClickListener    { confirmDelete() }
        findViewById<Button>(R.id.exportButton).setOnClickListener    { onExport() }

        // Seekbar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { currentFrame = progress; showFrame(currentFrame) }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { stopPlayback() }
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        loadFrames()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(playRunnable)
        lowResFrames.forEach { it?.recycle() }
        lowResFrames.clear()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Reads frames from disk (IO) then decodes low-res thumbnails (IO). */
    private fun loadFrames() {
        lifecycleScope.launch {
            frames = withContext(Dispatchers.IO) {
                getFrameFiles(this@FramePreviewActivity, sceneNumber).toMutableList()
            }
            if (frames.isEmpty()) {
                frameCounter.text = "No frames in scene $sceneNumber"
                return@launch
            }
            lowResFrames = withContext(Dispatchers.IO) {
                frames.map { decodeLowRes(it) }.toMutableList()
            }
            seekBar.max = frames.size - 1
            currentFrame = currentFrame.coerceIn(0, frames.size - 1)
            showFrame(currentFrame)
            Log.i("FramePreview", "Loaded ${frames.size} frames")
        }
    }

    private fun decodeLowRes(file: File): Bitmap? = runCatching {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = 4 } // ¼ res
        )
    }.getOrElse {
        Log.e("FramePreview", "Decode error for ${file.name}: ${it.message}")
        null
    }

    // ── Display ───────────────────────────────────────────────────────────────

    private fun showFrame(index: Int) {
        if (index !in 0 until lowResFrames.size) return
        lowResFrames[index]?.let { imageView.setImageBitmap(it) }
        frameCounter.text = "Frame ${index + 1} / ${frames.size}   ${frames[index].name}"
        seekBar.progress = index
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun togglePlayback() {
        isPlaying = !isPlaying
        playButton.text = if (isPlaying) "⏸ Pause" else "▶ Play"
        if (isPlaying) handler.post(playRunnable)
        else handler.removeCallbacks(playRunnable)
    }

    private fun stopPlayback() {
        if (isPlaying) togglePlayback()
    }

    // ── Edit: Delete ──────────────────────────────────────────────────────────

    private fun confirmDelete() {
        if (frames.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete frame")
            .setMessage("Delete frame ${currentFrame + 1} (${frames[currentFrame].name})?")
            .setPositiveButton("Delete") { _, _ -> doDelete() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doDelete() {
        if (frames.isEmpty()) return
        stopPlayback()
        val target = frames[currentFrame]
        // Remove from memory immediately for snappy UI
        lowResFrames[currentFrame]?.recycle()
        frames.removeAt(currentFrame)
        lowResFrames.removeAt(currentFrame)
        currentFrame = currentFrame.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        seekBar.max = (frames.size - 1).coerceAtLeast(0)
        if (frames.isEmpty()) frameCounter.text = "No frames"
        else showFrame(currentFrame)

        // Delete file and renumber on disk in background
        lifecycleScope.launch(Dispatchers.IO) {
            deleteFrame(this@FramePreviewActivity, target, sceneNumber)
            // Reload file references so names match disk after renumber
            val refreshed = getFrameFiles(this@FramePreviewActivity, sceneNumber)
            withContext(Dispatchers.Main) {
                frames.clear()
                frames.addAll(refreshed)
                Toast.makeText(this@FramePreviewActivity, "Frame deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Edit: Move left / right ───────────────────────────────────────────────

    private fun onMoveLeft() {
        if (frames.size < 2 || currentFrame <= 0) return
        stopPlayback()
        lifecycleScope.launch(Dispatchers.IO) {
            val newIndex = moveFrameLeft(this@FramePreviewActivity, currentFrame, sceneNumber)
            withContext(Dispatchers.Main) {
                frames.swap(currentFrame, newIndex)
                lowResFrames.swap(currentFrame, newIndex)
                currentFrame = newIndex
                showFrame(currentFrame)
            }
        }
    }

    private fun onMoveRight() {
        if (frames.size < 2 || currentFrame >= frames.size - 1) return
        stopPlayback()
        lifecycleScope.launch(Dispatchers.IO) {
            val newIndex = moveFrameRight(this@FramePreviewActivity, currentFrame, sceneNumber)
            withContext(Dispatchers.Main) {
                frames.swap(currentFrame, newIndex)
                lowResFrames.swap(currentFrame, newIndex)
                currentFrame = newIndex
                showFrame(currentFrame)
            }
        }
    }

    // ── Edit: Rename ──────────────────────────────────────────────────────────

    private fun onRename() {
        if (frames.isEmpty()) return
        stopPlayback()
        val current = frames[currentFrame]
        val input = EditText(this).apply {
            setText(current.nameWithoutExtension)
            setSingleLine(true)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Rename frame ${currentFrame + 1}")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                val renamed = renameFrame(current, newName)
                frames[currentFrame] = renamed
                showFrame(currentFrame)
                Toast.makeText(this, "→ ${renamed.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Export: MP4 ──────────────────────────────────────────────────────────

    private fun onExport() {
        if (frames.isEmpty()) {
            Toast.makeText(this, "No frames to export", Toast.LENGTH_SHORT).show()
            return
        }
        stopPlayback()
        Toast.makeText(this, "Encoding MP4 (${frames.size} frames @ ${fps}fps)…", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            VideoEncoder.encode(
                context  = this@FramePreviewActivity,
                frames   = frames.toList(),
                scene    = sceneNumber,
                fps      = fps,
                onProgress = { cur, total ->
                    Log.i("FramePreview", "Encoding frame $cur / $total")
                },
                onComplete = { uri ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val msg = if (uri != null) "✓ MP4 saved to Pictures/StopMotion"
                                  else "✗ Export failed"
                        Toast.makeText(this@FramePreviewActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> MutableList<T>.swap(a: Int, b: Int) {
        val tmp = this[a]; this[a] = this[b]; this[b] = tmp
    }
}
