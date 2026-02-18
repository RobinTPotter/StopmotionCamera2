package com.robin.stopmotioncamera2.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log


fun updateOnionSkins(
    context: Context,
    savedImages: MutableList<Uri?>,
    skins: Int = 3,
    showCrosshair: Boolean = true,
    showThirds: Boolean = false,
    opacityStart: Float = 0.5f,
    opacityEnd: Float = 0.35f
): Bitmap {
    val resultBitmap: Bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
    val c = Canvas(resultBitmap)
    
    Log.i("ImageUtils", "updateOnionSkins called with ${savedImages.size} images, skins=$skins")
    
    // savedImages is in DESC order (newest first)
    // Draw oldest to newest with gentle alpha increase
    
    try {
        val numToDraw = minOf(savedImages.size, skins)
        
        for (i in 0 until numToDraw) {
            // Calculate index in savedImages (newest first list)
            val imageIndex = numToDraw - 1 - i
            
            // Calculate alpha with gradient from opacityEnd to opacityStart
            val alphaValue = if (numToDraw == 1) {
                opacityStart
            } else {
                opacityEnd + (opacityStart - opacityEnd) * i / (numToDraw - 1)
            }
            
            val uri = savedImages[imageIndex]
            Log.i("ImageUtils", "Drawing layer $i: savedImages[$imageIndex] with alpha=$alphaValue, uri=$uri")
            
            val inputStream = uri?.let { context.contentResolver.openInputStream(it) }
            val bm = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bm != null) {
                val paint = Paint().apply {
                    this.alpha = (alphaValue * 255).toInt()
                    isFilterBitmap = true
                }
                c.drawBitmap(bm, 0f, 0f, paint)
                Log.i("ImageUtils", "Successfully drew bitmap: alpha=${(alphaValue * 255).toInt()}/255")
            } else {
                Log.w("ImageUtils", "Failed to decode bitmap for index $imageIndex")
            }
        }
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error updating onion skins: ${e.message}", e)
    }
    
    // Draw guides
    val p = Paint(Color.WHITE)
    p.strokeWidth = 2f
    p.alpha = 180  // Slightly transparent
    
    if (showCrosshair) {
        // Draw crosshair
        c.drawLine(1920f / 2, 0.0f, 1920f / 2, 1080.0f, p)
        c.drawLine(0f, 1080f / 2, 1920f, 1080.0f / 2, p)
    }
    
    if (showThirds) {
        // Draw rule of thirds grid
        // Vertical lines at 1/3 and 2/3
        c.drawLine(1920f / 3, 0.0f, 1920f / 3, 1080.0f, p)
        c.drawLine(1920f * 2 / 3, 0.0f, 1920f * 2 / 3, 1080.0f, p)
        // Horizontal lines at 1/3 and 2/3
        c.drawLine(0f, 1080f / 3, 1920f, 1080f / 3, p)
        c.drawLine(0f, 1080f * 2 / 3, 1920f, 1080f * 2 / 3, p)
    }
    
    Log.i("ImageUtils", "Onion skin composite complete")
    return resultBitmap
}
