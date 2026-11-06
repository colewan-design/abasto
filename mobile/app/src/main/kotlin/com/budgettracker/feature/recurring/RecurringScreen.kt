package com.budgettracker.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.RecurrenceFrequencies
import com.budgettracker.data.model.RecurringRule
import com.budgettracker.data.model.TransactionType
import com.budgettracker.ui.components.AccountPickerSheet
import com.budgettracker.ui.components.CategoryPickerSheet
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.DateField
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
import com.budgettracker.ui.components.SegmentedChoice
import com.budgettracker.ui.components.SheetTitle

/**
 * Entries that repeat — rent, a salary, a subscription.
 *
 * A rule that posts automatically needs nothing from anyone; one that does not
 * waits to be run, which suits an amount that changes every month.
 */
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = "Recurring",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::addRule,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New rule") },
            )
        },
    ) { padding ->
        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            state.rules.isEmpty() && state.error != null -> CentredBox(padding = padding) {
                ErrorState(error = state.error!!, onRetry = viewModel::load)
            }

            state.rules.isEmpty() -> CentredBox(padding = padding) {
                EmptyState(
                    icon = Icons.Outlined.Repeat,
                    title = "Nothing repeating yet",
                    message = "Set up the entries that come round every month and they will " +
                        "be recorded for you.",
                    actionLabel = "Add a rule",
                    onAction = viewModel::addRule,
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
                items(state.active, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { viewModel.editRule(rule) },
                        onRun = { viewModel.runNow(rule) },
                        onToggle = { viewModel.setActive(rule, !rule.isActive) },
                        onDelete = { viewModel.delete(rule) },
                    )
                }

                if (state.paused.isNotEmpty()) {
                    item {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    items(state.paused, key = { "paused-${it.id}" }) { rule ->
                        RuleCard(
                            rule = rule,
                            onEdit = { viewModel.editRule(rule) },
                            onRun = { viewModel.runNow(rule) },
                            onToggle = { viewModel.setActive(rule, !rule.isActive) },
                            onDelete = { viewModel.delete(rule) },
                        )
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        RuleFormSheet(state = state, form = form, viewModel = viewModel)
    }
}

@Composable
private fun RuleCard(
    rule: RecurringRule,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = rule.name, style = MaterialTheme.typography.titleSmall)

                    Spacer(modifier = Modifier.size(6.dp))

                    Pill(text = rule.type.label)

                    if (!rule.autoPost) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Pill(text = "Manual")
                    }
                }

                Text(
                    text = listOfNotNull(
                        rule.frequencyLabel.ifBlank { RecurrenceFrequencies.label(rule.frequency) },
                        rule.category?.name,
                        rule.nextRunOn?.let { "next ${Dates.relativeOrRaw(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(text = rule.formattedAmount, style = MaterialTheme.typography.titleMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("Edit") }

            TextButton(onClick = onToggle) {
                Text(if (rule.isActive) "Pause" else "Resume")
            }

            TextButton(onClick = onDelete) { Text("Remove") }

            TextButton(onClick = onRun) { Text("Run now") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleFormSheet(
    state: RecurringUiState,
    form: RuleForm,
    viewModel: RecurringViewModel,
) {
    var pickingFrequency by remember { mutableStateOf(false) }
    var pickingAccount by remember { mutableStateOf(false) }
    var pickingDestination by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }

    val categories = state.categories.filter { category ->
        when (form.type) {
            TransactionType.Income -> category.type == CategoryType.Income
            else -> category.type == CategoryType.Expense
        }
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(
                title = if (form.isEditing) "Edit rule" else "New recurring rule",
                action = {
                    TextButton(onClick = viewModel::saveForm, enabled = form.canSave) {
                        Text("Save")
                    }
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
                    onValueChange = { value -> viewModel.editForm { it.copy(name = value) } },
                    label = { Text("What is it") },
                    singleLine = true,
                    isError = form.fieldErrors.containsKey("name"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                form.fieldErrors["name"]?.let { FieldError(it) }

                SegmentedChoice(
                    options = listOf(
                        TransactionType.Expense to "Expense",
                        TransactionType.Income to "Income",
                        TransactionType.Transfer to "Transfer",
                    ),
                    selected = form.type,
                    onSelect = { type ->
                        viewModel.editForm { it.copy(type = type, categoryId = null) }
                    },
                )

                MoneyField(
                    value = form.amount,
                    onValueChange = { value -> viewModel.editForm { it.copy(amount = value) } },
                    currency = state.currency,
                    label = "Amount",
                    error = form.fieldErrors["amount_minor"],
                )

                PickerField(
                    label = if (form.isTransfer) "From" else "Account",
                    value = state.accounts.firstOrNull { it.id == form.accountId }?.name,
                    placeholder = "Choose an account",
                    onClick = { pickingAccount = true },
                )

                if (form.isTransfer) {
                    PickerField(
                        label = "To",
                        value = state.accounts.firstOrNull { it.id == form.transferAccountId }?.name,
                        placeholder = "Choose a destination",
                        onClick = { pickingDestination = true },
                    )
                } else {
                    PickerField(
                        label = "Category",
                        value = categories.firstOrNull { it.id == form.categoryId }?.name,
                        placeholder = "Choose a category",
                        onClick = { pickingCategory = true },
                    )

                    OutlinedTextField(
                        value = form.merchant,
                        onValueChange = { value -> viewModel.editForm { it.copy(merchant = value) } },
                        label = { Text("Merchant (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PickerField(
                    label = "Repeats",
                    value = RecurrenceFrequencies.label(form.frequency),
                    placeholder = "How often",
                    onClick = { pickingFrequency = true },
                )

                DateField(
                    date = form.startDate,
                    onDateChange = { value -> viewModel.editForm { it.copy(startDate = value) } },
                    label = "Starting",
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Record it for me", style = MaterialTheme.typography.bodyMedium)

                        Text(
                            text = "Off means it waits for you to run it, for an amount that " +
                                "changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = form.autoPost,
                        onCheckedChange = { value ->
                            viewModel.editForm { it.copy(autoPost = value) }
                        },
                    )
                }
            }
        }
    }

    if (pickingFrequency) {
        OptionSheet(
            title = "How often",
            options = RecurrenceFrequencies.options,
            selected = form.frequency,
            onSelect = { value ->
                viewModel.editForm { it.copy(frequency = value) }
                pickingFrequency = false
            },
            onDismiss = { pickingFrequency = false },
        )
    }

    if (pickingAccount) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = form.accountId,
            onSelect = { account ->
                viewModel.editForm { it.copy(accountId = account.id) }
                pickingAccount = false
            },
            onDismiss = { pickingAccount = false },
        )
    }

    if (pickingDestination) {
        AccountPickerSheet(
            accounts = state.accounts.filterNot { it.id == form.accountId },
            selectedId = form.transferAccountId,
            onSelect = { account ->
                viewModel.editForm { it.copy(transferAccountId = account.id) }
                pickingDestination = false
            },
            onDismiss = { pickingDestination = false },
            title = "Transfer to",
        )
    }

    if (pickingCategory) {
        CategoryPickerSheet(
            categories = categories,
            selectedId = form.categoryId,
            onSelect = { category ->
                viewModel.editForm { it.copy(categoryId = category?.id) }
                pickingCategory = false
            },
            onDismiss = { pickingCategory = false },
        )
    }
}
