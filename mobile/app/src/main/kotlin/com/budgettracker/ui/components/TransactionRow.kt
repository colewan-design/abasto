package com.budgettracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Transaction
import com.budgettracker.ui.icons.AppIcons
import com.budgettracker.ui.theme.hexColor

/**
 * One line in the ledger.
 *
 * The secondary line answers "which account, and what kind of entry" — for a
 * transfer it names both sides, which is the only way a collapsed transfer row
 * makes sense on its own.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The rounded square, not the circle: an entry is a thing that
        // happened — a shop, a service, a bill — and the squarer mark reads
        // closer to the app tile it was paid in. Circles are for people.
        val badge = RoundedCornerShape(14.dp)

        if (transaction.isTransfer) {
            IconBadge(
                icon = Icons.Outlined.SwapHoriz,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = badge,
            )
        } else {
            IconBadge(
                icon = AppIcons.named(transaction.category?.icon ?: transaction.category?.group?.icon),
                tint = hexColor(
                    transaction.category?.color ?: transaction.category?.group?.color,
                    MaterialTheme.colorScheme.primary,
                ),
                shape = badge,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (transaction.receiptId != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = "From a receipt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }

                if (transaction.recurringRuleId != null) {
                    Icon(
                        imageVector = Icons.Outlined.Repeat,
                        contentDescription = "Recurring",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Text(
                text = subtitle(transaction, showDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TransactionAmount(transaction = transaction)
    }
}

private fun subtitle(transaction: Transaction, showDate: Boolean): String {
    val parts = mutableListOf<String>()

    if (showDate) {
        parts += Dates.relativeOrRaw(transaction.occurredOn)
    }

    if (transaction.isTransfer) {
        val from = transaction.account?.name
        val to = transaction.transferAccount?.name

        parts += when {
            from != null && to != null -> "$from → $to"
            else -> "Transfer"
        }
    } else {
        transaction.category?.name?.let { parts += it }
        transaction.account?.name?.let { parts += it }
    }

    if (transaction.hasSplits) {
        parts += "${transaction.splits.size} categories"
    }

    return parts.joinToString(" · ")
}

/**
 * A date heading above the rows that fall under it.
 *
 * It is set as large as a section title rather than as a caption: the day is
 * what the ledger is divided by, and on a screen of near-identical rows the
 * headings are the only landmarks to scroll against.
 */
@Composable
fun DateHeader(
    label: String,
    total: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )

        if (total != null) {
            Text(
                text = total,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
