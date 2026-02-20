package com.robin.stopmotioncamera2.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Removes all MediaStore entries under Pictures/StopMotion and rescans files
 * This "adopts" orphaned files from previous app installations
 */
suspend fun rebuildMediaStoreForStopMotion(context: Context) = withContext(Dispatchers.IO) {
    try {
        Log.i("MediaStoreRebuild", "Starting MediaStore rebuild for StopMotion")
        
        // Step 1: Delete all MediaStore entries for Pictures/StopMotion
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%StopMotion%")
        
        val deletedCount = context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            selection,
            selectionArgs
        )
        
        Log.i("MediaStoreRebuild", "Deleted $deletedCount MediaStore entries")
        
        // Step 2: Find all actual JPG files on disk
        val stopMotionDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            ),
            "StopMotion"
        )
        
        if (!stopMotionDir.exists()) {
            Log.i("MediaStoreRebuild", "StopMotion directory doesn't exist yet")
            return@withContext
        }
        
        // Recursively find all JPG files
        val jpgFiles = mutableListOf<String>()
        stopMotionDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "jpg" }
            .forEach { jpgFiles.add(it.absolutePath) }
        
        Log.i("MediaStoreRebuild", "Found ${jpgFiles.size} JPG files to scan")
        
        if (jpgFiles.isEmpty()) {
            Log.i("MediaStoreRebuild", "No files to scan")
            return@withContext
        }
        
        // Step 3: Scan all files to add them back to MediaStore
        var scannedCount = 0
        MediaScannerConnection.scanFile(
            context,
            jpgFiles.toTypedArray(),
            arrayOf("image/jpeg"),
            { path, uri ->
                scannedCount++
                Log.d("MediaStoreRebuild", "Scanned [$scannedCount/${jpgFiles.size}]: $path")
            }
        )
        
        Log.i("MediaStoreRebuild", "MediaStore rebuild complete: $scannedCount files scanned")
        
    } catch (e: Exception) {
        Log.e("MediaStoreRebuild", "Error rebuilding MediaStore: ${e.message}", e)
    }
}

/**
 * Quick check if MediaStore needs rebuilding
 * Returns true if there are files on disk but not in MediaStore
 */
fun needsMediaStoreRebuild(context: Context): Boolean {
    try {
        // Count files on disk
        val stopMotionDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            ),
            "StopMotion"
        )
        
        if (!stopMotionDir.exists()) return false
        
        val filesOnDisk = stopMotionDir.walkTopDown()
            .count { it.isFile && it.extension.lowercase() == "jpg" }
        
        // Count files in MediaStore
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%StopMotion%")
        val projection = arrayOf(MediaStore.Images.Media._ID)
        
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        
        val filesInMediaStore = cursor?.use { it.count } ?: 0
        cursor?.close()
        
        val needsRebuild = filesOnDisk > filesInMediaStore
        
        Log.i("MediaStoreCheck", "Files on disk: $filesOnDisk, in MediaStore: $filesInMediaStore, needs rebuild: $needsRebuild")
        
        return needsRebuild
        
    } catch (e: Exception) {
        Log.e("MediaStoreCheck", "Error checking: ${e.message}", e)
        return false
    }
}
