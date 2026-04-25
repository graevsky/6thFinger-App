package com.example.a6thfingercontrollapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Returns the private file used for the current account avatar.
 */
internal fun avatarFile(context: Context): File {
    val dir = File(context.filesDir, "avatar")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "avatar.jpg")
}

/**
 * Deletes an avatar file if the stored path still points to an existing file.
 */
internal fun deleteAvatarIfExists(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching {
        val f = File(path)
        if (f.exists()) f.delete()
    }
}

/**
 * Loads a bitmap from disk with downsampling.
 */
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

/**
 * Saves a cropped avatar Uri into the app's private avatar file.
 *
 * The image is decoded with sampling, scaled to a fixed square size and saved
 * as JPEG so both local display and backend upload use the same file.
 */
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

/**
 * Calculates a power-of-two BitmapFactory sample size for target max dimension.
 */
private fun computeInSampleSize(srcW: Int, srcH: Int, maxDim: Int): Int {
    var sample = 1
    val largest = max(srcW, srcH)
    while (largest / sample > maxDim) sample *= 2
    return sample.coerceAtLeast(1)
}
