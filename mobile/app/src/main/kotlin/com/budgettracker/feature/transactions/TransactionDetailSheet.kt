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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
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
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Transaction
import com.budgettracker.ui.components.CategoryBadge
import com.budgettracker.ui.components.MoneyTone
import com.budgettracker.ui.components.MoneyText
import com.budgettracker.ui.components.Pill
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.ui.theme.MoneyLarge

/**
 * One entry in full: what it was, where it came from, and how it got here.
 *
 * "How it got here" is worth showing — an entry created by a receipt scan or a
 * recurring rule behaves differently from one typed by hand, and knowing which
 * explains why a figure appeared without anyone entering it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onOpenReceipt: ((Long) -> Unit)? = null,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(title = transaction.title)

            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(
                        formatted = transaction.formattedAmount,
                        style = MoneyLarge,
                        tone = when {
                            transaction.isTransfer -> MoneyTone.Muted
                            transaction.isInflow -> MoneyTone.Positive
                            else -> MoneyTone.Neutral
                        },
                        modifier = Modifier.weight(1f),
                    )

                    Pill(text = transaction.type.label)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                DetailRow(label = "Date", value = Dates.relativeOrRaw(transaction.occurredOn))

                if (transaction.isTransfer) {
                    DetailRow(label = "From", value = transaction.account?.name ?: "—")
                    DetailRow(label = "To", value = transaction.transferAccount?.name ?: "—")
                } else {
                    DetailRow(label = "Account", value = transaction.account?.name ?: "—")

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )

                        if (transaction.category != null) {
                            CategoryBadge(category = transaction.category, size = 24.dp)
                        }

                        Text(
                            text = transaction.category?.name ?: "Uncategorised",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (transaction.hasSplits && transaction.splits.isNotEmpty()) {
                    Text(
                        text = "Split across categories",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    transaction.splits.forEach { split ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(category = split.category, size = 24.dp)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            ) {
                                Text(
                                    text = split.category?.name ?: "Uncategorised",
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                split.note?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Text(text = split.formatted, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                transaction.note?.takeIf { it.isNotBlank() }?.let { note ->
                    DetailRow(label = "Note", value = note)
                }

                if (transaction.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        transaction.tags.forEach { tag ->
                            SuggestionChip(onClick = {}, label = { Text(tag.name) })
                        }
                    }
                }

                DetailRow(
                    label = "Added",
                    value = when (transaction.createdVia) {
                        "receipt" -> "From a scanned receipt"
                        "recurring" -> "By a recurring rule"
                        "bill" -> "By marking a bill paid"
                        "import" -> "Imported"
                        "demo" -> "Sample data"
                        else -> "By hand"
                    },
                )

                if (transaction.receiptId != null && onOpenReceipt != null) {
                    AssistChip(
                        onClick = { onOpenReceipt(transaction.receiptId) },
                        label = { Text("View the receipt") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { onEdit(transaction) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )

                        Text(text = "Edit", modifier = Modifier.padding(start = 6.dp))
                    }

                    OutlinedButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )

                        Text(text = "Delete", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    text = if (transaction.isTransfer) {
                        "Both sides of the transfer are removed and the two balances go " +
                            "back to what they were."
                    } else {
                        "The account balance goes back to what it was. This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete(transaction)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.4f),
        )
    }
}
