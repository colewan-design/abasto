package com.budgettracker.feature.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.core.money.Currency
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.AccountType
import com.budgettracker.ui.components.AccountBadge
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.DetailScaffold
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FieldError
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.OptionSheet
import com.budgettracker.ui.components.Pill
import com.budgettracker.ui.components.PickerField
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.ui.components.StatTile
import com.budgettracker.ui.components.MoneyTone
import com.budgettracker.ui.theme.MoneyLarge
import com.budgettracker.ui.theme.money

/**
 * Where the money is. Net worth up top, then each account with its balance,
 * and the archived ones tucked away behind a toggle.
 */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Account?>(null) }

    DetailScaffold(
        title = "Accounts",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::messageShown,
        actions = {
            IconButton(onClick = viewModel::recalculate) {
                Icon(Icons.Outlined.Calculate, contentDescription = "Rebuild balances")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::addAccount,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add account") },
            )
        },
    ) { padding ->
        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            state.accounts.isEmpty() && state.error != null -> CentredBox(padding = padding) {
                ErrorState(error = state.error!!, onRetry = viewModel::load)
            }

            state.accounts.isEmpty() -> CentredBox(padding = padding) {
                EmptyState(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "No accounts yet",
                    message = "Add the wallet, bank account or card you actually use, and " +
                        "balances will follow every entry you record.",
                    actionLabel = "Add an account",
                    onAction = viewModel::addAccount,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard {
                        Text(
                            text = "Net worth",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(text = state.balances.formatted.netWorth, style = MoneyLarge)

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Available",
                                value = state.balances.formatted.available,
                                tone = MoneyTone.Positive,
                                modifier = Modifier.weight(1f),
                            )

                            StatTile(
                                label = "Owed",
                                value = state.balances.formatted.owed,
                                tone = MoneyTone.Negative,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                items(state.active, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        onEdit = { viewModel.editAccount(account) },
                        onArchive = { viewModel.archive(account) },
                        onDelete = { removing = account },
                    )
                }

                if (state.archived.isNotEmpty()) {
                    item {
                        TextButton(onClick = viewModel::toggleArchived) {
                            Text(
                                if (state.showArchived) {
                                    "Hide archived (${state.archived.size})"
                                } else {
                                    "Show archived (${state.archived.size})"
                                },
                            )
                        }
                    }

                    if (state.showArchived) {
                        items(state.archived, key = { "archived-${it.id}" }) { account ->
                            AccountCard(
                                account = account,
                                onEdit = { viewModel.editAccount(account) },
                                onArchive = { viewModel.restore(account) },
                                onDelete = { removing = account },
                            )
                        }
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        AccountFormSheet(
            form = form,
            currency = state.currency,
            onEdit = viewModel::editForm,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::closeForm,
        )
    }

    removing?.let { account ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${account.name}?") },
            text = {
                Text(
                    "If it has any history it will be archived instead, so the entries " +
                        "that reference it stay intact.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(account)
                        removing = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountBadge(account = account)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = account.name, style = MaterialTheme.typography.titleSmall)

                    if (account.isArchived) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Pill(text = "Archived")
                    }
                }

                Text(
                    text = listOfNotNull(
                        account.typeLabel.takeIf { it.isNotBlank() },
                        account.institution,
                        account.transactionsCount?.let { "$it entries" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = account.formatted.balance,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (account.isLiability) {
                        MaterialTheme.money.negative
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                account.formatted.availableCredit?.let { credit ->
                    Text(
                        text = "$credit available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("Edit") }

            TextButton(onClick = onArchive) {
                Text(if (account.isArchived) "Restore" else "Archive")
            }

            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormSheet(
    form: AccountForm,
    currency: Currency,
    onEdit: ((AccountForm) -> AccountForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingType by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(
                title = if (form.isEditing) "Edit account" else "New account",
                action = {
                    TextButton(onClick = onSave, enabled = form.canSave) { Text("Save") }
                },
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> onEdit { it.copy(name = value) } },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = form.fieldErrors.containsKey("name"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                form.fieldErrors["name"]?.let { FieldError(it) }

                PickerField(
                    label = "Type",
                    value = form.type.label,
                    placeholder = "Choose a type",
                    onClick = { pickingType = true },
                )

                MoneyField(
                    value = form.openingBalance,
                    onValueChange = { value -> onEdit { it.copy(openingBalance = value) } },
                    currency = currency,
                    label = if (form.isLiability) "Opening balance owed" else "Opening balance",
                    error = form.fieldErrors["opening_balance_minor"],
                )

                if (form.isLiability) {
                    MoneyField(
                        value = form.creditLimit,
                        onValueChange = { value -> onEdit { it.copy(creditLimit = value) } },
                        currency = currency,
                        label = "Credit limit",
                    )
                }

                OutlinedTextField(
                    value = form.institution,
                    onValueChange = { value -> onEdit { it.copy(institution = value) } },
                    label = { Text("Bank or provider (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Count in net worth", style = MaterialTheme.typography.bodyMedium)

                        Text(
                            text = "Turn this off for an account you track but do not own, " +
                                "like a shared wallet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = form.includeInTotal,
                        onCheckedChange = { value -> onEdit { it.copy(includeInTotal = value) } },
                    )
                }

                OutlinedButton(
                    onClick = onSave,
                    enabled = form.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (form.isEditing) "Save changes" else "Add account")
                }
            }
        }
    }

    if (pickingType) {
        OptionSheet(
            title = "Account type",
            options = AccountType.entries.map { it to it.label },
            selected = form.type,
            onSelect = { type ->
                onEdit { it.copy(type = type) }
                pickingType = false
            },
            onDismiss = { pickingType = false },
        )
    }
}
