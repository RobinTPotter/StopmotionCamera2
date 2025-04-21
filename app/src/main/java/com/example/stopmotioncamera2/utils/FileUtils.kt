package com.example.stopmotioncamera2.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


fun nextFile(context: Context, outputFolder: String): String {
    val nextNumber = countImagesInFolder(context, outputFolder)
    return String.format("%05d.jpg", nextNumber)
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
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/$folderName/%")

    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )

    val count = cursor?.count ?: 0
    cursor?.close()
    return count
}


fun getLastImagesByName(context: Context, folderName: String, numImages: Int): MutableList<Uri?> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )

    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/$folderName/%")
    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} DESC"
    val imageUris = mutableListOf<Uri?>()

    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )

    cursor?.use {
        var count = 0
        while (it.moveToNext() && count < numImages) {
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            imageUris.add(uri)
            count++
        }
    }

    return imageUris
}

fun renameImagesInMediaStore(context: Context, folderName: String) {
    val contentResolver = context.contentResolver
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )

    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/$folderName/%")

    val cursor = contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        MediaStore.Images.Media.DISPLAY_NAME + " ASC"
    )

    cursor?.use {
        var count = 0
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val originalUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val newName = String.format("%05d.jpg", count)

            // Copy the original file to a new file with the new name
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName")
            }

            val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (newUri != null) {
                context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                    context.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Delete the original
                contentResolver.delete(originalUri, null, null)
                Log.i("Rename", "Renamed to $newName")
            } else {
                Log.w("Rename", "Failed to create new file for $newName")
            }
            count++
        }
    }
}


fun oldrenameImagesInMediaStore(context: Context, folderName: String) {
    val contentResolver = context.contentResolver
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )

    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/$folderName/%")

    val cursor = contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        MediaStore.Images.Media.DISPLAY_NAME + " ASC" // Optional: sort oldest to newest
    )

    cursor?.use {
        var count = 0
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

            val newName = String.format("%05d.jpg", count)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            }

            val rows = contentResolver.update(uri, values, null, null)
            Log.i("Rename", "Updated to $newName ($rows row(s))")
            count++
        }
    }

}
