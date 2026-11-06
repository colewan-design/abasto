package com.budgettracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.ui.components.DetailScaffold
import com.budgettracker.ui.components.IconBadge
import com.budgettracker.ui.components.OptionSheet
import com.budgettracker.ui.components.Pill
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.feature.update.UpdateCard
import com.budgettracker.ui.components.SectionHeader
import com.budgettracker.ui.lock.canAuthenticate

/**
 * Who is signed in, how the app behaves on this device, and the way out.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var pickingCurrency by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingSignOut by remember { mutableStateOf(false) }

    // Whether this device can satisfy the lock at all decides if the switch
    // is even offered.
    LaunchedEffect(Unit) {
        viewModel.setLockSupported(canAuthenticate(context))
    }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    DetailScaffold(
        title = "Settings",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::messageShown,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.user?.name ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                            )

                            if (state.isDemo) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Pill(text = "Demo")
                            }
                        }

                        Text(
                            text = state.user?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TextButton(
                        onClick = {
                            nameDraft = state.user?.name.orEmpty()
                            editingName = true
                        },
                    ) {
                        Text("Rename")
                    }
                }

                if (state.isDemo) {
                    Text(
                        text = "This is a throwaway demo account. Sign in with Google from " +
                            "the welcome screen to keep what you add.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SectionCard {
                SectionHeader(title = "Money")

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Currency", style = MaterialTheme.typography.bodyMedium)

                        Text(
                            text = "Amounts are recorded and shown in " +
                                "${state.user?.currency?.code ?: "—"}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TextButton(
                        onClick = { pickingCurrency = true },
                        enabled = state.currencies.isNotEmpty() && !state.busy,
                    ) {
                        Text("Change")
                    }
                }
            }

            SectionCard {
                SectionHeader(title = "This device")

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Outlined.Fingerprint,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 36.dp,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(text = "Lock the app", style = MaterialTheme.typography.bodyMedium)

                        Text(
                            text = if (state.lockSupported) {
                                "Ask for your fingerprint, face or device PIN when the app " +
                                    "is reopened."
                            } else {
                                "This device has no screen lock set up, so there is nothing " +
                                    "to unlock with."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = state.appLockEnabled,
                        onCheckedChange = viewModel::setAppLock,
                        enabled = state.lockSupported && !state.busy,
                    )
                }
            }

            // The deliberate way to look for a build, for when the launch
            // prompt was put off or the version was skipped.
            UpdateCard()

            if (state.isDemo) {
                SectionCard {
                    SectionHeader(
                        title = "Demo data",
                        subtitle = "Put the sample accounts and entries back as they were.",
                    )

                    OutlinedButton(
                        onClick = viewModel::resetDemo,
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Text("Reset the demo")
                    }
                }
            }

            SectionCard {
                SectionHeader(title = "Account")

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { confirmingSignOut = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign out")
                }

                OutlinedButton(
                    onClick = { confirmingDelete = true },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text("Delete my account")
                }
            }

            Text(
                text = "Abasto ${state.appVersion}\n${state.apiBase}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
            )
        }
    }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changeName(nameDraft)
                        editingName = false
                    },
                    enabled = nameDraft.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text("Cancel") }
            },
        )
    }

    if (pickingCurrency) {
        OptionSheet(
            title = "Currency",
            options = state.currencies.map { it.code to "${it.code} · ${it.name}" },
            selected = state.user?.currency?.code,
            onSelect = { code ->
                viewModel.changeCurrency(code)
                pickingCurrency = false
            },
            onDismiss = { pickingCurrency = false },
        )
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (state.isDemo) {
                        "This is a demo account — signing out leaves it behind and it " +
                            "cannot be reached again."
                    } else {
                        "Your records stay on the server. Sign back in any time."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSignOut = false
                        viewModel.signOut()
                    },
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) { Text("Stay") }
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "Every account, entry, receipt and budget is removed from the server. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.deleteAccount()
                    },
                ) {
                    Text("Delete everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep my account") }
            },
        )
    }
}
