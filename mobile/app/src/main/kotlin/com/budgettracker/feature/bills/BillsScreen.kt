package com.budgettracker.feature.bills

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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
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
import com.budgettracker.data.model.Bill
import com.budgettracker.data.model.BillRecurrences
import com.budgettracker.ui.components.AccountBadge
import com.budgettracker.ui.components.AccountPickerSheet
import com.budgettracker.ui.components.CategoryBadge
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
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.ui.theme.money

/**
 * What is due, and what is coming. Overdue first, because that is the thing a
 * person opened this screen to find out.
 */
@Composable
fun BillsScreen(
    onBack: () -> Unit,
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = "Bills",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::addBill,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add bill") },
            )
        },
    ) { padding ->
        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            state.bills.isEmpty() && state.error != null -> CentredBox(padding = padding) {
                ErrorState(error = state.error!!, onRetry = viewModel::load)
            }

            state.bills.isEmpty() -> CentredBox(padding = padding) {
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "No bills yet",
                    message = "Add the ones that come round every month and you will be " +
                        "reminded before they are due.",
                    actionLabel = "Add a bill",
                    onAction = viewModel::addBill,
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
                if (state.due.isNotEmpty()) {
                    item {
                        Text(
                            text = if (state.overdueCount > 0) {
                                "${state.overdueCount} overdue"
                            } else {
                                "Due soon"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    items(state.due, key = { "due-${it.id}" }) { bill ->
                        BillCard(
                            bill = bill,
                            onPay = { viewModel.startPaying(bill) },
                            onSkip = { viewModel.skip(bill) },
                            onEdit = { viewModel.editBill(bill) },
                            onDelete = { viewModel.delete(bill) },
                        )
                    }
                }

                if (state.later.isNotEmpty()) {
                    item {
                        Text(
                            text = "Later",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    items(state.later, key = { "later-${it.id}" }) { bill ->
                        BillCard(
                            bill = bill,
                            onPay = { viewModel.startPaying(bill) },
                            onSkip = { viewModel.skip(bill) },
                            onEdit = { viewModel.editBill(bill) },
                            onDelete = { viewModel.delete(bill) },
                        )
                    }
                }

                if (state.inactive.isNotEmpty()) {
                    item {
                        TextButton(onClick = viewModel::toggleInactive) {
                            Text(
                                if (state.showInactive) {
                                    "Hide inactive (${state.inactive.size})"
                                } else {
                                    "Show inactive (${state.inactive.size})"
                                },
                            )
                        }
                    }

                    if (state.showInactive) {
                        items(state.inactive, key = { "off-${it.id}" }) { bill ->
                            BillCard(
                                bill = bill,
                                onPay = { viewModel.startPaying(bill) },
                                onSkip = { viewModel.skip(bill) },
                                onEdit = { viewModel.editBill(bill) },
                                onDelete = { viewModel.delete(bill) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        BillFormSheet(state = state, form = form, viewModel = viewModel)
    }

    state.paying?.let { form ->
        PaySheet(state = state, form = form, viewModel = viewModel)
    }
}

@Composable
private fun BillCard(
    bill: Bill,
    onPay: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val money = MaterialTheme.money

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = bill.name, style = MaterialTheme.typography.titleSmall)

                    if (bill.autoPay) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Pill(text = "Auto")
                    }

                    if (!bill.isActive) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Pill(text = "Off")
                    }
                }

                Text(
                    text = listOfNotNull(
                        bill.payee,
                        BillRecurrences.label(bill.recurrence),
                        bill.category?.name,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = bill.formattedAmount, style = MaterialTheme.typography.titleMedium)

                Text(
                    text = Dates.dueLabel(bill.daysUntilDue),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        bill.isOverdue -> money.negative
                        bill.daysUntilDue <= 3 -> money.warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (bill.isPaidThisCycle) {
            Text(
                text = "Paid for this cycle.",
                style = MaterialTheme.typography.bodySmall,
                color = money.positive,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("Edit") }

            TextButton(onClick = onSkip) { Text("Skip") }

            TextButton(onClick = onDelete) { Text("Remove") }

            Button(onClick = onPay) { Text("Mark paid") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillFormSheet(
    state: BillsUiState,
    form: BillForm,
    viewModel: BillsViewModel,
) {
    var pickingRecurrence by remember { mutableStateOf(false) }
    var pickingReminder by remember { mutableStateOf(false) }
    var pickingAccount by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(
                title = if (form.isEditing) "Edit bill" else "New bill",
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

                MoneyField(
                    value = form.amount,
                    onValueChange = { value -> viewModel.editForm { it.copy(amount = value) } },
                    currency = state.currency,
                    label = "Amount",
                    error = form.fieldErrors["amount_minor"],
                )

                DateField(
                    date = form.dueDate,
                    onDateChange = { value -> viewModel.editForm { it.copy(dueDate = value) } },
                    label = "Next due date",
                )

                PickerField(
                    label = "Repeats",
                    value = BillRecurrences.label(form.recurrence),
                    placeholder = "How often",
                    onClick = { pickingRecurrence = true },
                )

                PickerField(
                    label = "Paid from (optional)",
                    value = state.accounts.firstOrNull { it.id == form.accountId }?.name,
                    placeholder = "Choose an account",
                    onClick = { pickingAccount = true },
                    leading = state.accounts
                        .firstOrNull { it.id == form.accountId }
                        ?.let { account -> { AccountBadge(account = account, size = 24.dp) } },
                )

                PickerField(
                    label = "Category (optional)",
                    value = state.categories.firstOrNull { it.id == form.categoryId }?.name,
                    placeholder = "Choose a category",
                    onClick = { pickingCategory = true },
                    leading = state.categories
                        .firstOrNull { it.id == form.categoryId }
                        ?.let { category -> { CategoryBadge(category = category, size = 24.dp) } },
                )

                PickerField(
                    label = "Remind me",
                    value = reminderLabel(form.reminderDays),
                    placeholder = "When to remind you",
                    onClick = { pickingReminder = true },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Pays itself", style = MaterialTheme.typography.bodyMedium)

                        Text(
                            text = "It is taken automatically, so it only needs recording.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = form.autoPay,
                        onCheckedChange = { value ->
                            viewModel.editForm { it.copy(autoPay = value) }
                        },
                    )
                }

                OutlinedTextField(
                    value = form.note,
                    onValueChange = { value -> viewModel.editForm { it.copy(note = value) } },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (pickingRecurrence) {
        OptionSheet(
            title = "How often",
            options = BillRecurrences.options,
            selected = form.recurrence,
            onSelect = { value ->
                viewModel.editForm { it.copy(recurrence = value) }
                pickingRecurrence = false
            },
            onDismiss = { pickingRecurrence = false },
        )
    }

    if (pickingReminder) {
        OptionSheet(
            title = "Remind me",
            options = REMINDER_OPTIONS.map { it to reminderLabel(it) },
            selected = form.reminderDays,
            onSelect = { value ->
                viewModel.editForm { it.copy(reminderDays = value) }
                pickingReminder = false
            },
            onDismiss = { pickingReminder = false },
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

    if (pickingCategory) {
        CategoryPickerSheet(
            categories = state.categories,
            selectedId = form.categoryId,
            onSelect = { category ->
                viewModel.editForm { it.copy(categoryId = category?.id) }
                pickingCategory = false
            },
            onDismiss = { pickingCategory = false },
        )
    }
}

/** How many days before a due date a reminder is worth sending. */
private val REMINDER_OPTIONS = listOf(0, 1, 2, 3, 5, 7, 14)

private fun reminderLabel(days: Int): String = when (days) {
    0 -> "On the day"
    1 -> "1 day before"
    else -> "$days days before"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(
    state: BillsUiState,
    form: PayForm,
    viewModel: BillsViewModel,
) {
    var pickingAccount by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = viewModel::cancelPaying,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(title = "Pay ${form.bill.name}")

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoneyField(
                    value = form.amount,
                    onValueChange = { value -> viewModel.editPayment { it.copy(amount = value) } },
                    currency = state.currency,
                    label = "Amount paid",
                )

                DateField(
                    date = form.paidOn,
                    onDateChange = { value -> viewModel.editPayment { it.copy(paidOn = value) } },
                    label = "Paid on",
                    allowFuture = false,
                )

                PickerField(
                    label = "From",
                    value = state.accounts.firstOrNull { it.id == form.accountId }?.name,
                    placeholder = "Choose an account",
                    onClick = { pickingAccount = true },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Also record the expense",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Text(
                            text = "Leave this off if you already entered the payment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = form.recordExpense,
                        onCheckedChange = { value ->
                            viewModel.editPayment { it.copy(recordExpense = value) }
                        },
                    )
                }

                Button(
                    onClick = viewModel::confirmPayment,
                    enabled = !form.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mark as paid")
                }
            }
        }
    }

    if (pickingAccount) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = form.accountId,
            onSelect = { account ->
                viewModel.editPayment { it.copy(accountId = account.id) }
                pickingAccount = false
            },
            onDismiss = { pickingAccount = false },
        )
    }
}
