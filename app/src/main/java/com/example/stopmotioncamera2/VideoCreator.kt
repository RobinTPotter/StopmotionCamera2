package com.example.stopmotioncamera2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.jcodec.api.android.AndroidSequenceEncoder
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.Rational
import java.io.File


class VideoCreator {

    public  fun encode(dir: File) {

        val file : File =  File(dir, "test.mp4")
        val out = NIOUtils.writableFileChannel(file.absolutePath)

        try {
            val encoder = AndroidSequenceEncoder(out, Rational.R(15, 1));
            dir.list().forEach {
                val im = File(dir,it)
                val bitmap = BitmapFactory.decodeFile(im.toString())
                val a = 1.0f * bitmap.height/ bitmap.width

                val b2 = Bitmap.createScaledBitmap(bitmap,640, (640*a).toInt(),false)
                encoder.encodeImage(b2)
                Log.i("Encode", "Encoding image %s".format(File(it).absolutePath))
            }

            encoder.finish();
        } catch (e:Exception) {
            Log.e("Encode", "ERROR %s".format(e))
        } finally {
            NIOUtils.closeQuietly(out);
        }
    }
}
