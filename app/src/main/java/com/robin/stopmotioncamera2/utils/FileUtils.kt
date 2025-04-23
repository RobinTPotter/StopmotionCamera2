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


fun nextFile(context: Context, outputFolder: String): String {
    val nextNumber = 1 + countImagesInFolder(context, outputFolder)
    return String.format("%05d.jpg", nextNumber)
}

fun outputFolder(scene: Int, withTitle: Boolean= true): String {
    val dateFolder = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
    val sceneFolder = String.format("%03d", scene)
    return if (withTitle) "StopMotion/$dateFolder-$sceneFolder"
    else {
        "$dateFolder-$sceneFolder"
    }
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
    val list = mutableListOf<String>()
    var count = 0
    cursor?.use {
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
            list.add(name)
            count++
        }
    }

    // val count = cursor?.count ?: 0
    cursor?.close()
    return count
}

fun getCursor(context: Context, folderName: String, dir: String = "ASC"): Cursor? {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%$folderName/%")
    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} $dir"

    return context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
       null, // selection,
        null, //selectionArgs,
        sortOrder
    )
}

fun getLastImagesByName(context: Context, folderName: String, numImages: Int): MutableList<Uri?> {

    val cursor = getCursor(context, folderName, "DESC")
    val imageUris = mutableListOf<Uri?>()

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


fun renameAllJpgImagesAlphabetically(
    context: Context,
    folderName: String,
    suffix: Boolean = false
) {


    val fileList = mutableListOf<Triple<Long, String, String>>() // ID, current name, path
    val contentResolver = context.contentResolver

    val cursor = getCursor(context, folderName)

    cursor.use {
        if (it != null) {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol)
                val path = it.getString(pathCol)
                if (name.lowercase().endsWith(".jpg") && path.contains(folderName)) {
                    Log.i("Rename", "Found: $path/$name")
                    fileList.add(Triple(id, name, path))
                }
            }
        }
    }

    Log.i("Rename", "Found ${fileList.size} .jpg files in $folderName")

    fileList.forEachIndexed { index, (id, oldName, path) ->
        val ending = if (suffix) System.currentTimeMillis().toString() else ""

        val newName = String.format("%05d$ending.jpg", index)
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, newName)
        }

        val updated = contentResolver.update(uri, values, null, null)
        Log.i("Rename", "Renamed $oldName ➝ $newName ($updated row)")
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

//    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} = ${MediaStore.Images.Media.MIME_TYPE} OR  ${MediaStore.Images.Media.MIME_TYPE} = ?"
//    val selectionArgs = arrayOf("Pictures/$folderName/%", "image/jpeg")
    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

    val fileList = mutableListOf<Triple<Long, String, String>>() // ID, current name, path

    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null, // selection,
        null, // selectionArgs,
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
            if (name.lowercase().endsWith(".jpg") && path.contains(folderName)) {
                fileList.add(Triple(id, name, path))
            }
        }
    }

    Log.i("Listing", "Found ${fileList.size} .jpg files in $folderName")

    fileList.forEachIndexed { index, (id, oldName, path) ->
        Log.i("Listing", "Found $oldName")
    }

}

