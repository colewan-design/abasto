package com.budgettracker.feature.receipts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.budgettracker.data.model.ReceiptItem
import com.budgettracker.data.model.ReceiptStatus
import com.budgettracker.ui.components.AccountBadge
import com.budgettracker.ui.components.AccountPickerSheet
import com.budgettracker.ui.components.CategoryBadge
import com.budgettracker.ui.components.CategoryPickerSheet
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.DateField
import com.budgettracker.ui.components.DetailScaffold
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FieldError
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.Pill
import com.budgettracker.ui.components.PickerField
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.components.WarningBanner
import com.budgettracker.ui.theme.money

/**
 * Checking a scan before it is recorded.
 *
 * The photo stays on screen so a figure can be compared against the paper it
 * came from, and anything the parser was unsure about is marked "check" — the
 * point of this screen is that a misread total is caught by a person, not
 * carried into the ledger.
 */
@Composable
fun ReceiptReviewScreen(
    onBack: () -> Unit,
    onRecorded: () -> Unit,
    viewModel: ReceiptReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingAccount by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingItem by remember { mutableStateOf<ReceiptItem?>(null) }

    LaunchedEffect(state.confirmedTransactionId) {
        if (state.confirmedTransactionId != null) onRecorded()
    }

    DetailScaffold(
        title = "Review receipt",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::dismissMessage,
        actions = {
            IconButton(onClick = viewModel::reprocess, enabled = !state.reprocessing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Read it again")
            }

            IconButton(onClick = { viewModel.delete(onBack) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete this receipt")
            }
        },
    ) { padding ->
        val receipt = state.receipt

        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            receipt == null -> CentredBox(padding = padding) {
                state.error?.let { error ->
                    ErrorState(error = error, onRetry = viewModel::load)
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                        ) {}

                        AsyncImage(
                            model = receipt.imageUrl,
                            contentDescription = "The scanned receipt",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                        )
                    }
                }

                if (receipt.duplicate.isDuplicate) {
                    item {
                        WarningBanner(
                            message = receipt.duplicate.reason
                                ?: "This looks like a receipt you have already recorded.",
                            actionLabel = if (state.acknowledgeDuplicate) null else "Record anyway",
                            onAction = viewModel::acknowledgeDuplicate,
                        )
                    }
                }

                if (receipt.status == ReceiptStatus.Failed) {
                    item {
                        WarningBanner(
                            message = receipt.extraction.failureReason
                                ?: "The receipt could not be read. Fill in the figures by hand, " +
                                "or try reading it again.",
                            actionLabel = "Read again",
                            onAction = viewModel::reprocess,
                        )
                    }
                }

                if (receipt.status == ReceiptStatus.Confirmed) {
                    item {
                        WarningBanner(
                            message = "This receipt has already been recorded as a transaction.",
                        )
                    }
                }

                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "What it says",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )

                            receipt.extraction.driver?.let { driver ->
                                Pill(text = "Read by $driver")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FieldLabel(label = "Merchant", flagged = state.flagged("merchant"))

                        OutlinedTextField(
                            value = state.merchant,
                            onValueChange = viewModel::onMerchantChange,
                            singleLine = true,
                            placeholder = { Text("Who it was paid to") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FieldLabel(label = "Total", flagged = state.flagged("total"))

                        MoneyField(
                            value = state.total,
                            onValueChange = viewModel::onTotalChange,
                            currency = state.currency,
                            label = "Total paid",
                            error = state.fieldErrors["total_minor"],
                        )

                        if (receipt.formatted.subtotal != null || receipt.formatted.tax != null) {
                            Text(
                                text = listOfNotNull(
                                    receipt.formatted.subtotal?.let { "Subtotal $it" },
                                    receipt.formatted.tax?.let { "Tax $it" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FieldLabel(label = "Date", flagged = state.flagged("receipt_date"))

                        DateField(
                            date = state.date,
                            onDateChange = viewModel::onDateChange,
                            label = "Date on the receipt",
                            allowFuture = false,
                        )
                    }
                }

                item {
                    SectionCard {
                        Text(
                            text = "How to record it",
                            style = MaterialTheme.typography.titleSmall,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PickerField(
                            label = "Paid from",
                            value = state.account?.name,
                            placeholder = "Choose an account",
                            onClick = { pickingAccount = true },
                            error = state.fieldErrors["account_id"],
                            leading = state.account?.let { account ->
                                { AccountBadge(account = account, size = 24.dp) }
                            },
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        PickerField(
                            label = if (state.splitByItem) "Fallback category" else "Category",
                            value = state.category?.name,
                            placeholder = "Choose a category",
                            onClick = { pickingCategory = true },
                            leading = state.category?.let { category ->
                                { CategoryBadge(category = category, size = 24.dp) }
                            },
                        )

                        state.meta.suggestedCategory?.category?.let { suggested ->
                            if (state.categoryId == suggested.id) {
                                Text(
                                    text = "Suggested from what you have recorded for this " +
                                        "merchant before.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.note,
                            onValueChange = viewModel::onNoteChange,
                            label = { Text("Note (optional)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (receipt.items.isNotEmpty()) {
                    item {
                        SectionCard(padding = 0.dp) {
                            Row(
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 12.dp,
                                    top = 14.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Split across ${state.distinctItemCategories} " +
                                            if (state.distinctItemCategories == 1) {
                                                "category"
                                            } else {
                                                "categories"
                                            },
                                        style = MaterialTheme.typography.titleSmall,
                                    )

                                    Text(
                                        text = if (state.splitByItem) {
                                            "The total is shared across the lines by category, " +
                                                "tax and discounts included."
                                        } else {
                                            "The whole receipt goes under one category."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Switch(
                                    checked = state.splitByItem,
                                    onCheckedChange = { viewModel.toggleSplitByItem() },
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            receipt.items.forEach { item ->
                                ItemRow(
                                    item = item,
                                    categoryName = state.categories
                                        .firstOrNull { it.id == state.itemCategories[item.id] }
                                        ?.name,
                                    enabled = state.splitByItem,
                                    onClick = { pickingItem = item },
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                item {
                    Button(
                        onClick = viewModel::confirm,
                        enabled = state.canConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Record this receipt")
                        }
                    }
                }

                if (state.blockedByDuplicate) {
                    item {
                        Text(
                            text = "Confirm you want to record it even though it looks like " +
                                "a duplicate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.money.warning,
                        )
                    }
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

    pickingItem?.let { item ->
        CategoryPickerSheet(
            categories = state.categories,
            selectedId = state.itemCategories[item.id],
            onSelect = { category ->
                viewModel.onItemCategoryChange(item.id, category?.id)
                pickingItem = null
            },
            onDismiss = { pickingItem = null },
            title = item.description.ifBlank { "This line" },
        )
    }
}

@Composable
private fun FieldLabel(label: String, flagged: Boolean) {
    val money = MaterialTheme.money

    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        if (flagged) {
            Pill(
                text = "Check this",
                container = money.warningContainer,
                content = money.warning,
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: ReceiptItem,
    categoryName: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.description.ifBlank { "Line ${item.lineNo}" },
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = when {
                    !enabled -> "Grouped with the rest"
                    categoryName != null -> categoryName
                    else -> "Tap to categorise"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled && categoryName == null) {
                    MaterialTheme.money.warning
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Text(text = item.formatted, style = MaterialTheme.typography.titleSmall)
    }
}
