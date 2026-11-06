package com.budgettracker.feature.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Savings
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
import com.budgettracker.data.model.SavingsGoal
import com.budgettracker.ui.components.AccountPickerSheet
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.DateField
import com.budgettracker.ui.components.DetailScaffold
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FieldError
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.Pill
import com.budgettracker.ui.components.PickerField
import com.budgettracker.ui.components.ProgressMeter
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.ui.theme.money

/**
 * Savings goals: what is being saved for, how far along it is, and what to put
 * aside this month to get there on time.
 */
@Composable
fun SavingsScreen(
    onBack: () -> Unit,
    viewModel: SavingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = "Savings",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::addGoal,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New goal") },
            )
        },
    ) { padding ->
        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            state.goals.isEmpty() && state.error != null -> CentredBox(padding = padding) {
                ErrorState(error = state.error!!, onRetry = viewModel::load)
            }

            state.goals.isEmpty() -> CentredBox(padding = padding) {
                EmptyState(
                    icon = Icons.Outlined.Savings,
                    title = "Nothing being saved for yet",
                    message = "Set a target and a date, and the app works out what to put " +
                        "aside each month.",
                    actionLabel = "Set a goal",
                    onAction = viewModel::addGoal,
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
                items(state.active, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        onContribute = { viewModel.startContribution(goal) },
                        onEdit = { viewModel.editGoal(goal) },
                        onArchive = { viewModel.archive(goal) },
                        onDelete = { viewModel.delete(goal) },
                    )
                }

                if (state.achieved.isNotEmpty()) {
                    item {
                        Text(
                            text = "Reached",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    items(state.achieved, key = { "done-${it.id}" }) { goal ->
                        GoalCard(
                            goal = goal,
                            onContribute = { viewModel.startContribution(goal) },
                            onEdit = { viewModel.editGoal(goal) },
                            onArchive = { viewModel.archive(goal) },
                            onDelete = { viewModel.delete(goal) },
                        )
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        GoalFormSheet(state = state, form = form, viewModel = viewModel)
    }

    state.contributing?.let { form ->
        ContributionSheet(state = state, form = form, viewModel = viewModel)
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoal,
    onContribute: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val money = MaterialTheme.money

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = goal.name, style = MaterialTheme.typography.titleSmall)

                    if (goal.isAchieved) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Pill(
                            text = "Reached",
                            container = money.positiveContainer,
                            content = money.positive,
                        )
                    }
                }

                Text(
                    text = "${goal.formatted.saved} of ${goal.formatted.target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "${(goal.progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = if (goal.isAchieved) money.positive else MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        ProgressMeter(
            fraction = goal.progress.toFloat(),
            colour = if (goal.isAchieved) money.positive else MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = listOfNotNull(
                goal.formatted.remaining.takeIf { !goal.isAchieved }?.let { "$it to go" },
                goal.targetDate?.let { date ->
                    Dates.parse(date)?.let { "by ${Dates.medium(it)}" }
                },
                goal.formatted.suggestedMonthly?.let { "$it a month gets there" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("Edit") }

            TextButton(onClick = if (goal.isAchieved) onArchive else onDelete) {
                Text(if (goal.isAchieved) "Archive" else "Remove")
            }

            Button(onClick = onContribute) { Text("Put aside") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFormSheet(
    state: SavingsUiState,
    form: GoalForm,
    viewModel: SavingsViewModel,
) {
    var pickingAccount by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(
                title = if (form.isEditing) "Edit goal" else "New goal",
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
                    label = { Text("What are you saving for") },
                    singleLine = true,
                    isError = form.fieldErrors.containsKey("name"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                form.fieldErrors["name"]?.let { FieldError(it) }

                MoneyField(
                    value = form.target,
                    onValueChange = { value -> viewModel.editForm { it.copy(target = value) } },
                    currency = state.currency,
                    label = "Target amount",
                    error = form.fieldErrors["target_amount_minor"],
                )

                DateField(
                    date = form.targetDate ?: Dates.today().plusMonths(6),
                    onDateChange = { value -> viewModel.editForm { it.copy(targetDate = value) } },
                    label = "Target date (optional)",
                )

                PickerField(
                    label = "Held in (optional)",
                    value = state.accounts.firstOrNull { it.id == form.accountId }?.name,
                    placeholder = "Choose an account",
                    onClick = { pickingAccount = true },
                )

                Text(
                    text = "Linking an account means contributions move money into it, so " +
                        "balances stay truthful.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributionSheet(
    state: SavingsUiState,
    form: ContributionForm,
    viewModel: SavingsViewModel,
) {
    var pickingAccount by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = viewModel::cancelContribution,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(title = "Put aside for ${form.goal.name}")

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoneyField(
                    value = form.amount,
                    onValueChange = { value -> viewModel.editContribution { it.copy(amount = value) } },
                    currency = state.currency,
                    label = "Amount",
                )

                DateField(
                    date = form.date,
                    onDateChange = { value -> viewModel.editContribution { it.copy(date = value) } },
                    label = "On",
                    allowFuture = false,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Move the money too",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Text(
                            text = "Takes it out of an account, instead of only crediting " +
                                "the goal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = form.moveMoney,
                        onCheckedChange = { value ->
                            viewModel.editContribution { it.copy(moveMoney = value) }
                        },
                    )
                }

                if (form.moveMoney) {
                    PickerField(
                        label = "From",
                        value = state.accounts.firstOrNull { it.id == form.fromAccountId }?.name,
                        placeholder = "Choose an account",
                        onClick = { pickingAccount = true },
                    )
                }

                Button(
                    onClick = viewModel::saveContribution,
                    enabled = !form.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add it")
                }
            }
        }
    }

    if (pickingAccount) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = form.fromAccountId,
            onSelect = { account ->
                viewModel.editContribution { it.copy(fromAccountId = account.id) }
                pickingAccount = false
            },
            onDismiss = { pickingAccount = false },
        )
    }
}
