package com.budgettracker.feature.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gets a photo ready to upload.
 *
 * A modern phone camera produces a 4-12 MB file; a receipt only needs enough
 * resolution for the text to be legible. Downscaling and recompressing before
 * the upload is the difference between a scan that works on a slow connection
 * and one that times out — and it is done after applying the EXIF rotation, so
 * the server and the OCR driver both see the image the right way up.
 */
@Singleton
class ReceiptImagePreparer @Inject constructor() {

    suspend fun prepare(context: Context, source: Uri): File? = withContext(Dispatchers.IO) {
        val target = File(
            receiptCacheDirectory(context),
            "upload-${System.currentTimeMillis()}.jpg",
        )

        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

            context.contentResolver.openInputStream(source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return@runCatching null

            val longestEdge = max(bounds.outWidth, bounds.outHeight)

            if (longestEdge <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(longestEdge)
            }

            val decoded = context.contentResolver.openInputStream(source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@runCatching null

            val rotation = context.contentResolver.openInputStream(source)?.use { stream ->
                ExifInterface(stream).rotationDegrees
            } ?: 0

            val oriented = decoded.oriented(rotation)
            val scaled = oriented.scaledToFit(MAX_EDGE)

            target.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }

            if (scaled !== oriented) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()

            target.takeIf { it.length() > 0L }
        }.getOrNull()
    }

    /**
     * Captures and prepared uploads pile up in the cache; clearing the old
     * ones keeps a few hundred scans from quietly filling the device.
     */
    fun clearOldFiles(context: Context) {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS

        receiptCacheDirectory(context)
            .listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun sampleSize(longestEdge: Int): Int {
        var sample = 1

        while (longestEdge / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }

        return sample
    }

    private fun Bitmap.oriented(degrees: Int): Bitmap {
        if (degrees == 0) return this

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }

        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.scaledToFit(maxEdge: Int): Bitmap {
        val longest = max(width, height)

        if (longest <= maxEdge) return this

        val ratio = maxEdge.toFloat() / longest.toFloat()

        return scale(
            width = (width * ratio).roundToInt().coerceAtLeast(1),
            height = (height * ratio).roundToInt().coerceAtLeast(1),
        )
    }

    private companion object {
        /** Enough for small print to survive; well under the server's limit. */
        const val MAX_EDGE = 2000
        const val JPEG_QUALITY = 82
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}

/**
 * Captures and prepared uploads live here. It is the path the manifest's
 * `file_paths.xml` shares with the camera, so a capture file can be handed
 * out as a content URI.
 */
fun receiptCacheDirectory(context: Context): File =
    File(context.cacheDir, "receipts").apply { mkdirs() }

/** A fresh file for the camera to write its photo into. */
fun receiptCaptureFile(context: Context): File =
    File(receiptCacheDirectory(context), "capture-${System.currentTimeMillis()}.jpg")
