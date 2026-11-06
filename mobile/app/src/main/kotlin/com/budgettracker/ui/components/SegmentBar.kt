package com.budgettracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgettracker.ui.theme.accents

/** One category's share of the month, as a slice of the spend bar. */
data class Spend(val fraction: Float, val colour: Color)

/**
 * The month in one line: a coloured stub per category, left to right in the
 * order they are given, against the track that stands for the whole limit.
 *
 * Where the colours stop is where the money stopped — the gap at the right
 * is what is left, so the bar answers "how much of the budget is gone" and
 * "what is it going on" at the same time.
 *
 * Fractions are of the *whole* bar, not of each other. If they add up to more
 * than one — the month is overspent — they are scaled to fill it, because a
 * bar cannot be more than full and stretching it past the end would say
 * nothing the figures above it have not already said.
 */
@Composable
fun SegmentBar(
    segments: List<Spend>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    gap: Dp = 3.dp,
    track: Color = MaterialTheme.accents.track,
) {
    val total = segments.sumOf { it.fraction.toDouble() }.toFloat()
    val scale = if (total > 1f) 1f / total else 1f
    val animated by animateFloatAsState(targetValue = scale, label = "segments")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        val gapPx = gap.toPx()

        drawRoundRect(color = track, cornerRadius = radius)

        var x = 0f

        segments.forEach { segment ->
            // A slice too thin to round is still a slice: it is drawn at the
            // bar's height so a one-percent category does not vanish, which
            // is why the widths are clamped rather than skipped.
            val width = (segment.fraction * animated * size.width)
                .coerceAtLeast(size.height)
                .coerceAtMost((size.width - x).coerceAtLeast(0f))

            if (width <= 0f) return@forEach

            drawRoundRect(
                color = segment.colour,
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )

            x += width + gapPx
        }
    }
}
