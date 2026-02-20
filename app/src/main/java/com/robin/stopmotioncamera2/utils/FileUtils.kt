package com.robin.stopmotioncamera2.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.documentfile.provider.DocumentFile


// Replace the nextFile() function in FileUtils.kt with this:

fun nextFile(context: Context, outputFolder: String): String {
    var nextNumber = countImagesInFolder(context, outputFolder)
    var filename = String.format("%05d.jpg", nextNumber)
    
    // Double-check: if this filename already exists, increment until we find an available one
    var attempts = 0
    while (fileExists(context, outputFolder, filename) && attempts < 20) {
        Log.w("FileUtils", "File $filename already exists in $outputFolder, trying next number")
        nextNumber++
        filename = String.format("%05d.jpg", nextNumber)
        attempts++
    }
    
    if (attempts > 0) {
        Log.i("FileUtils", "nextFile($outputFolder) = $filename after $attempts collision checks")
    }
    return filename
}

private fun fileExists(context: Context, folderName: String, filename: String): Boolean {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf("%$folderName%", filename)
    
    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )
    
    val exists = cursor?.use { it.count > 0 } ?: false
    cursor?.close()
    return exists
}



fun outputFolder(scene: Int): String {
    val dateFolder = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
    val sceneFolder = String.format("%03d", scene)
    return "StopMotion/$dateFolder-$sceneFolder"
}


fun saveImageToPublicPictures(
    context: Context,
    bitmap: Bitmap,
    subfolder: String,
    filename: String
): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_PICTURES}/$subfolder"
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            // Mark as not pending so it's visible to media scanners
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }
    return uri
}


fun countImagesInFolder(context: Context, folderName: String): Int {
    val cursor = getCursor(context, folderName)
    var count = 0
    cursor?.use {
        while (it.moveToNext()) {
            count++
        }
    }
    cursor?.close()
    Log.i("FileUtils", "countImagesInFolder($folderName) = $count")
    return count
}

fun getCursor(context: Context, folderName: String, dir: String = "ASC"): Cursor? {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    // FIXED: Actually use the selection to filter by folder
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%$folderName%")
    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} $dir"

    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,  // FIXED: Use the selection
        selectionArgs,  // FIXED: Use the selection args
        sortOrder
    )
    
    Log.i("FileUtils", "getCursor($folderName) returned ${cursor?.count ?: 0} results")
    return cursor
}

fun getLastImagesByName(context: Context, folderName: String, numImages: Int): MutableList<Uri?> {

    val cursor = getCursor(context, folderName, "DESC")
    val imageUris = mutableListOf<Uri?>()

    cursor?.use {
        var count = 0
        while (it.moveToNext() && count < numImages) {
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
            val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            imageUris.add(uri)
            Log.i("FileUtils", "getLastImagesByName: Added $path$name")
            count++
        }
    }

    Log.i("FileUtils", "getLastImagesByName($folderName, $numImages) returned ${imageUris.size} URIs")
    return imageUris
}


fun renameAllJpgImagesAlphabetically(
    context: Context,
    folderName: String
) {
    val fileList = mutableListOf<Triple<Long, String, String>>() // ID, current name, path
    val contentResolver = context.contentResolver

    val cursor = getCursor(context, folderName)

    cursor?.use {
        val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val pathCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

        while (it.moveToNext()) {
            val id = it.getLong(idCol)
            val name = it.getString(nameCol)
            val path = it.getString(pathCol)
            if (name.lowercase().endsWith(".jpg")) {
                Log.i("Rename", "Found: $path$name")
                fileList.add(Triple(id, name, path))
            }
        }
    }

    Log.i("Rename", "Found ${fileList.size} .jpg files in $folderName")

    // Sort by name to ensure consistent ordering
    val sortedFiles = fileList.sortedBy { it.second.lowercase() }

    sortedFiles.forEachIndexed { index, (id, oldName, path) ->
        val newName = String.format("%05d.jpg", index)
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

        if (oldName != newName) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            }

            val updated = contentResolver.update(uri, values, null, null)
            Log.i("Rename", "Renamed $oldName ➝ $newName (updated=$updated)")
        }
    }

    Log.i("Rename", "Renaming complete")
}


suspend fun listing(
    context: Context,
    folderName: String
) = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%$folderName%")
    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

    val fileList = mutableListOf<Triple<Long, String, String>>() // ID, current name, path

    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        var tick = 0
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol)
            val path = cursor.getString(pathCol)
            Log.i("Listing", "Found: $path$name $tick")
            tick++
            if (name.lowercase().endsWith(".jpg")) {
                fileList.add(Triple(id, name, path))
            }
        }
    }

    Log.i("Listing", "Found ${fileList.size} .jpg files in $folderName")

    fileList.forEachIndexed { index, (id, oldName, path) ->
        Log.i("Listing", "[$index] $oldName")
    }
}
// Add these SAF functions to your existing FileUtils.kt


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
    
    val imageUris = jpgs.map { it.uri as Uri? }.toMutableList()
    
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
