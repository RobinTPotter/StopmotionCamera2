package com.robin.stopmotioncamera2.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Removes all MediaStore entries under Pictures/StopMotion and rescans files
 * PROPERLY WAITS for scanning to complete
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
        val jpgFiles = mutableListOf<File>()
        stopMotionDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "jpg" }
            .forEach { jpgFiles.add(it) }
        
        Log.i("MediaStoreRebuild", "Found ${jpgFiles.size} JPG files to scan")
        
        if (jpgFiles.isEmpty()) {
            Log.i("MediaStoreRebuild", "No files to scan")
            return@withContext
        }
        
        // Step 3: Scan each file ONE BY ONE and wait for completion
        var scannedCount = 0
        for ((index, file) in jpgFiles.withIndex()) {
            try {
                scanFileSuspend(context, file.absolutePath, "image/jpeg")
                scannedCount++
                if ((index + 1) % 10 == 0 || index == jpgFiles.size - 1) {
                    Log.i("MediaStoreRebuild", "Progress: ${index + 1}/${jpgFiles.size}")
                }
            } catch (e: Exception) {
                Log.e("MediaStoreRebuild", "Failed to scan ${file.name}: ${e.message}")
            }
        }
        
        Log.i("MediaStoreRebuild", "MediaStore rebuild complete: $scannedCount files scanned")
        
    } catch (e: Exception) {
        Log.e("MediaStoreRebuild", "Error rebuilding MediaStore: ${e.message}", e)
    }
}

/**
 * Scans a single file and WAITS for completion using coroutine suspension
 */
private suspend fun scanFileSuspend(context: Context, path: String, mimeType: String): Uri? {
    return suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            arrayOf(mimeType)
        ) { scannedPath, uri ->
            if (continuation.isActive) {
                continuation.resume(uri)
            }
        }
    }
}

/**
 * Quick check if MediaStore needs rebuilding
 * Uses SAF to count files (sees all files, not just owned ones)
 */
suspend fun needsMediaStoreRebuild(context: Context, treeUri: Uri?): Boolean = withContext(Dispatchers.IO) {
    try {
        if (treeUri == null) {
            Log.i("MediaStoreCheck", "No SAF access")
            return@withContext false
        }
        
        // Count files using SAF (sees ALL files, even from other apps)
        val baseDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
        var filesOnDisk = 0
        baseDir.listFiles().forEach { subfolder ->
            if (subfolder.isDirectory) {
                filesOnDisk += subfolder.listFiles()
                    .count { it.isFile && it.name?.endsWith(".jpg", true) == true }
            }
        }
        
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
        
        Log.i("MediaStoreCheck", "Files on disk (SAF): $filesOnDisk, in MediaStore: $filesInMediaStore, needs rebuild: $needsRebuild")
        
        return@withContext needsRebuild
        
    } catch (e: Exception) {
        Log.e("MediaStoreCheck", "Error checking: ${e.message}", e)
        return@withContext false
    }
}
