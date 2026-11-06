package com.budgettracker.feature.receipts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

/**
 * The two ways a receipt gets into the app: the camera, or a photo already on
 * the device.
 *
 * The camera writes into the app's own cache through a `FileProvider`, so no
 * storage permission is involved and nothing is left behind in the gallery.
 */
class ReceiptCapture internal constructor(
    private val onTakePhoto: () -> Unit,
    private val onPickImage: () -> Unit,
    val cameraAvailable: Boolean,
) {
    fun takePhoto() = onTakePhoto()

    fun pickImage() = onPickImage()
}

@Composable
fun rememberReceiptCapture(onCaptured: (Uri) -> Unit): ReceiptCapture {
    val context = LocalContext.current

    // Survives the trip out to the camera app and back, which is when the
    // result arrives with nothing but a success flag.
    val pending = remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved ->
        pending.value?.let { uri -> if (saved) onCaptured(uri) }
        pending.value = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        picked?.let(onCaptured)
    }

    val launchCamera: () -> Unit = {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            receiptCaptureFile(context),
        )

        pending.value = uri
        cameraLauncher.launch(uri)
    }

    // The manifest declares CAMERA, which means the capture intent needs it
    // granted even though another app takes the photo.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
    }

    return ReceiptCapture(
        onTakePhoto = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                launchCamera()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onPickImage = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        cameraAvailable = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY,
        ),
    )
}
