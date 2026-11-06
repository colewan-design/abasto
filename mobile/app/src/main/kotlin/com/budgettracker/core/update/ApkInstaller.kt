package com.budgettracker.core.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a downloaded APK to the system installer.
 *
 * Nothing here installs anything itself — it cannot. The system shows its own
 * confirmation and the person approves the install there, which is the whole
 * reason a sideloaded update is safe to offer.
 *
 * Two gates stand between the file and that screen. The app has to hold
 * `REQUEST_INSTALL_PACKAGES`, which is in the manifest; and the person has to
 * have allowed *this* app to install others, which is a per-app setting only
 * they can turn on. [canInstall] reports the second so the UI can send them
 * there instead of firing an intent that quietly does nothing.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Opens the install screen for [file].
     *
     * The file is shared through the same `FileProvider` the camera writes
     * receipts into: a `file://` URI pointed at the app's own cache would be
     * rejected outright, and the installer needs read access granted to it
     * explicitly for the one URI.
     */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Started from an application context, which has no task of its own.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    /** The system screen where installing from this app is allowed. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
