package com.example.stopmotioncamera2.utils

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun createPhotoFile(outputFolder: File?): File {
    val nextNumber = outputFolder?.listFiles()?.size ?: 0
    val fileName = String.format("%05d.jpg", nextNumber)
    Log.i("File", "going to try and make filename $fileName")
    return File(outputFolder, fileName)
}

fun setupOutputFolder(scene: Int, savedImages: MutableList<File>): File {
    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val dateFolder = SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())
    val sceneFolder = String.format("%03d", scene)
    val outputFolder = File(picturesDir, "StopMotion/$dateFolder-$sceneFolder")

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
