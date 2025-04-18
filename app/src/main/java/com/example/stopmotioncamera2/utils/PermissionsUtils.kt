package com.example.stopmotioncamera2.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

