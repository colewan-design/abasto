package com.budgettracker.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.budgettracker.core.money.Money
import com.budgettracker.data.model.TransactionFilters
import com.budgettracker.ui.components.OptionalDateField
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.core.time.Dates

/**
 * Narrowing the ledger.
 *
 * The sheet edits a copy and applies it on confirm, so half-chosen filters
 * never fire a request — and dismissing it leaves the list exactly as it was.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    state: ActivityUiState,
    onApply: (TransactionFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(state.filters) }
    var minAmount by remember {
        mutableStateOf(
            state.filters.minAmountMinor?.let { Money.majorInput(it, state.currency.exponent) }.orEmpty(),
        )
    }
    var maxAmount by remember {
        mutableStateOf(
            state.filters.maxAmountMinor?.let { Money.majorInput(it, state.currency.exponent) }.orEmpty(),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(
                title = "Filters",
                action = {
                    TextButton(onClick = { draft = draft.cleared() }) { Text("Reset") }
                },
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilterGroup(title = "Show") {
                    TransactionFilters.typeOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = draft.type == value,
                            onClick = { draft = draft.copy(type = value) },
                            label = { Text(label) },
                        )
                    }
                }

                if (state.accounts.isNotEmpty()) {
                    FilterGroup(title = "Accounts") {
                        state.accounts.forEach { account ->
                            FilterChip(
                                selected = account.id in draft.accountIds,
                                onClick = {
                                    draft = draft.copy(
                                        accountIds = draft.accountIds.toggle(account.id),
                                    )
                                },
                                label = { Text(account.name) },
                            )
                        }
                    }
                }

                if (state.categories.isNotEmpty()) {
                    FilterGroup(title = "Categories") {
                        state.categories.forEach { category ->
                            FilterChip(
                                selected = category.id in draft.categoryIds,
                                onClick = {
                                    draft = draft.copy(
                                        categoryIds = draft.categoryIds.toggle(category.id),
                                    )
                                },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }

                if (state.tags.isNotEmpty()) {
                    FilterGroup(title = "Tags") {
                        state.tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in draft.tagIds,
                                onClick = {
                                    draft = draft.copy(tagIds = draft.tagIds.toggle(tag.id))
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionalDateField(
                        date = draft.from,
                        onDateChange = { draft = draft.copy(from = it) },
                        label = "From",
                        fallback = Dates.today().withDayOfMonth(1),
                        modifier = Modifier.weight(1f),
                    )

                    OptionalDateField(
                        date = draft.to,
                        onDateChange = { draft = draft.copy(to = it) },
                        label = "To",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyField(
                        value = minAmount,
                        onValueChange = { minAmount = it },
                        currency = state.currency,
                        label = "Least",
                        modifier = Modifier.weight(1f),
                    )

                    MoneyField(
                        value = maxAmount,
                        onValueChange = { maxAmount = it },
                        currency = state.currency,
                        label = "Most",
                        modifier = Modifier.weight(1f),
                    )
                }

                ToggleRow(
                    label = "Only entries with a receipt",
                    checked = draft.hasReceipt == true,
                    onCheckedChange = { draft = draft.copy(hasReceipt = it.takeIf { on -> on }) },
                )

                ToggleRow(
                    label = "Only uncategorised",
                    checked = draft.uncategorized == true,
                    onCheckedChange = { draft = draft.copy(uncategorized = it.takeIf { on -> on }) },
                )

                FilterGroup(title = "Sort by") {
                    listOf(
                        "occurred_on" to "Date",
                        "amount" to "Amount",
                        "merchant" to "Merchant",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = draft.sort == value,
                            onClick = { draft = draft.copy(sort = value) },
                            label = { Text(label) },
                        )
                    }
                }

                Button(
                    onClick = {
                        onApply(
                            draft.copy(
                                minAmountMinor = Money.parse(minAmount, state.currency.exponent),
                                maxAmountMinor = Money.parse(maxAmount, state.currency.exponent),
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun List<Long>.toggle(id: Long): List<Long> =
    if (id in this) this - id else this + id
