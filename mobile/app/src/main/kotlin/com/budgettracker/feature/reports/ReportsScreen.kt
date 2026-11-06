package com.budgettracker.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.DailyFlow
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.MonthlyTrend
import com.budgettracker.ui.components.AmountDisplay
import com.budgettracker.ui.components.BarValue
import com.budgettracker.ui.components.CategoryDonut
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.CompactSegmented
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorBanner
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.InsightBanner
import com.budgettracker.ui.components.InsightTile
import com.budgettracker.ui.components.HeroCard
import com.budgettracker.ui.components.MonthChip
import com.budgettracker.ui.components.OverlineLabel
import com.budgettracker.ui.components.RoundIconButton
import com.budgettracker.ui.components.ScreenHeader
import com.budgettracker.ui.components.SectionTitle
import com.budgettracker.ui.components.SegmentedChoice
import com.budgettracker.ui.components.SoftCard
import com.budgettracker.ui.components.Sparkline
import com.budgettracker.ui.components.StatusChip
import com.budgettracker.ui.components.TabSurface
import com.budgettracker.ui.components.ValueBarChart
import com.budgettracker.ui.theme.LocalThemeController
import com.budgettracker.ui.theme.MoneyDisplay
import com.budgettracker.ui.theme.accents
import com.budgettracker.ui.theme.money
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far back the bar chart looks. */
private enum class Range(val label: String) {
    Week("W"),
    Month("M"),
    Year("Y"),
}

/**
 * What the money has been doing: the month's total and its shape, how that
 * compares with last month, and where it concentrated.
 *
 * Each block answers one question on one scale — the total and its trend,
 * the run of days or months, the share by category — so nothing has to be
 * read against two axes at once.
 */
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalThemeController.current
    var range by rememberSaveable { mutableStateOf(Range.Week) }

    val comparison = state.comparison()

    TabSurface { padding ->
        Column(
            modifier = Modifier.padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            ScreenHeader(
                title = "Insights",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            ) {
                MonthChip(month = state.month, onMonthChange = viewModel::showMonth)

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

            if (state.loading) {
                CentredBox { FullScreenLoader() }

                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { error ->
                    item { ErrorBanner(message = error.message) }
                }

                item {
                    SpendCard(
                        total = state.cashFlow.totals.formatted.expense,
                        comparison = comparison,
                        daily = state.daysSoFar().expenseHeights(),
                    )
                }

                comparison?.sentence(state.currency)?.let { sentence ->
                    item {
                        InsightBanner(
                            message = sentence,
                            icon = if (comparison.spentLess) {
                                Icons.Outlined.Celebration
                            } else {
                                Icons.Outlined.Bolt
                            },
                        )
                    }
                }

                item {
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(
                                title = when (range) {
                                    Range.Week -> "This week"
                                    Range.Month -> "This month"
                                    Range.Year -> "Last six months"
                                },
                                modifier = Modifier.weight(1f),
                            )

                            CompactSegmented(
                                options = Range.entries.map { it to it.label },
                                selected = range,
                                onSelect = { range = it },
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val bars = state.bars(range)

                        if (bars.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.BarChart,
                                title = "Nothing in this period",
                                message = "Record some entries and the shape of the month " +
                                    "appears here.",
                            )
                        } else {
                            ValueBarChart(
                                bars = bars,
                                // The heaviest bar is the one worth finding,
                                // and it is the only thing the accent is
                                // spent on here. A period with no spending
                                // has no heaviest day, so nothing is picked
                                // out rather than the first bar arbitrarily.
                                highlight = bars.indices
                                    .maxByOrNull { bars[it].value }
                                    ?.takeIf { bars[it].value > 0f },
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InsightTile(
                            label = if (state.type == CategoryType.Expense) {
                                "Top spending"
                            } else {
                                "Top earner"
                            },
                            value = state.categories.firstOrNull()?.name ?: "—",
                            icon = Icons.Outlined.Category,
                            tint = MaterialTheme.accents.accent,
                            modifier = Modifier.weight(1f),
                        )

                        InsightTile(
                            label = "Daily avg",
                            value = Money.format(state.dailyAverageMinor(), state.currency),
                            icon = Icons.Outlined.Bolt,
                            tint = MaterialTheme.accents.category(4),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    SoftCard {
                        SegmentedChoice(
                            options = listOf(
                                CategoryType.Expense to "Spending",
                                CategoryType.Income to "Income",
                            ),
                            selected = state.type,
                            onSelect = viewModel::showType,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (state.categories.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.BarChart,
                                title = "Nothing in this month",
                                message = "Record some entries and the breakdown appears here.",
                            )
                        } else {
                            CategoryDonut(
                                breakdown = state.categories,
                                centreLabel = if (state.type == CategoryType.Expense) {
                                    "Spent"
                                } else {
                                    "Earned"
                                },
                                centreValue = if (state.type == CategoryType.Expense) {
                                    state.cashFlow.totals.formatted.expense
                                } else {
                                    state.cashFlow.totals.formatted.income
                                },
                                currency = state.currency,
                                limit = 8,
                            )
                        }
                    }
                }

                if (state.merchants.isNotEmpty()) {
                    item {
                        SectionTitle(title = "Where you spent most")
                    }

                    item {
                        SoftCard {
                            state.merchants.forEach { merchant ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = merchant.merchant,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )

                                        Text(
                                            text = "${merchant.visits} " +
                                                if (merchant.visits == 1) "visit" else "visits",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Text(
                                        text = merchant.formatted,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The month's spending on the dark card, with its shape underneath.
 *
 * The line carries no axis on purpose: the figure above it is the only value
 * worth reading off, and the line is there to say whether the month has been
 * steady or spiky.
 */
@Composable
private fun SpendCard(
    total: String,
    comparison: Comparison?,
    daily: List<Float>,
) {
    val accents = MaterialTheme.accents
    val money = MaterialTheme.money

    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlineLabel(
                text = "Spent this month",
                color = accents.inkMuted,
                modifier = Modifier.weight(1f),
            )

            if (comparison != null) {
                StatusChip(
                    text = "${comparison.percent}% vs last",
                    icon = if (comparison.spentLess) {
                        Icons.AutoMirrored.Outlined.TrendingDown
                    } else {
                        Icons.AutoMirrored.Outlined.TrendingUp
                    },
                    container = if (comparison.spentLess) {
                        money.positive.copy(alpha = 0.18f)
                    } else {
                        money.negative.copy(alpha = 0.18f)
                    },
                    content = if (comparison.spentLess) money.positive else money.negative,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AmountDisplay(formatted = total, style = MoneyDisplay, color = accents.onInk)

        if (daily.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))

            // The accent is the one colour on this card that is not ink, and
            // the line is what it is spent on: spending drawn in the hue the
            // rest of the app spends money in. The dots mark the two ends and
            // the month's heaviest day, and the dashed verticals give the
            // curve something to be read against without an axis.
            Sparkline(
                values = daily,
                colour = accents.accent,
                markerFill = accents.inkBead,
                guide = accents.onInk.copy(alpha = 0.13f),
                modifier = Modifier.height(74.dp),
            )
        }
    }
}

/** This month's spending against last month's. */
private data class Comparison(
    val percent: Int,
    val differenceMinor: Long,
    val spentLess: Boolean,
) {
    /** The banner's sentence, or nothing when the change is not worth one. */
    fun sentence(currency: Currency): String? {
        if (percent == 0) return null

        val amount = Money.format(abs(differenceMinor), currency)

        return if (spentLess) {
            "You spent $amount less than last month. Keep it up."
        } else {
            "You spent $amount more than last month."
        }
    }
}

/**
 * The selected month against the one before it, from the trend series the
 * screen already loaded — a month with nothing to compare against gets no
 * comparison rather than a made-up zero.
 */
private fun ReportsUiState.comparison(): Comparison? {
    val current = trends.forMonth(month) ?: return null
    val previous = trends.forMonth(month.minusMonths(1)) ?: return null

    if (previous.expenseMinor <= 0L) return null

    val difference = current.expenseMinor - previous.expenseMinor
    val percent = (difference * 100.0 / previous.expenseMinor).roundToInt()

    return Comparison(
        percent = abs(percent),
        differenceMinor = difference,
        spentLess = difference < 0L,
    )
}

private fun List<MonthlyTrend>.forMonth(month: YearMonth): MonthlyTrend? {
    val wire = Dates.wireMonth(month)

    return firstOrNull { it.period == wire || it.period.startsWith(wire) }
}

/**
 * The days of the month that have actually happened.
 *
 * The report covers the whole month, so a month in progress arrives with its
 * remaining days present and empty. Counting those would flatten every
 * average and fill "this week" with zeroes from a week that has not come
 * yet, so they are dropped — a past month keeps all of its days.
 */
private fun ReportsUiState.daysSoFar(): List<DailyFlow> {
    val today = Dates.today()

    val elapsed = cashFlow.daily.filter { day ->
        Dates.parse(day.date)?.isAfter(today) != true
    }

    return elapsed.ifEmpty { cashFlow.daily }
}

/** Spending per day, as the sparkline wants it: just the heights. */
private fun List<DailyFlow>.expenseHeights(): List<Float> =
    map { it.expenseMinor.toFloat() }

/** What was spent per day on average, over the days the month has had. */
private fun ReportsUiState.dailyAverageMinor(): Long {
    val days = daysSoFar().size.takeIf { it > 0 } ?: return 0L

    return cashFlow.totals.expenseMinor / days
}

/**
 * The bars for the chosen range.
 *
 * A week is the last seven days with data, a month is its weeks, and a year
 * is the trend series — three views of the same ledger, each few enough to
 * label every bar.
 */
private fun ReportsUiState.bars(range: Range): List<BarValue> = when (range) {
    Range.Week -> daysSoFar()
        .takeLast(7)
        .mapNotNull { day ->
            val date = Dates.parse(day.date) ?: return@mapNotNull null

            BarValue(
                label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                value = day.expenseMinor.toFloat(),
                caption = Money.formatCompact(day.expenseMinor, currency),
            )
        }

    Range.Month -> daysSoFar()
        .chunked(7)
        .mapIndexed { index, week ->
            val total = week.sumOf { it.expenseMinor }

            BarValue(
                label = "W${index + 1}",
                value = total.toFloat(),
                caption = Money.formatCompact(total, currency),
            )
        }

    Range.Year -> trends.map { trend ->
        BarValue(
            label = trend.label.substringBefore(' ').take(3),
            value = trend.expenseMinor.toFloat(),
            caption = Money.formatCompact(trend.expenseMinor, currency),
        )
    }
}
