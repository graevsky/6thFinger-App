package com.example.a6thfingercontrollapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

internal fun avatarFile(context: Context): File {
    val dir = File(context.filesDir, "avatar")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "avatar.jpg")
}

internal fun deleteAvatarIfExists(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching {
        val f = File(path)
        if (f.exists()) f.delete()
    }
}

internal fun loadBitmapFromFile(path: String?, maxDim: Int = 1024): Bitmap? {
    if (path.isNullOrBlank()) return null
    val f = File(path)
    if (!f.exists()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, opts)
}

internal fun saveAvatarFromCroppedUri(
    context: Context,
    croppedUri: Uri,
    outSize: Int = 512,
    quality: Int = 92
): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(croppedUri)?.use { ins ->
        BitmapFactory.decodeStream(ins, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim = outSize * 2)
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bmp = context.contentResolver.openInputStream(croppedUri)?.use { ins ->
        BitmapFactory.decodeStream(ins, null, opts)
    } ?: return null

    val scaled = if (bmp.width != outSize || bmp.height != outSize) {
        Bitmap.createScaledBitmap(bmp, outSize, outSize, true)
    } else bmp

    val outFile = avatarFile(context)
    FileOutputStream(outFile).use { fos ->
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
    }
    return outFile.absolutePath
}

private fun computeInSampleSize(srcW: Int, srcH: Int, maxDim: Int): Int {
    var sample = 1
    val largest = max(srcW, srcH)
    while (largest / sample > maxDim) sample *= 2
    return sample.coerceAtLeast(1)
}
