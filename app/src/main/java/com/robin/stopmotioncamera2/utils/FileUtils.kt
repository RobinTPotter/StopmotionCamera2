// Add these SAF functions to your existing FileUtils.kt

package com.robin.stopmotioncamera2.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * Get last N images using SAF instead of MediaStore
 * This works even if files are "owned" by a previous app installation
 */
fun getLastImagesByNameWithSAF(context: Context, folderUri: Uri, numImages: Int): MutableList<Uri?> {
    val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return mutableListOf()
    
    val jpgs = dir.listFiles()
        .filter { it.isFile && it.name?.endsWith(".jpg", true) == true }
        .sortedByDescending { it.name!!.lowercase() }  // DESC order (newest first)
        .take(numImages)
    
    val imageUris = jpgs.map { it.uri }.toMutableList()
    
    Log.i("FileUtils", "getLastImagesByNameWithSAF: Found ${imageUris.size} images")
    return imageUris
}

/**
 * Count images using SAF
 */
fun countImagesWithSAF(context: Context, folderUri: Uri): Int {
    val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return 0
    val count = dir.listFiles()
        .count { it.isFile && it.name?.endsWith(".jpg", true) == true }
    Log.i("FileUtils", "countImagesWithSAF: $count images")
    return count
}

/**
 * Get next filename using SAF
 */
fun nextFileWithSAF(context: Context, folderUri: Uri): String {
    val count = countImagesWithSAF(context, folderUri)
    return String.format("%05d.jpg", count)
}
