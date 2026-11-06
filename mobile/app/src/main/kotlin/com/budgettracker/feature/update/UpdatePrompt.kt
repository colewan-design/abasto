package com.budgettracker.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.data.repository.PendingUpdate
import com.budgettracker.data.repository.UpdateStatus

/**
 * The "there is a newer version" prompt.
 *
 * Shown over the app rather than tucked into settings, because an install that
 * has fallen behind cannot be expected to go looking. It appears once the
 * launch check comes back with something newer, and it stays the same dialog
 * all the way through: available, downloading, ready to install.
 *
 * A required update has no way out — no dismiss buttons, and tapping outside
 * does nothing. Everything else can be put off for now or skipped for good.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkOnLaunch() }

    // A local val, so the `when` branches below smart-cast. Reading the
    // delegated property directly would not.
    val current = status
    val pending = current.pending ?: return

    // Set when installing was refused because this app is not allowed to
    // install others — the dialog then explains that instead of the release.
    var needsPermission by remember { mutableStateOf(false) }

    val dismissible = !pending.required && current !is UpdateStatus.Downloading

    AlertDialog(
        onDismissRequest = { if (dismissible) viewModel.dismiss() },
        icon = { Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null) },
        title = {
            Text(
                when {
                    needsPermission -> "Allow installing updates"
                    pending.required -> "Update required"
                    current is UpdateStatus.ReadyToInstall -> "Ready to install"
                    else -> "Update available"
                },
            )
        },
        text = {
            if (needsPermission) {
                Text(
                    text = "Android needs your permission for this app to install an " +
                        "update. Turn on \"Allow from this source\", then come back and " +
                        "tap Install.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                UpdateBody(status = current, pending = pending)
            }
        },
        confirmButton = {
            when {
                needsPermission -> TextButton(
                    onClick = {
                        needsPermission = false
                        viewModel.openInstallPermissionSettings()
                    },
                ) {
                    Text("Open settings")
                }

                current is UpdateStatus.ReadyToInstall -> TextButton(
                    onClick = {
                        if (viewModel.canInstall()) {
                            viewModel.install(current.file)
                        } else {
                            needsPermission = true
                        }
                    },
                ) {
                    Text("Install")
                }

                current is UpdateStatus.Failed -> TextButton(onClick = viewModel::download) {
                    Text("Try again")
                }

                current is UpdateStatus.Downloading -> TextButton(onClick = {}, enabled = false) {
                    Text("Downloading")
                }

                else -> TextButton(onClick = viewModel::download) {
                    Text("Download")
                }
            }
        },
        dismissButton = {
            if (needsPermission) {
                TextButton(onClick = { needsPermission = false }) { Text("Not now") }
            } else if (dismissible) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Only worth offering before the file has been fetched;
                    // after that "later" is the only sensible way out.
                    if (current is UpdateStatus.Available) {
                        TextButton(onClick = viewModel::skip) { Text("Skip") }
                    }

                    TextButton(onClick = viewModel::dismiss) { Text("Later") }
                }
            }
        },
    )
}

@Composable
private fun UpdateBody(status: UpdateStatus, pending: PendingUpdate) {
    val release = pending.release

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = buildString {
                append("Version ${release.versionName}")
                release.readableSize?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (pending.required) {
            Text(
                text = "This version of the app no longer works with the server, so it " +
                    "has to be updated before you carry on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        release.notes.forEach { note ->
            Text(
                text = "• $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (status) {
            is UpdateStatus.Downloading -> DownloadProgress(status)

            is UpdateStatus.ReadyToInstall -> Text(
                text = "Android will ask you to confirm the install.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateStatus.Failed -> Text(
                text = status.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            else -> Unit
        }
    }
}

@Composable
private fun DownloadProgress(status: UpdateStatus.Downloading) {
    val fraction = status.fraction

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (fraction == null) {
            // No content length: a determinate bar would be a guess.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = fraction
                ?.let { "${(it * 100).toInt()}% of ${status.update.release.readableSize ?: "the update"}" }
                ?: "Downloading…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
