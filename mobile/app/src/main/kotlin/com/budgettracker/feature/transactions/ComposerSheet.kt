package com.budgettracker.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionType
import com.budgettracker.ui.components.AccountBadge
import com.budgettracker.ui.components.AccountPickerSheet
import com.budgettracker.ui.components.CategoryBadge
import com.budgettracker.ui.components.CategoryPickerSheet
import com.budgettracker.ui.components.DateField
import com.budgettracker.ui.components.ErrorBanner
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.PickerField
import com.budgettracker.ui.components.SegmentedChoice
import com.budgettracker.ui.components.SheetTitle

/**
 * The entry form, as a sheet so it can be opened over any screen.
 *
 * Amount comes first because it is the one field that is always needed, and
 * the type switch sits above it: changing between expense, income and transfer
 * changes what the rest of the form even asks for.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionComposerSheet(
    editing: Transaction?,
    initialType: TransactionType,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ComposerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(editing?.id, initialType) {
        if (editing != null) viewModel.edit(editing) else viewModel.start(initialType)
    }

    var pickingAccount by remember { mutableStateOf(false) }
    var pickingDestination by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            SheetTitle(
                title = state.title,
                action = {
                    TextButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = state.canSave,
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { message ->
                    ErrorBanner(message = message, onDismiss = viewModel::dismissError)
                }

                if (!state.isEditing) {
                    SegmentedChoice(
                        options = listOf(
                            TransactionType.Expense to "Expense",
                            TransactionType.Income to "Income",
                            TransactionType.Transfer to "Transfer",
                        ),
                        selected = state.type,
                        onSelect = viewModel::onTypeChange,
                    )
                }

                MoneyField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    currency = state.currency,
                    label = "Amount",
                    error = state.fieldErrors["amount_minor"],
                    large = true,
                )

                PickerField(
                    label = if (state.isTransfer) "From" else "Account",
                    value = state.account?.name,
                    placeholder = "Choose an account",
                    onClick = { if (!state.accountsLocked) pickingAccount = true },
                    error = state.fieldErrors["account_id"],
                    leading = state.account?.let { account ->
                        { AccountBadge(account = account, size = 24.dp) }
                    },
                )

                if (state.isTransfer) {
                    PickerField(
                        label = "To",
                        value = state.toAccount?.name,
                        placeholder = "Choose a destination",
                        onClick = { if (!state.accountsLocked) pickingDestination = true },
                        error = state.fieldErrors["to_account_id"],
                        leading = state.toAccount?.let { account ->
                            { AccountBadge(account = account, size = 24.dp) }
                        },
                    )

                    if (state.accountsLocked) {
                        Text(
                            text = "The two sides of a transfer stay as they were. Amount, " +
                                "date and note can still be changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = state.merchant,
                        onValueChange = viewModel::onMerchantChange,
                        label = { Text(if (state.type == TransactionType.Income) "Source" else "Merchant") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PickerField(
                        label = "Category",
                        value = state.category?.name,
                        placeholder = "Choose a category",
                        onClick = { pickingCategory = true },
                        error = state.fieldErrors["category_id"],
                        leading = state.category?.let { category ->
                            { CategoryBadge(category = category, size = 24.dp) }
                        },
                    )

                    state.suggestion?.category?.let { suggested ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = viewModel::acceptSuggestion,
                                label = { Text("Use ${suggested.name}") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )

                            TextButton(onClick = viewModel::dismissSuggestion) {
                                Text("No thanks")
                            }
                        }
                    }
                }

                DateField(
                    date = state.date,
                    onDateChange = viewModel::onDateChange,
                    label = "Date",
                    error = state.fieldErrors["occurred_on"],
                )

                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.tags.isNotEmpty() && !state.isTransfer) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in state.tagIds,
                                onClick = { viewModel.toggleTag(tag.id) },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(4.dp))

                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isEditing) "Save changes" else "Record it")
                }
            }
        }
    }

    if (pickingAccount) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            onSelect = { account ->
                viewModel.onAccountChange(account.id)
                pickingAccount = false
            },
            onDismiss = { pickingAccount = false },
            title = if (state.isTransfer) "Transfer from" else "Account",
        )
    }

    if (pickingDestination) {
        AccountPickerSheet(
            accounts = state.accounts.filterNot { it.id == state.accountId },
            selectedId = state.toAccountId,
            onSelect = { account ->
                viewModel.onToAccountChange(account.id)
                pickingDestination = false
            },
            onDismiss = { pickingDestination = false },
            title = "Transfer to",
        )
    }

    if (pickingCategory) {
        CategoryPickerSheet(
            categories = state.categories,
            selectedId = state.categoryId,
            onSelect = { category ->
                viewModel.onCategoryChange(category?.id)
                pickingCategory = false
            },
            onDismiss = { pickingCategory = false },
        )
    }
}
