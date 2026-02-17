package com.robin.stopmotioncamera2.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log


fun updateOnionSkins(context: Context, savedImages: MutableList<Uri?>, skins: Int = 3): Bitmap {
    val resultBitmap: Bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
    val c = Canvas(resultBitmap)
    
    Log.i("ImageUtils", "updateOnionSkins called with ${savedImages.size} images, skins=$skins")
    
    // savedImages is in DESC order (newest first)
    // Draw oldest to newest with gentle alpha increase
    
    try {
        val numToDraw = minOf(savedImages.size, skins)
        
        // Gentle gradient: oldest at 0.35, newest at 0.5
        val minAlpha = 0.35f
        val maxAlpha = 0.5f
        
        for (i in 0 until numToDraw) {
            // Calculate index in savedImages (newest first list)
            val imageIndex = numToDraw - 1 - i
            
            // Calculate alpha with gentle gradient
            // For 2 skins: [0.35, 0.5]
            // For 3 skins: [0.35, 0.425, 0.5]
            val alphaValue = if (numToDraw == 1) {
                maxAlpha
            } else {
                minAlpha + (maxAlpha - minAlpha) * i / (numToDraw - 1)
            }
            
            val uri = savedImages[imageIndex]
            Log.i("ImageUtils", "Drawing layer $i: savedImages[$imageIndex] with alpha=$alphaValue (${(alphaValue * 100).toInt()}%), uri=$uri")
            
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
    
    // Draw crosshairs
    val p = Paint(Color.BLACK)
    p.strokeWidth = 3f
    c.drawLine(1920f / 2, 0.0f, 1920f / 2, 1080.0f, p)
    c.drawLine(0f, 1080f / 2, 1920f, 1080.0f / 2, p)
    
    Log.i("ImageUtils", "Onion skin composite complete")
    return resultBitmap
}
