package com.budgettracker.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.budgettracker.ui.components.AccentGlow
import com.budgettracker.ui.components.IconBadge
import com.budgettracker.ui.components.ScreenHeader
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.components.TabSurface
import com.budgettracker.ui.theme.accents

/**
 * The rest of the app.
 *
 * Four tabs cover what gets used daily; everything else is one tap from here
 * rather than crowded into a bar nobody can read.
 *
 * The three cards are the grouping: what you look things up in, what runs on
 * its own, and what you change. Nothing here is a figure, so this is the one
 * screen that can spend its top corner on light rather than on a total.
 */
@Composable
fun MoreScreen(
    onOpenAccounts: () -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accents = MaterialTheme.accents

    TabSurface { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScreenHeader(
                    title = "More",
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 8.dp,
                    ),
                )

                MoreGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "Receipts",
                        subtitle = "Scan one, or check a draft you have not recorded yet",
                        onClick = onOpenReceipts,
                    )

                    MoreRow(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "Accounts",
                        subtitle = "Balances, cards and what you are worth",
                        onClick = onOpenAccounts,
                    )

                    MoreRow(
                        icon = Icons.Outlined.BarChart,
                        title = "Reports",
                        subtitle = "Where the money went, month by month",
                        onClick = onOpenReports,
                        last = true,
                    )
                }

                MoreGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "Bills",
                        subtitle = "Due dates and reminders",
                        onClick = onOpenBills,
                    )

                    MoreRow(
                        icon = Icons.Outlined.Savings,
                        title = "Savings goals",
                        subtitle = "What you are putting money aside for",
                        onClick = onOpenSavings,
                    )

                    MoreRow(
                        icon = Icons.Outlined.Repeat,
                        title = "Recurring",
                        subtitle = "Entries that repeat on their own",
                        onClick = onOpenRecurring,
                        last = true,
                    )
                }

                MoreGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        icon = Icons.Outlined.Settings,
                        title = "Settings",
                        subtitle = "Currency, app lock and your account",
                        onClick = onOpenSettings,
                        last = true,
                    )
                }

                Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 48.dp))
            }

            // The light is laid over the page rather than under it, so it
            // falls across the corner of the first card the way it falls
            // across the window — a glow that stopped at the card's edge
            // would only draw the edge.
            //
            // It is hung off the top right of the window, above the status
            // bar's own inset, and given well under half the width: the glow
            // is sized from what it is drawn in, so at the full width of the
            // page it reaches most of the way down the first card and the
            // screen's title ends up inside it. A corner is the point.
            AccentGlow(
                accent = accents.accent,
                strength = if (accents.dark) 1f else 0.4f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.64f)
                    .height(230.dp),
            )
        }
    }
}

/** One of the three cards: rows under a single rounded surface. */
@Composable
private fun MoreGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SectionCard(modifier = modifier, padding = 0.dp) {
        Spacer(modifier = Modifier.height(4.dp))

        content()

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * One way out of this screen: what it is, what is behind it, and the arrow
 * that says it leads somewhere.
 *
 * The badge is a rounded square rather than a circle — these are places in
 * the app, and the squarer mark is what the rest of the app gives a thing
 * rather than a person.
 */
@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = icon,
            tint = MaterialTheme.colorScheme.primary,
            size = 46.dp,
            shape = RoundedCornerShape(15.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }

    // The rule starts where the words do, so the badges read as a column of
    // their own rather than as the first cell of a table.
    if (!last) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 76.dp),
        )
    }
}
