package com.budgettracker.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.data.repository.UpdateStatus
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.components.SectionHeader

/**
 * The deliberate way to look for an update, for when the launch check was
 * dismissed or the version was skipped.
 *
 * Shares [UpdateViewModel]'s singleton repository with [UpdatePrompt], so a
 * download started here is the same one the prompt shows, and vice versa.
 */
@Composable
fun UpdateCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val current = status

    var needsPermission by remember { mutableStateOf(false) }

    SectionCard {
        SectionHeader(
            title = "App updates",
            subtitle = "Installed: ${viewModel.installedVersionName} " +
                "(build ${viewModel.installedVersionCode})",
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline(current),
                    style = MaterialTheme.typography.bodyMedium,
                )

                detail(current, needsPermission)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (current is UpdateStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        if (current is UpdateStatus.Downloading) {
            val fraction = current.fraction

            Spacer(modifier = Modifier.height(10.dp))

            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }

        OutlinedButton(
            onClick = {
                when {
                    needsPermission -> {
                        needsPermission = false
                        viewModel.openInstallPermissionSettings()
                    }

                    current is UpdateStatus.ReadyToInstall ->
                        if (viewModel.canInstall()) {
                            viewModel.install(current.file)
                        } else {
                            needsPermission = true
                        }

                    current is UpdateStatus.Available -> viewModel.download()

                    current is UpdateStatus.Failed -> {
                        // A failed download retries the download; a failed
                        // check has nothing downloaded to retry.
                        if (current.update != null) viewModel.download() else viewModel.check()
                    }

                    else -> viewModel.check()
                }
            },
            enabled = current !is UpdateStatus.Downloading && current !is UpdateStatus.Checking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Text(action(current, needsPermission))
        }
    }
}

private fun headline(status: UpdateStatus): String = when (status) {
    UpdateStatus.Idle -> "Check whether a newer build has been published."
    UpdateStatus.Checking -> "Checking…"
    UpdateStatus.UpToDate -> "You are on the latest version."
    is UpdateStatus.Available -> "Version ${status.update.release.versionName} is available."
    is UpdateStatus.Downloading -> "Downloading version ${status.update.release.versionName}…"
    is UpdateStatus.ReadyToInstall -> "Version ${status.update.release.versionName} is ready to install."
    is UpdateStatus.Failed -> "Could not get the update."
}

private fun detail(status: UpdateStatus, needsPermission: Boolean): String? = when {
    needsPermission ->
        "Android needs your permission for this app to install an update."

    status is UpdateStatus.Available ->
        listOfNotNull(status.update.release.readableSize, status.update.release.releasedAt)
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }

    status is UpdateStatus.Downloading ->
        status.fraction?.let { "${(it * 100).toInt()}%" }

    status is UpdateStatus.ReadyToInstall ->
        "Android will ask you to confirm the install."

    status is UpdateStatus.Failed -> status.message

    else -> null
}

private fun action(status: UpdateStatus, needsPermission: Boolean): String = when {
    needsPermission -> "Open settings"
    status is UpdateStatus.ReadyToInstall -> "Install"
    status is UpdateStatus.Available -> "Download"
    status is UpdateStatus.Downloading -> "Downloading…"
    status is UpdateStatus.Checking -> "Checking…"
    status is UpdateStatus.Failed -> "Try again"
    else -> "Check for updates"
}
