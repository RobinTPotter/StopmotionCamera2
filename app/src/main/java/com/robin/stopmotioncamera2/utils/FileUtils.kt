package com.robin.stopmotioncamera2.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// ── Scene directory ─────────────────────────────────────────────────────────────

/**
 * Returns (and creates if needed) the app-private external directory for a scene.
 * Path: /sdcard/Android/data/<package>/files/scenes/NNN/
 * No permissions required on Android 10+. Not scanned by MediaStore.
 */
fun getSceneDir(context: Context, scene: Int): File =
    File(context.getExternalFilesDir(null), "scenes/%03d".format(scene))
        .also { if (!it.exists()) it.mkdirs() }

// ── Frame listing ───────────────────────────────────────────────────────────────

/**
 * Returns all JPG frames for a scene sorted by filename (00000.jpg … 99999.jpg).
 * Instant — no MediaStore query involved.
 */
fun getFrameFiles(context: Context, scene: Int): List<File> =
    getSceneDir(context, scene)
        .listFiles { f -> f.extension.equals("jpg", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?: emptyList()

// ── Save a new frame ────────────────────────────────────────────────────────────

/**
 * Compresses [bitmap] to JPEG and appends it to the scene folder.
 * Filename is the next sequential number (e.g. 00007.jpg).
 */
fun saveFrame(context: Context, bitmap: Bitmap, scene: Int): File {
    val dir = getSceneDir(context, scene)
    val next = "%05d.jpg".format(getFrameFiles(context, scene).size)
    val file = File(dir, next)
    try {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        Log.i("FileUtils", "saveFrame → ${file.name}")
    } catch (e: IOException) {
        Log.e("FileUtils", "saveFrame failed: ${e.message}")
    }
    return file
}

// ── Delete ──────────────────────────────────────────────────────────────────────

/**
 * Deletes [file] then renumbers the remaining frames so the sequence stays tight.
 */
fun deleteFrame(context: Context, file: File, scene: Int) {
    if (file.delete()) {
        Log.i("FileUtils", "Deleted ${file.name}")
        renumberFrames(context, scene)
    } else {
        Log.e("FileUtils", "Could not delete ${file.name}")
    }
}

// ── Reorder ─────────────────────────────────────────────────────────────────────

/** Swaps the frame at [index] with the one before it. Returns the new index. */
fun moveFrameLeft(context: Context, index: Int, scene: Int): Int {
    if (index <= 0) return index
    swapFrameFiles(context, index - 1, index, scene)
    return index - 1
}

/** Swaps the frame at [index] with the one after it. Returns the new index. */
fun moveFrameRight(context: Context, index: Int, scene: Int): Int {
    val size = getFrameFiles(context, scene).size
    if (index >= size - 1) return index
    swapFrameFiles(context, index, index + 1, scene)
    return index + 1
}

private fun swapFrameFiles(context: Context, a: Int, b: Int, scene: Int) {
    val frames = getFrameFiles(context, scene)
    val fa = frames[a]
    val fb = frames[b]
    val tmp = File(fa.parent, "__swap__.jpg")
    fa.renameTo(tmp)
    fb.renameTo(fa)
    tmp.renameTo(fb)
    Log.i("FileUtils", "Swapped ${fa.name} ↔ ${fb.name}")
}

// ── Renumber ────────────────────────────────────────────────────────────────────

/**
 * Renames all JPGs in the scene folder to a tight 00000.jpg … sequence.
 * Two-pass approach avoids rename collisions when e.g. 00001 → 00000 while
 * 00000 still exists.
 */
fun renumberFrames(context: Context, scene: Int) {
    val dir = getSceneDir(context, scene)
    val files = dir.listFiles { f ->
        f.extension.equals("jpg", ignoreCase = true) && f.name != "__swap__.jpg"
    }?.sortedBy { it.name } ?: return

    // Pass 1 → temporary names
    files.forEachIndexed { i, f ->
        f.renameTo(File(dir, "tmp_%05d.jpg".format(i)))
    }
    // Pass 2 → final names
    dir.listFiles { f -> f.name.startsWith("tmp_") }
        ?.sortedBy { it.name }
        ?.forEachIndexed { i, f ->
            f.renameTo(File(dir, "%05d.jpg".format(i)))
        }
    Log.i("FileUtils", "renumberFrames: scene $scene → ${files.size} frames")
}

// ── Rename ──────────────────────────────────────────────────────────────────────

/**
 * Renames a single frame file. [newBaseName] should NOT include the .jpg extension.
 * Invalid characters are replaced with underscores.
 * Returns the renamed File (or the original if rename failed).
 */
fun renameFrame(file: File, newBaseName: String): File {
    val safe = newBaseName.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    if (safe.isEmpty()) return file
    val dest = File(file.parent, "$safe.jpg")
    return if (file.renameTo(dest)) {
        Log.i("FileUtils", "Renamed ${file.name} → ${dest.name}")
        dest
    } else {
        Log.e("FileUtils", "Failed to rename ${file.name}")
        file
    }
}

// ── Permissions ─────────────────────────────────────────────────────────────────

//fun hasCameraPermission(context: Context): Boolean =
//    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
//            PackageManager.PERMISSION_GRANTED

// ── Save final MP4 to public Pictures/StopMotion ────────────────────────────────

/**
 * Copies [source] MP4 into the public Pictures/StopMotion folder via MediaStore.
 * This is the ONLY place in the new architecture that touches MediaStore.
 */
fun saveVideoToPublicPictures(context: Context, source: File, displayName: String): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StopMotion")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    try {
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        Log.i("FileUtils", "Video saved to public Pictures: $displayName")
    } catch (e: IOException) {
        Log.e("FileUtils", "saveVideoToPublicPictures failed: ${e.message}")
        resolver.delete(uri, null, null)
        return null
    }
    return uri
}
