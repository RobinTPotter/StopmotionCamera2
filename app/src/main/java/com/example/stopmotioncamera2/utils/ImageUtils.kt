package com.example.stopmotioncamera2.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File




fun updateOnionSkins(savedImages: MutableList<File>): Bitmap {
    val skins = 2
    val startalpha = 0.6f
    var alpha = startalpha
    var resultBitmap: Bitmap = Bitmap.createBitmap( 1920,1080,Bitmap.Config.ARGB_8888   )

    val c: Canvas =  Canvas(resultBitmap)
    for (si in savedImages.size - skins - 1 until (savedImages.size)) {
        if (si < 0) continue
        val bm = BitmapFactory.decodeFile(savedImages[si].absolutePath)

        // Set up the paint with the desired alpha
        val paint = Paint().apply {
            this.alpha = (alpha * 255).toInt()  // 50% alpha = 127
            alpha -= (startalpha / (skins + 1))
            isFilterBitmap = true
        }
        // Draw the overlay bitmap on top of the base bitmap
      if (bm !=null)  c.drawBitmap(bm, 0f, 0f, paint)

    }

    val p : Paint=  Paint(Color.BLACK)
    p.strokeWidth = 3f



    c.drawLine( 1920f/2, 0.0f, 1920f/2,1080.0f,p)
    c.drawLine( 0f, 1080f/2, 1920f,1080.0f/2,p)
    return resultBitmap

}
