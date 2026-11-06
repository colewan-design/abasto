package com.budgettracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.DailyFlow
import com.budgettracker.data.model.MonthlyTrend
import com.budgettracker.ui.theme.money
import kotlin.math.max

/**
 * Income against spending, month by month.
 *
 * Both series are amounts in the same currency on one shared scale — there is
 * no second axis, so the bars can be compared by eye without being misled.
 * Green is money in and red is money out, the same meaning those colours carry
 * everywhere else in the app, and the legend names both so the pairing is
 * never left to colour alone.
 *
 * Tapping a month is the mobile stand-in for hovering: the readout above the
 * chart switches to that month instead of labelling every bar.
 */
@Composable
fun MonthlyTrendChart(
    trends: List<MonthlyTrend>,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val moneyColours = MaterialTheme.money
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant

    var selected by remember(trends) { mutableStateOf(trends.lastIndex) }
    val focus = trends.getOrNull(selected)
    val ceiling = remember(trends) {
        max(
            trends.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 0L,
            1L,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = focus?.label ?: "No data yet",
                    style = MaterialTheme.typography.titleSmall,
                )

                if (focus != null) {
                    Text(
                        text = "Net ${Money.format(focus.netMinor, currency, signed = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }

            if (focus != null) {
                Column(horizontalAlignment = Alignment.End) {
                    LegendValue(
                        swatch = moneyColours.positive,
                        label = "Income",
                        value = Money.format(focus.incomeMinor, currency),
                    )

                    LegendValue(
                        swatch = moneyColours.negative,
                        label = "Expenses",
                        value = Money.format(focus.expenseMinor, currency),
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .pointerInput(trends) {
                    detectTapGestures { offset ->
                        if (trends.isNotEmpty()) {
                            val slot = size.width.toFloat() / trends.size
                            selected = (offset.x / slot).toInt().coerceIn(0, trends.lastIndex)
                        }
                    }
                },
        ) {
            if (trends.isEmpty()) return@Canvas

            val baseline = size.height - 1f
            val slot = size.width / trends.size
            val gap = 2.dp.toPx()
            val barWidth = ((slot - gap * 3f) / 2f).coerceAtLeast(3f)
            val radius = 4.dp.toPx()

            drawLine(
                color = grid,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1f,
            )

            trends.forEachIndexed { index, trend ->
                val left = slot * index + gap
                val dim = index != selected && trends.size > 1

                drawBar(
                    left = left,
                    width = barWidth,
                    baseline = baseline,
                    fraction = trend.incomeMinor.toFloat() / ceiling.toFloat(),
                    colour = moneyColours.positive.copy(alpha = if (dim) 0.45f else 1f),
                    radius = radius,
                )

                drawBar(
                    left = left + barWidth + gap,
                    width = barWidth,
                    baseline = baseline,
                    fraction = trend.expenseMinor.toFloat() / ceiling.toFloat(),
                    colour = moneyColours.negative.copy(alpha = if (dim) 0.45f else 1f),
                    radius = radius,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            trends.forEach { trend ->
                Text(
                    text = trend.label.substringBefore(' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Spending day by day across a month — one series, so no legend is needed and
 * the heading names it. Thin bars with a flat baseline make the heavy days
 * obvious without labelling thirty of them.
 */
@Composable
fun DailySpendChart(
    days: List<DailyFlow>,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val moneyColours = MaterialTheme.money
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant

    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val focus = selected?.let(days::getOrNull)
    val ceiling = remember(days) { max(days.maxOfOrNull { it.expenseMinor } ?: 0L, 1L) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (focus) {
                null -> "Spending by day"
                else -> "${Dates.relativeOrRaw(focus.date)} · ${Money.format(focus.expenseMinor, currency)}"
            },
            style = MaterialTheme.typography.titleSmall,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        if (days.isNotEmpty()) {
                            val slot = size.width.toFloat() / days.size
                            selected = (offset.x / slot).toInt().coerceIn(0, days.lastIndex)
                        }
                    }
                },
        ) {
            if (days.isEmpty()) return@Canvas

            val baseline = size.height - 1f
            val slot = size.width / days.size
            val barWidth = (slot - 2.dp.toPx()).coerceAtLeast(2f)

            drawLine(
                color = grid,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1f,
            )

            days.forEachIndexed { index, day ->
                val dim = selected != null && selected != index

                drawBar(
                    left = slot * index + 1.dp.toPx(),
                    width = barWidth,
                    baseline = baseline,
                    fraction = day.expenseMinor.toFloat() / ceiling.toFloat(),
                    colour = moneyColours.negative.copy(alpha = if (dim) 0.4f else 0.9f),
                    radius = 2.dp.toPx(),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(days.firstOrNull(), days.getOrNull(days.size / 2), days.lastOrNull())
                .forEachIndexed { index, day ->
                    Text(
                        text = Dates.parse(day?.date)?.let { Dates.short(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        modifier = Modifier.weight(1f),
                        textAlign = when (index) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            1 -> androidx.compose.ui.text.style.TextAlign.Center
                            else -> androidx.compose.ui.text.style.TextAlign.End
                        },
                    )
                }
        }
    }
}

@Composable
private fun LegendValue(swatch: Color, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = swatch, shape = RoundedCornerShape(2.dp)),
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(text = value, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A bar anchored to the baseline with only its top corners rounded — the end
 * that carries the value is rounded, the end that carries the zero is not.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    left: Float,
    width: Float,
    baseline: Float,
    fraction: Float,
    colour: Color,
    radius: Float,
) {
    val height = (fraction.coerceIn(0f, 1f) * (baseline - 4f)).coerceAtLeast(0f)

    if (height <= 0.5f) return

    val top = baseline - height
    val corner = radius.coerceAtMost(width / 2f).coerceAtMost(height)

    val path = Path().apply {
        moveTo(left, baseline)
        lineTo(left, top + corner)
        arcTo(
            rect = Rect(offset = Offset(left, top), size = Size(corner * 2f, corner * 2f)),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(left + width - corner, top)
        arcTo(
            rect = Rect(
                offset = Offset(left + width - corner * 2f, top),
                size = Size(corner * 2f, corner * 2f),
            ),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(left + width, baseline)
        close()
    }

    drawPath(path = path, color = colour)
}
