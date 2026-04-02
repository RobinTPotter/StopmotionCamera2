package com.robin.stopmotioncamera2.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import java.io.File

/**
 * Composites the most recent [skins] frames into a single onion-skin bitmap.
 *
 * [frames] is the full sorted frame list (oldest → newest).
 * The last [skins] entries are drawn oldest-first with increasing opacity so
 * the most recent frame appears most prominently.
 */
fun updateOnionSkins(
    frames: List<File>,
    skins: Int = 3,
    showCrosshair: Boolean = true,
    showThirds: Boolean = false,
    opacityStart: Float = 0.5f,   // opacity of the most-recent frame
    opacityEnd: Float = 0.35f     // opacity of the oldest frame in the window
): Bitmap {
    val result = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    val numToDraw = minOf(frames.size, skins)
    if (numToDraw == 0) return result

    // Take the last N frames (oldest of the recent window first)
    val window = frames.takeLast(numToDraw)

    Log.i("ImageUtils", "updateOnionSkins: drawing $numToDraw / ${frames.size} frames")

    window.forEachIndexed { i, file ->
        // i=0 is the oldest in the window → opacityEnd; i=last → opacityStart
        val alpha = if (numToDraw == 1) opacityStart
        else opacityEnd + (opacityStart - opacityEnd) * i.toFloat() / (numToDraw - 1)

        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: run {
            Log.w("ImageUtils", "Could not decode ${file.name}")
            return@forEachIndexed
        }

        val paint = Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isFilterBitmap = true
        }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        bmp.recycle()
        Log.i("ImageUtils", "Drew ${file.name} alpha=${(alpha * 255).toInt()}")
    }

    // Guides
    val guide = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        this.alpha = 180
    }
    if (showCrosshair) {
        canvas.drawLine(960f, 0f, 960f, 1080f, guide)
        canvas.drawLine(0f, 540f, 1920f, 540f, guide)
    }
    if (showThirds) {
        canvas.drawLine(640f, 0f, 640f, 1080f, guide)
        canvas.drawLine(1280f, 0f, 1280f, 1080f, guide)
        canvas.drawLine(0f, 360f, 1920f, 360f, guide)
        canvas.drawLine(0f, 720f, 1920f, 720f, guide)
    }

    Log.i("ImageUtils", "Onion skin composite complete")
    return result
}
