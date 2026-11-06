package com.budgettracker.feature.receipts

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Reads the text off a receipt on the device.
 *
 * The recognised text is uploaded with the image so the server can parse the
 * receipt without calling a paid OCR service — the model ships with the app,
 * so this works on a plane and costs nothing per scan.
 *
 * A failure here is not fatal: the upload goes ahead without the text and the
 * server falls back to its own extraction.
 */
@Singleton
class ReceiptTextRecognizer @Inject constructor() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(context: Context, file: File): String? =
        recognize(context, Uri.fromFile(file))

    suspend fun recognize(context: Context, image: Uri): String? =
        suspendCancellableCoroutine { continuation ->
            val input = try {
                InputImage.fromFilePath(context, image)
            } catch (_: Exception) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            recognizer.process(input)
                .addOnSuccessListener { result ->
                    continuation.resume(result.text.takeIf { it.isNotBlank() })
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
                .addOnCanceledListener {
                    continuation.resume(null)
                }
        }
}
