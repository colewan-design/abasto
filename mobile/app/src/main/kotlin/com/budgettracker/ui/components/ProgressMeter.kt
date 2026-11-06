package com.budgettracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgettracker.data.model.BudgetStatus
import com.budgettracker.ui.theme.accents
import com.budgettracker.ui.theme.money

/**
 * How much of a limit is used, or how far a goal has come.
 *
 * Past 100% the bar stays full and turns the overspend colour — stretching it
 * off the end would say nothing, and shrinking the scale would hide that the
 * limit was passed at all. A faint marker shows where the alert threshold sits
 * so "approaching" is visible before it becomes "exceeded".
 */
@Composable
fun ProgressMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    colour: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.accents.track,
    threshold: Float? = null,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = clamped, label = "progress")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        drawRoundRect(color = track, cornerRadius = radius)

        if (animated > 0f) {
            drawRoundRect(
                color = colour,
                size = Size(width = size.width * animated, height = size.height),
                cornerRadius = radius,
            )
        }

        if (threshold != null && threshold in 0.01f..0.99f) {
            val x = size.width * threshold

            drawLine(
                color = track.copy(alpha = 0.9f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = size.height * 0.22f,
            )
        }
    }
}

/** The meter with its own label and figures above it, as a budget row shows. */
@Composable
fun LabelledMeter(
    label: String,
    fraction: Float,
    primaryValue: String,
    secondaryValue: String,
    modifier: Modifier = Modifier,
    status: BudgetStatus = BudgetStatus.Ok,
    threshold: Float? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val money = MaterialTheme.money
    val colour = when (status) {
        BudgetStatus.Exceeded -> money.negative
        BudgetStatus.Approaching -> money.warning
        BudgetStatus.Ok -> MaterialTheme.colorScheme.primary
        BudgetStatus.Unset -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.titleSmall,
                    color = colour,
                )
            }

            ProgressMeter(
                fraction = fraction,
                colour = colour,
                threshold = threshold,
            )

            Text(
                text = secondaryValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
