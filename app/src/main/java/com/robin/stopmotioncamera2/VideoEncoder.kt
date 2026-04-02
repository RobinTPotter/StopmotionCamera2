package com.robin.stopmotioncamera2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.robin.stopmotioncamera2.utils.saveVideoToPublicPictures
import java.io.File

/**
 * Encodes a list of JPEG frame files into an H.264 MP4 using [MediaCodec] + [MediaMuxer].
 *
 * The resulting video is written to a temp file in [Context.cacheDir], then copied to
 * Pictures/StopMotion/ via MediaStore so it is visible in the gallery.
 *
 * All work is synchronous — call from a background coroutine / thread.
 */
object VideoEncoder {

    private const val TAG          = "VideoEncoder"
    private const val MIME         = "video/avc"
    private const val TIMEOUT_US   = 10_000L
    private const val BIT_RATE     = 8_000_000   // 8 Mbps
    private const val I_INTERVAL   = 1           // keyframe every second

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * @param frames     Ordered list of JPEG files (first → last frame).
     * @param scene      Scene number — used only to name the output file.
     * @param fps        Playback frame rate (default 12).
     * @param onProgress Called on the calling thread with (framesEncoded, totalFrames).
     * @param onComplete Called on the calling thread with the MediaStore Uri, or null on failure.
     */
    fun encode(
        context: Context,
        frames: List<File>,
        scene: Int,
        fps: Int = 12,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (Uri?) -> Unit = {}
    ) {
        if (frames.isEmpty()) {
            Log.w(TAG, "encode() called with empty frame list")
            onComplete(null)
            return
        }

        // Probe dimensions from first frame (no full decode)
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(frames[0].absolutePath, probe)
        val width  = (probe.outWidth  / 2) * 2   // must be divisible by 2 for H.264
        val height = (probe.outHeight / 2) * 2

        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Could not read dimensions from ${frames[0].name}")
            onComplete(null)
            return
        }

        Log.i(TAG, "Encoding ${frames.size} frames at ${width}×${height} @ ${fps}fps")

        val tempFile = File(context.cacheDir, "scene_%03d_export.mp4".format(scene))
        tempFile.delete()

        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE,        BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE,      fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_INTERVAL)
        }

        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufInfo        = MediaCodec.BufferInfo()
        var trackIndex     = -1
        var muxerStarted   = false
        val frameDurationUs = 1_000_000L / fps
        var presentationUs  = 0L

        try {
            frames.forEachIndexed { i, file ->
                onProgress(i + 1, frames.size)

                var bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp == null) {
                    Log.w(TAG, "Skipping unreadable frame: ${file.name}")
                    return@forEachIndexed
                }
                // Scale if this frame differs from the first (shouldn't happen normally)
                if (bmp.width != width || bmp.height != height) {
                    val scaled = Bitmap.createScaledBitmap(bmp, width, height, true)
                    bmp.recycle()
                    bmp = scaled
                }

                // Feed frame to encoder
                val inId = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inId >= 0) {
                    val img = codec.getInputImage(inId)
                    if (img != null) {
                        // Preferred path: Image API handles NV12 / I420 stride automatically
                        writeBitmapToImage(bmp, img)
                    } else {
                        // Fallback: raw NV12 ByteBuffer
                        codec.getInputBuffer(inId)!!.apply {
                            clear()
                            put(bitmapToNv12(bmp, width, height))
                        }
                    }
                    bmp.recycle()
                    codec.queueInputBuffer(inId, 0, width * height * 3 / 2, presentationUs, 0)
                    presentationUs += frameDurationUs
                } else {
                    bmp.recycle()
                    Log.w(TAG, "No encoder input buffer available for frame $i")
                }

                // Drain encoded output (non-blocking)
                val r = drain(codec, muxer, bufInfo, trackIndex, muxerStarted, endOfStream = false)
                trackIndex   = r.first
                muxerStarted = r.second
            }

            // Signal end of stream and drain remaining output
            val inId = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inId >= 0) {
                codec.queueInputBuffer(inId, 0, 0, presentationUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            val r = drain(codec, muxer, bufInfo, trackIndex, muxerStarted, endOfStream = true)
            trackIndex   = r.first
            muxerStarted = r.second

        } catch (e: Exception) {
            Log.e(TAG, "Encoding error: ${e.message}", e)
            runCatching { codec.stop(); codec.release() }
            runCatching { if (muxerStarted) muxer.stop(); muxer.release() }
            tempFile.delete()
            onComplete(null)
            return
        }

        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }

        val outputName = "stopmotion_%03d_%d.mp4".format(scene, System.currentTimeMillis() / 1000)
        val uri = saveVideoToPublicPictures(context, tempFile, outputName)
        tempFile.delete()

        Log.i(TAG, if (uri != null) "Export complete → $uri" else "Export failed (null uri)")
        onComplete(uri)
    }

    // ── Drain encoder output ──────────────────────────────────────────────────

    /**
     * Pulls encoded buffers from [codec] and writes them to [muxer].
     * Returns the (possibly updated) trackIndex and muxerStarted flag.
     */
    private fun drain(
        codec: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        trackIdx: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean
    ): Pair<Int, Boolean> {
        var track   = trackIdx
        var started = muxerStarted

        while (true) {
            when (val outId = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {

                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!started) { "Format changed after muxer started" }
                    track   = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                    Log.d(TAG, "Muxer started, track=$track")
                }

                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output ready yet
                    if (!endOfStream) break
                    // If we're waiting for EOS keep spinning (but yield slightly)
                }

                else -> {
                    if (outId < 0) break   // unexpected negative id

                    val isCodecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val isEos         = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM  != 0

                    if (!isCodecConfig && info.size > 0 && started) {
                        val buf = codec.getOutputBuffer(outId)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }

                    codec.releaseOutputBuffer(outId, false)
                    if (isEos) break
                }
            }
        }
        return Pair(track, started)
    }

    // ── Pixel conversion ──────────────────────────────────────────────────────

    /**
     * Writes [bitmap] pixels into a [MediaCodec] input [Image].
     * Handles both NV12 (pixelStride=2) and I420 (pixelStride=1) via plane strides.
     */
    private fun writeBitmapToImage(bitmap: Bitmap, image: Image) {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf      = yPlane.buffer
        val uBuf      = uPlane.buffer
        val vBuf      = vPlane.buffer
        val yStride   = yPlane.rowStride
        val uvStride  = uPlane.rowStride
        val uvPxStride = uPlane.pixelStride

        // Y plane
        for (row in 0 until h) {
            for (col in 0 until w) {
                val p = argb[row * w + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8)  and 0xFF
                val b =  p         and 0xFF
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235)
                yBuf.put(row * yStride + col, y.toByte())
            }
        }

        // UV planes (one sample per 2×2 block)
        for (row in 0 until h / 2) {
            for (col in 0 until w / 2) {
                val p = argb[(row * 2) * w + (col * 2)]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8)  and 0xFF
                val b =  p         and 0xFF
                val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240)
                val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240)
                val pos = row * uvStride + col * uvPxStride
                uBuf.put(pos, u.toByte())
                vBuf.put(pos, v.toByte())
            }
        }
    }

    /**
     * Fallback raw NV12 (semi-planar YUV420) byte array for devices where
     * [MediaCodec.getInputImage] returns null.
     */
    private fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIdx  = 0
        var uvIdx = width * height

        for (row in 0 until height) {
            for (col in 0 until width) {
                val p = argb[row * width + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8)  and 0xFF
                val b =  p         and 0xFF
                yuv[yIdx++] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                    .coerceIn(0, 255).toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    yuv[uvIdx++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
                        .coerceIn(0, 255).toByte()
                    yuv[uvIdx++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
                        .coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
