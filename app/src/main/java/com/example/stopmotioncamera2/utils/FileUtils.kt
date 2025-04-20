package com.example.stopmotioncamera2.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


fun createPhotoFile(outputFolder: File?): File {
    val nextNumber = outputFolder?.listFiles()?.size ?: 0
    val fileName = String.format("%05d.jpg", nextNumber)
    Log.i("File", "going to try and make filename $fileName")
    return File(outputFolder, fileName)
}

fun nextFile(context: Context , outputFolder: String): String {
    val nextNumber = countImagesInFolder(context, outputFolder)
    return String.format("%05d.jpg", nextNumber)
}

fun outputFolder(scene: Int): String {
    val dateFolder = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
    val sceneFolder = String.format("%03d", scene)
    return "StopMotion/$dateFolder-$sceneFolder"
}

fun setupOutputFolder(context: Context, scene: Int, savedImages: MutableList<File>): File {
    val picturesDir  = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val subfolder = outputFolder(scene)
    val outputFolder = File(picturesDir, subfolder)

    if (!outputFolder.exists()) {
        outputFolder.mkdirs()
        savedImages.clear()
    } else {
        var count = 0
        outputFolder.listFiles()?.forEach {
            val expectedName = String.format("%05d.jpg", count)
            if (it.name != expectedName) {
                val renamed = it.renameTo(File(outputFolder, expectedName))
                Log.i("GetNewName", "Renamed ${it.name} -> $expectedName ($renamed)")
            }
            count++
        }
//        outputFolder.listFiles()?.forEach {
//           savedImages.add(File(it.name))
//        }
    }

    Log.i("FileUtils", "using directory $outputFolder")
    return outputFolder
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


fun moveImageToPublicPictures(context: Context, sourceFile: File, subfolder: String): File? {
    val destDir = File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), "StopMotion/$subfolder")

    if (!destDir.exists()) {
        if (!destDir.mkdirs()) {
            Log.e("MoveImage", "Failed to create directory: $destDir")
            return null
        }
    }

    val destFile = File(destDir, sourceFile.name)

    return try {
        sourceFile.copyTo(destFile, overwrite = true)
        // Optional: delete original
        sourceFile.delete()

        // Trigger media scanner
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("image/jpeg"),
            null
        )

        destFile
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}



fun moveToPublicFolder(context: Context, sourceFile: File): File? {
    // Step 1: Extract the folder name (e.g., "20250419-000") from the source file path
    val folderName = sourceFile.parentFile?.name ?: return null

    // Step 2: Define the target directory in the public Pictures folder
    val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "StopMotion/$folderName")

    // Step 3: Create the directory if it doesn't exist
    if (!targetDir.exists()) {
        if (!targetDir.mkdirs()) {
            // Failed to create directory
            return null
        }
    }

    // Step 4: Define the destination file path (same file name in the new location)
    val targetFile = File(targetDir, sourceFile.name)

    // Step 5: Move the file from internal app folder to the public Pictures folder
    return try {
        // Copy the file to the target location
        sourceFile.copyTo(targetFile, overwrite = true)

        // Optionally: Delete the original file
        sourceFile.delete()

        // Trigger media scan so the file shows up in gallery
        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf("image/jpeg"),
            null
        )

        targetFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


fun saveBitmapToGallery(context: Context, bitmap: Bitmap, folderName: String?, fileName: String?) {
    val values = ContentValues()
    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") // or "image/png"
    values.put(
        MediaStore.Images.Media.RELATIVE_PATH,
        Environment.DIRECTORY_PICTURES + "/$folderName"
    )
    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri).use { out ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG, 100,
                    out!!
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
            resolver.delete(imageUri, null, null) // Clean up if something went wrong
        }
    }
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
        MediaStore.Images.Media.DATE_ADDED + " ASC" // Optional: sort oldest to newest
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
