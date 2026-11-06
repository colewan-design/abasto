package com.budgettracker.feature.budget

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.core.money.Money
import com.budgettracker.data.model.BudgetLine
import com.budgettracker.ui.components.AmountDisplay
import com.budgettracker.ui.components.CategoryBadge
import com.budgettracker.ui.components.CategoryTile
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorBanner
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.HeroCard
import com.budgettracker.ui.components.MoneyField
import com.budgettracker.ui.components.MonthChip
import com.budgettracker.ui.components.OverlineLabel
import com.budgettracker.ui.components.RoundIconButton
import com.budgettracker.ui.components.ScreenHeader
import com.budgettracker.ui.components.SectionTitle
import com.budgettracker.ui.components.SegmentBar
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.ui.components.SoftCard
import com.budgettracker.ui.components.Spend
import com.budgettracker.ui.components.TabSurface
import com.budgettracker.ui.icons.AppIcons
import com.budgettracker.ui.theme.LocalThemeController
import com.budgettracker.ui.theme.MoneyDisplay
import com.budgettracker.ui.theme.accents
import com.budgettracker.ui.theme.hexColor
import com.budgettracker.ui.theme.money

/**
 * The month's limits and how much of each is left.
 *
 * It is built around one figure — what is still spendable — and one bar that
 * says where the rest went. The bar and the cards below it share a colour per
 * category, so a slice can be traced to a card without reading a legend.
 *
 * A month with no budget is not an error: it offers to build one from what
 * was actually spent over the last three months, which is a far better
 * starting point than a blank form.
 */
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalThemeController.current

    TabSurface(
        message = state.message,
        onMessageShown = viewModel::messageShown,
    ) { padding ->
        Column(
            modifier = Modifier.padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            ScreenHeader(
                title = "Budget",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            ) {
                MonthChip(
                    month = state.month,
                    onMonthChange = viewModel::showMonth,
                    allowFuture = true,
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
            }

            when {
                state.loading -> CentredBox { FullScreenLoader() }

                state.error != null && !state.isSet -> CentredBox {
                    ErrorState(error = state.error!!, onRetry = { viewModel.load() })
                }

                !state.isSet -> CentredBox {
                    EmptyState(
                        icon = Icons.Outlined.PieChart,
                        title = "No budget for ${state.view.meta.label.ifBlank { "this month" }}",
                        message = if (state.suggestions.isEmpty()) {
                            "Set a limit per category and this screen will track what is " +
                                "left as you spend."
                        } else {
                            "Start from what you actually spent recently — ${state.suggestions.size} " +
                                "categories have a suggested limit ready."
                        },
                        actionLabel = if (state.suggestions.isEmpty()) {
                            "Set up a budget"
                        } else {
                            "Use suggested limits"
                        },
                        onAction = {
                            viewModel.startEditing()

                            if (state.suggestions.isNotEmpty()) {
                                viewModel.applySuggestions()
                            }
                        },
                    )
                }

                else -> BudgetBody(state = state, onEdit = viewModel::startEditing)
            }
        }
    }

    state.draft?.let { draft ->
        BudgetEditorSheet(
            state = state,
            draft = draft,
            onOverallChange = viewModel::onOverallChange,
            onLimitChange = viewModel::onLimitChange,
            onRemoveLimit = viewModel::removeLimit,
            onToggleRollover = viewModel::toggleRollover,
            onApplySuggestions = viewModel::applySuggestions,
            onSave = viewModel::save,
            onDismiss = viewModel::cancelEditing,
        )
    }
}

@Composable
private fun BudgetBody(state: BudgetUiState, onEdit: () -> Unit) {
    val money = MaterialTheme.money
    val overall = state.overall
    val lines = state.lines.sortedByDescending { it.spentMinor }

    // What the bar is measured against: the overall limit if one is set, and
    // otherwise the limits that were set per category, which is the only
    // other figure that means "the whole month".
    val scale = overall?.limitMinor
        ?: lines.sumOf { it.limitMinor }.takeIf { it > 0L }
        ?: lines.sumOf { it.spentMinor }.coerceAtLeast(1L)

    // Spending in a category that has a limit, against everything spent.
    // The two are rarely equal — a month always has spending outside the
    // budget — and the difference is what the grey tail on the bar is.
    val trackedSpent = lines.sumOf { it.spentMinor }
    val totalSpent = (overall?.spentMinor ?: trackedSpent).coerceAtLeast(trackedSpent)
    val untrackedSpent = totalSpent - trackedSpent

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val remaining = overall?.formatted?.remaining
            val accents = MaterialTheme.accents

            // The figure the screen is about belongs on the one dark card,
            // the way the balance and the month's spending do on the other
            // two tabs. Left loose on the page it was the only hero in the
            // app with nothing under it, and the colour of the cards below
            // ran straight into it.
            HeroCard {
                OverlineLabel(
                    text = if (remaining != null) "Left to spend" else "Spent this month",
                    color = accents.inkMuted,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    AmountDisplay(
                        formatted = remaining ?: overall?.formatted?.spent.orEmpty(),
                        style = MoneyDisplay,
                        color = if (overall?.remainingMinor != null && overall.remainingMinor < 0L) {
                            money.negative
                        } else {
                            accents.onInk
                        },
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(bottom = 5.dp),
                    ) {
                        Text(
                            text = overall?.formatted?.spent.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = accents.onInk,
                            textAlign = TextAlign.End,
                        )

                        Text(
                            text = overall?.formatted?.limit?.let { "of $it spent" }
                                ?: "no overall limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.inkMuted,
                            textAlign = TextAlign.End,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SegmentBar(
                    segments = lines.mapIndexed { index, line ->
                        Spend(
                            fraction = Money.share(line.spentMinor, scale),
                            colour = line.colour(index),
                        )
                    } + listOfNotNull(
                        // Without this the colours would stop well short of
                        // the figure above them and the bar would look like
                        // it was contradicting it.
                        untrackedSpent.takeIf { it > 0L }?.let {
                            Spend(
                                fraction = Money.share(it, scale),
                                // Taken out of the card rather than named in
                                // a grey of its own: a solid mid-grey is the
                                // heaviest thing on the light card, and this
                                // is the segment with the least to say.
                                colour = accents.onInk.copy(alpha = 0.30f),
                            )
                        },
                    ),
                    // The theme's track is mixed for a page, not for the one
                    // near-black card; on it, what is left of the month has
                    // to be cut out of the card itself.
                    track = accents.onInk.copy(alpha = 0.14f),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))

            SectionTitle(
                title = "Where it went",
                trailing = "${lines.size} " + if (lines.size == 1) "category" else "categories",
            )
        }

        if (lines.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.PieChart,
                    title = "No category limits",
                    message = "Add a limit per category to see what is left in each.",
                    actionLabel = "Add limits",
                    onAction = onEdit,
                )
            }
        }

        itemsIndexed(lines) { index, line ->
            CategoryTile(
                name = line.category?.name ?: "Uncategorised",
                icon = AppIcons.named(line.category?.icon ?: line.category?.group?.icon),
                colour = line.colour(index),
                amount = line.formatted.spent,
                caption = caption(line = line, totalSpent = totalSpent),
                limitLabel = line.formatted.limit?.let { "of $it" },
                flag = if (line.isOverspent) {
                    "OVER"
                } else if ((line.percentUsed ?: 0.0) >= 1.0) {
                    "MAXED"
                } else {
                    null
                },
                onClick = onEdit,
            )
        }

        if (state.view.meta.unbudgetedMinor > 0L) {
            item {
                SoftCard {
                    Text(text = "Outside the budget", style = MaterialTheme.typography.titleSmall)

                    Text(
                        text = "${Money.format(state.view.meta.unbudgetedMinor, state.currency)} " +
                            "was spent in categories with no limit set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Edit limits")
            }
        }
    }
}

/**
 * The hue a category is drawn in: the one its owner chose, and otherwise the
 * next one from the design's palette so a fresh account still reads as six
 * distinct categories rather than six grey cards.
 */
@Composable
private fun BudgetLine.colour(index: Int) =
    hexColor(category?.color ?: category?.group?.color, MaterialTheme.accents.category(index))

/** "31% of spend · 100% of limit", the two shares that explain the card. */
private fun caption(line: BudgetLine, totalSpent: Long): String {
    val ofSpend = if (totalSpent > 0L) {
        (line.spentMinor * 100.0 / totalSpent).toInt()
    } else {
        0
    }

    val ofLimit = line.percentUsed
        ?.let { (it * 100).toInt() }
        ?: (Money.share(line.spentMinor, line.limitMinor) * 100).toInt()

    return "$ofSpend% of spend · $ofLimit% of limit"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorSheet(
    state: BudgetUiState,
    draft: BudgetDraft,
    onOverallChange: (String) -> Unit,
    onLimitChange: (Long, String) -> Unit,
    onRemoveLimit: (Long) -> Unit,
    onToggleRollover: () -> Unit,
    onApplySuggestions: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetTitle(
                title = "Limits for ${state.view.meta.label.ifBlank { "this month" }}",
                action = {
                    TextButton(onClick = onSave, enabled = !draft.saving) { Text("Save") }
                },
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                draft.error?.let { message -> ErrorBanner(message = message) }

                MoneyField(
                    value = draft.overall,
                    onValueChange = onOverallChange,
                    currency = state.currency,
                    label = "Overall limit for the month",
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Carry leftovers forward",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Text(
                            text = "What is unspent in a category is added to next month's " +
                                "limit for it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(checked = draft.rollover, onCheckedChange = { onToggleRollover() })
                }

                if (state.suggestions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onApplySuggestions,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fill from recent spending")
                    }
                }

                Text(
                    text = "Per category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(category = category, size = 32.dp)

                        MoneyField(
                            value = draft.limits[category.id].orEmpty(),
                            onValueChange = { value -> onLimitChange(category.id, value) },
                            currency = state.currency,
                            label = category.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        )

                        if (draft.limits[category.id].orEmpty().isNotEmpty()) {
                            IconButton(onClick = { onRemoveLimit(category.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove this limit",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
