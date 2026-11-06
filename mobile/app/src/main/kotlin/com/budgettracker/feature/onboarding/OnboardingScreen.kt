package com.budgettracker.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.budgettracker.data.model.AccountTypeOption
import com.budgettracker.data.model.CatalogGroup
import com.budgettracker.ui.components.ChoiceRow
import com.budgettracker.ui.components.ErrorBanner
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.IconBadge
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.OptionSheet
import com.budgettracker.ui.components.PickerField
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.icons.AppIcons

/**
 * Guided setup, four steps.
 *
 * Each step asks for one thing and nothing is written until the last one, so
 * going back is free. The budget step is skippable — a budget is useful but
 * not required to start recording.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.loading) {
            FullScreenLoader()
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { message ->
                    ErrorBanner(message = message, onDismiss = viewModel::dismissError)
                }

                AnimatedContent(targetState = state.step, label = "setup-step") { step ->
                    when (step) {
                        SetupStep.Currency -> CurrencyStep(state, viewModel)
                        SetupStep.Accounts -> AccountsStep(state, viewModel)
                        SetupStep.Categories -> CategoriesStep(state, viewModel)
                        SetupStep.Budget -> BudgetStep(state, viewModel)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step != SetupStep.Currency) {
                    OutlinedButton(
                        onClick = viewModel::back,
                        enabled = !state.saving,
                    ) {
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        if (state.step == SetupStep.Budget) {
                            viewModel.finish(onFinished)
                        } else {
                            viewModel.next()
                        }
                    },
                    enabled = state.canContinue && !state.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        state.saving -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )

                        state.step == SetupStep.Budget -> Text("Finish setup")
                        else -> Text("Continue")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrencyStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeading(
            title = "Which currency do you keep your books in?",
            body = "Every amount is recorded in this currency. It can be changed later " +
                "in settings.",
        )

        SectionCard(padding = 0.dp) {
            state.options?.currencies?.forEach { option ->
                ChoiceRow(
                    label = "${option.code} · ${option.name}",
                    secondary = "Symbol ${option.symbol}",
                    selected = option.code == state.currencyCode,
                    onClick = { viewModel.chooseCurrency(option) },
                )
            }
        }
    }
}

@Composable
private fun AccountsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeading(
            title = "Where do you keep your money?",
            body = "Add a wallet, a bank account, an e-wallet — whatever you actually " +
                "use. Starting balances make the running totals true from day one.",
        )

        state.accounts.forEach { draft ->
            AccountDraftCard(
                draft = draft,
                types = state.options?.accountTypes.orEmpty(),
                currency = state.currency,
                removable = state.accounts.size > 1,
                onRemove = { viewModel.removeAccount(draft.key) },
                onEdit = { transform -> viewModel.editAccount(draft.key, transform) },
            )
        }

        OutlinedButton(
            onClick = viewModel::addAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("Add another account")
        }
    }
}

@Composable
private fun AccountDraftCard(
    draft: AccountDraft,
    types: List<AccountTypeOption>,
    currency: Currency,
    removable: Boolean,
    onRemove: () -> Unit,
    onEdit: ((AccountDraft) -> AccountDraft) -> Unit,
) {
    var pickingType by remember { mutableStateOf(false) }
    val typeLabel = types.firstOrNull { it.value == draft.type }?.label ?: draft.type.wire

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = AppIcons.forAccount(draft.type),
                tint = MaterialTheme.colorScheme.primary,
                size = 36.dp,
            )

            Text(
                text = draft.name.ifBlank { "New account" },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )

            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove this account")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> onEdit { it.copy(name = value) } },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(10.dp))

        PickerField(
            label = "Type",
            value = typeLabel,
            placeholder = "Choose a type",
            onClick = { pickingType = true },
        )

        Spacer(modifier = Modifier.height(10.dp))

        MoneyField(
            value = draft.balance,
            onValueChange = { value -> onEdit { it.copy(balance = value) } },
            currency = currency,
            label = if (draft.isLiability) "Balance owed now" else "Balance right now",
        )

        if (draft.isLiability) {
            Spacer(modifier = Modifier.height(10.dp))

            MoneyField(
                value = draft.creditLimit,
                onValueChange = { value -> onEdit { it.copy(creditLimit = value) } },
                currency = currency,
                label = "Credit limit (optional)",
            )
        }
    }

    if (pickingType) {
        OptionSheet(
            title = "Account type",
            options = types.map { it.value to it.label },
            selected = draft.type,
            onSelect = { type ->
                onEdit { it.copy(type = type) }
                pickingType = false
            },
            onDismiss = { pickingType = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val groups: List<CatalogGroup> = remember(state.options) { viewModel.expenseCatalog() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeading(
            title = "What do you spend on?",
            body = "Pick the categories you want to track. The ones most people use are " +
                "already ticked, and you can add or rename any of them later.",
        )

        groups.forEach { group ->
            val names = group.categories.map { it.name }
            val allChosen = names.all { it in state.chosenCategories }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = AppIcons.named(group.icon),
                        tint = MaterialTheme.colorScheme.primary,
                        size = 32.dp,
                    )

                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )

                    TextButton(onClick = { viewModel.toggleGroup(names) }) {
                        Text(if (allChosen) "None" else "All")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group.categories.forEach { category ->
                        FilterChip(
                            selected = category.name in state.chosenCategories,
                            onClick = { viewModel.toggleCategory(category.name) },
                            label = { Text(category.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeading(
            title = "Set a monthly limit?",
            body = "An overall figure is enough to start. Per-category limits can be " +
                "added on the budget screen once there is some spending to go on.",
        )

        SectionCard {
            MoneyField(
                value = state.overallBudget,
                onValueChange = viewModel::onBudgetChange,
                currency = state.currency,
                label = "Monthly budget (optional)",
                imeAction = ImeAction.Done,
            )

            Text(
                text = "Leave it empty to skip. Nothing is locked in — a budget can be " +
                    "changed any month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
