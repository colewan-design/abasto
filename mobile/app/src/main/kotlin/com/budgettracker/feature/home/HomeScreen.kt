package com.budgettracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.BudgetStatus
import com.budgettracker.data.model.DashboardBudget
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionType
import com.budgettracker.data.model.UpcomingBill
import com.budgettracker.ui.components.AmountDisplay
import com.budgettracker.ui.components.AvatarBubble
import com.budgettracker.ui.components.Brandmark
import com.budgettracker.ui.components.CategoryDonut
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.HeroCard
import com.budgettracker.ui.components.IconBadge
import com.budgettracker.ui.components.OverlineLabel
import com.budgettracker.ui.components.ProgressMeter
import com.budgettracker.ui.components.QuickAction
import com.budgettracker.ui.components.RoundIconButton
import com.budgettracker.ui.components.SectionTitle
import com.budgettracker.ui.components.SoftCard
import com.budgettracker.ui.components.StatusChip
import com.budgettracker.ui.components.TabSurface
import com.budgettracker.ui.components.TransactionRow
import com.budgettracker.ui.components.WarningBanner
import com.budgettracker.ui.icons.AppIcons
import com.budgettracker.ui.theme.LocalThemeController
import com.budgettracker.ui.theme.MoneyDisplay
import com.budgettracker.ui.theme.MoneyLarge
import com.budgettracker.ui.theme.accents
import com.budgettracker.ui.theme.hexColor
import com.budgettracker.ui.theme.money

/**
 * What is happening with the money right now: what there is, what can be
 * done with it, how the month's budget is holding, and the last few entries.
 *
 * The balance sits on the one dark card in the app, with the four things
 * most often done next directly underneath it — recording something is two
 * taps from launch, not a hunt for a button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTransaction: (Transaction) -> Unit,
    onRecord: (TransactionType) -> Unit,
    onSeeAllActivity: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    TabSurface { padding ->
        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            dashboard == null -> CentredBox(padding = padding) {
                state.error?.let { error ->
                    ErrorState(error = error, onRetry = viewModel::refresh)
                }
            }

            // Everything here comes from one endpoint, so pulling the page
            // down is the whole refresh — there is no per-card reload to
            // confuse it with, and the gesture replaces an icon the design
            // has no room for.
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        BrandRow(
                            initials = state.user?.initials ?: "?",
                            unread = state.unreadNotifications,
                            onOpenNotifications = onOpenNotifications,
                            onOpenProfile = onOpenMore,
                        )
                    }

                    if (state.isDemo) {
                        item {
                            WarningBanner(
                                message = "You are exploring the demo. Sign in with Google to " +
                                    "keep what you add.",
                                icon = Icons.Outlined.AutoAwesome,
                                // Signing in means leaving the demo account,
                                // which is done from the profile — the banner
                                // opens it rather than only naming it.
                                onClick = onOpenMore,
                            )
                        }
                    }

                    state.error?.let { error ->
                        item {
                            WarningBanner(
                                message = error.message,
                                actionLabel = "Retry",
                                onAction = viewModel::refresh,
                            )
                        }
                    }

                    item {
                        BalanceCard(
                            netWorth = dashboard.balances.formatted.netWorth,
                            available = dashboard.balances.formatted.available,
                            owed = dashboard.balances.formatted.owed,
                            netMinor = dashboard.cashFlow.netMinor,
                            netFormatted = dashboard.cashFlow.formatted.net,
                            onOpenAccounts = onOpenAccounts,
                            onRecord = onRecord,
                            onOpenMore = onOpenMore,
                        )
                    }

                    dashboard.budget?.let { budget ->
                        item {
                            BudgetCard(
                                budget = budget,
                                // "October", not "October 2025": the card is
                                // about the month being lived in, and the year
                                // is only noise on a screen about right now.
                                month = Dates.monthLabel(state.month).substringBefore(' '),
                                currency = state.currency,
                                onClick = onOpenBudget,
                            )
                        }
                    }

                    if (dashboard.upcomingBills.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Coming up",
                                trailing = "All bills",
                                onTrailingClick = onOpenBills,
                            )
                        }

                        item {
                            val bills = dashboard.upcomingBills.take(3)

                            SoftCard(padding = 16.dp) {
                                bills.forEachIndexed { index, bill ->
                                    BillRow(bill = bill)

                                    if (index != bills.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (dashboard.savingsGoals.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Savings",
                                trailing = "All goals",
                                onTrailingClick = onOpenSavings,
                            )
                        }

                        item {
                            SoftCard {
                                dashboard.savingsGoals.take(3).forEachIndexed { index, goal ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = goal.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                        )

                                        Text(
                                            text = "${goal.formatted.saved} / ${goal.formatted.target}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(7.dp))

                                    ProgressMeter(fraction = goal.progress.toFloat())

                                    if (index != dashboard.savingsGoals.take(3).lastIndex) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (dashboard.topCategories.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Where it went",
                                trailing = "Insights",
                                onTrailingClick = onOpenInsights,
                            )
                        }

                        item {
                            SoftCard {
                                CategoryDonut(
                                    breakdown = dashboard.topCategories,
                                    centreLabel = "Spent",
                                    centreValue = dashboard.cashFlow.formatted.expense,
                                    currency = state.currency,
                                )
                            }
                        }
                    }

                    item {
                        SectionTitle(
                            title = "Recent activity",
                            trailing = "See all",
                            onTrailingClick = onSeeAllActivity,
                        )
                    }

                    item {
                        SoftCard(padding = 0.dp) {
                            if (dashboard.recentTransactions.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    title = "Nothing recorded yet",
                                    message = "Add an expense or scan a receipt and it will " +
                                        "show up here.",
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))

                                dashboard.recentTransactions.forEachIndexed { index, transaction ->
                                    TransactionRow(
                                        transaction = transaction,
                                        showDate = true,
                                        onClick = { onOpenTransaction(transaction) },
                                    )

                                    if (index != dashboard.recentTransactions.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.padding(start = 68.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The app's mark and name, the theme switch, the bell, and whoever is signed
 * in.
 *
 * Four things share this line with the app's name, so they are spaced a
 * little tighter than the rest of the screen — at the usual gap the name is
 * what gives way, and the name is the one thing here that cannot shrink.
 */
@Composable
private fun BrandRow(
    initials: String,
    unread: Int,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val theme = LocalThemeController.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Brandmark()

        Text(
            text = "Abasto",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )

        RoundIconButton(
            icon = if (theme.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = if (theme.isDark) {
                "Switch to the light theme"
            } else {
                "Switch to the dark theme"
            },
            onClick = theme::toggle,
        )

        RoundIconButton(
            icon = Icons.Outlined.NotificationsNone,
            contentDescription = "Notifications",
            onClick = onOpenNotifications,
            badge = unread.takeIf { it > 0 }?.coerceAtMost(99)?.toString(),
        )

        AvatarBubble(initials = initials, onClick = onOpenProfile)
    }
}

/**
 * One bill that is about to fall due: what it is, when, and for how much.
 *
 * The badge carries the bill's category — the icon and hue the rest of the
 * app already uses for that kind of spending — so the list is scannable by
 * shape before it is read, and a bill looks the same here as in the activity
 * row it will become once it is paid.
 */
@Composable
private fun BillRow(bill: UpcomingBill) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = AppIcons.named(bill.icon),
            tint = hexColor(bill.color, MaterialTheme.colorScheme.primary),
            size = 42.dp,
            shape = RoundedCornerShape(14.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = bill.name, style = MaterialTheme.typography.titleSmall)

            Text(
                text = Dates.dueLabel(bill.daysUntilDue),
                style = MaterialTheme.typography.bodySmall,
                color = if (bill.isOverdue) {
                    MaterialTheme.money.negative
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Text(text = bill.formatted, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Net worth as the hero figure, with the two halves it is made of underneath —
 * money available and money owed — because one number alone hides whether a
 * healthy total is really a big balance or a big debt.
 *
 * The four buttons are the whole point of the card: every one of them records
 * or finds money, and they are the shortest path to each.
 */
@Composable
private fun BalanceCard(
    netWorth: String,
    available: String,
    owed: String,
    netMinor: Long,
    netFormatted: String,
    onOpenAccounts: () -> Unit,
    onRecord: (TransactionType) -> Unit,
    onOpenMore: () -> Unit,
) {
    val accents = MaterialTheme.accents
    val money = MaterialTheme.money

    HeroCard {
        // The corner opposite the chip is left to the accent wash. Nothing
        // sits in it: a dark button in the brightest part of the card is the
        // one place on this screen a control would be harder to see, and
        // insights are a tab of their own.
        StatusChip(
            text = "All accounts",
            icon = Icons.Outlined.ExpandMore,
            container = accents.inkRaised,
            content = accents.onInk,
            onClick = onOpenAccounts,
        )

        Spacer(modifier = Modifier.height(18.dp))

        OverlineLabel(text = "Total balance", color = accents.inkMuted)

        Spacer(modifier = Modifier.height(4.dp))

        AmountDisplay(formatted = netWorth, style = MoneyDisplay, color = accents.onInk)

        Spacer(modifier = Modifier.height(10.dp))

        StatusChip(
            text = "$netFormatted this month",
            icon = if (netMinor < 0L) {
                Icons.AutoMirrored.Outlined.TrendingDown
            } else {
                Icons.AutoMirrored.Outlined.TrendingUp
            },
            container = if (netMinor < 0L) {
                money.negative.copy(alpha = 0.18f)
            } else {
                money.positive.copy(alpha = 0.18f)
            },
            content = if (netMinor < 0L) money.negative else money.positive,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickAction(
                icon = Icons.Outlined.Add,
                label = "Add",
                onClick = { onRecord(TransactionType.Expense) },
            )

            QuickAction(
                icon = Icons.Outlined.ArrowUpward,
                label = "Move",
                onClick = { onRecord(TransactionType.Transfer) },
            )

            QuickAction(
                icon = Icons.Outlined.ArrowDownward,
                label = "Income",
                onClick = { onRecord(TransactionType.Income) },
            )

            QuickAction(
                icon = Icons.Outlined.Apps,
                label = "More",
                onClick = onOpenMore,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        HorizontalDivider(color = accents.onInk.copy(alpha = 0.10f))

        Spacer(modifier = Modifier.height(16.dp))

        // The two halves of the balance, each behind the icon for what it is:
        // a wallet for the money there is, a card for the money owed. The rule
        // between them is what stops the pair reading as one four-line column.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BalanceHalf(
                icon = Icons.Outlined.AccountBalanceWallet,
                label = "Available",
                value = available,
                valueColor = accents.onInk,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            )

            VerticalDivider(
                color = accents.onInk.copy(alpha = 0.10f),
                modifier = Modifier.height(30.dp),
            )

            BalanceHalf(
                icon = Icons.Outlined.CreditCard,
                label = "Owed",
                value = owed,
                valueColor = money.negative,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
        }
    }
}

/** One side of the balance footer: an icon, what it is, and how much. */
@Composable
private fun BalanceHalf(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.accents

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accents.inkMuted,
            modifier = Modifier.size(22.dp),
        )

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accents.inkMuted,
            )

            // A balance in the millions runs past half a phone's width, and a
            // figure cut mid-digit is worse than one that says it was cut.
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The month's budget in one line of figures and one bar. */
@Composable
private fun BudgetCard(
    budget: DashboardBudget,
    month: String,
    currency: Currency,
    onClick: () -> Unit,
) {
    val overall = budget.overall
    val accents = MaterialTheme.accents
    val money = MaterialTheme.money

    // The dashboard sends the budget block as figures only — unlike the
    // budget endpoint, it carries no pre-formatted strings — so these are
    // rendered from the minor units rather than left blank.
    val spent = overall?.formatted?.spent?.takeIf { it.isNotBlank() }
        ?: overall?.let { Money.format(it.spentMinor, currency) }
    val limit = overall?.formatted?.limit?.takeIf { it.isNotBlank() }
        ?: overall?.limitMinor?.let { Money.format(it, currency) }
    val remaining = overall?.formatted?.remaining?.takeIf { it.isNotBlank() }
        ?: overall?.remainingMinor?.let { Money.format(it, currency) }

    // The icon carries the same thing the colour does, for the sake of anyone
    // the colour does not reach.
    val status = when (overall?.status) {
        BudgetStatus.Exceeded ->
            Triple("Over budget", money.negative, Icons.Outlined.ErrorOutline)

        BudgetStatus.Approaching ->
            Triple("Close to the limit", money.warning, Icons.Outlined.WarningAmber)

        BudgetStatus.Ok ->
            Triple("On track", money.positive, Icons.Outlined.CheckCircle)

        else ->
            Triple("No limit set", MaterialTheme.colorScheme.onSurfaceVariant, null)
    }

    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$month budget",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )

            StatusChip(
                text = status.first,
                icon = status.third,
                container = status.second.copy(alpha = 0.14f),
                content = status.second,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            AmountDisplay(formatted = spent.orEmpty(), style = MoneyLarge)

            // Both captions are a size below the body text. Three figures and
            // a rule have to share one line next to a 32sp total, and at body
            // size the last of them wraps on a narrow phone.
            if (limit != null) {
                Text(
                    text = "  of $limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (remaining != null) {
                // Without a rule between them, "of ₱40,000.00 ₱16,605.27 left"
                // runs together into one phrase about one number.
                if (limit != null) {
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .padding(start = 9.dp, end = 9.dp, bottom = 6.dp)
                            .height(13.dp),
                    )
                }

                Text(
                    text = "$remaining left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ProgressMeter(
            fraction = overall?.percentUsed?.toFloat()
                ?: Money.share(overall?.spentMinor ?: 0L, overall?.limitMinor ?: 0L),
            height = 10.dp,
            colour = if (overall?.status == BudgetStatus.Exceeded) money.negative else accents.accent,
            track = accents.track,
        )
    }
}
