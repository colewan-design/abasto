package com.budgettracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgettracker.ui.theme.accents

/**
 * The shape of a run of figures, with no axis and no labels.
 *
 * A sparkline is a sentence about direction, not a chart to read values off;
 * the number it belongs to is always printed next to it, so the line is free
 * to show only the shape.
 *
 * [markerFill] puts a dot on the two ends and the high point — the three
 * readings an eye looks for anyway — filled in the card's own colour so the
 * line reads through them rather than behind them. [guide] draws the faint
 * dashed verticals the dots sit on, which give the curve something to be
 * measured against without adding an axis anyone has to read.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    colour: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.5.dp,
    fill: Boolean = false,
    markerFill: Color? = null,
    guide: Color? = null,
    guides: Int = 5,
) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        if (values.size < 2) return@Canvas

        val stroke = strokeWidth.toPx()
        val radius = stroke * 2.2f

        // A dot hangs off the end of the line it marks, so the line stops
        // short of the edges by its radius — otherwise the first and last
        // ones are drawn half outside the card.
        val edge = if (markerFill != null) radius else 0f
        val inset = maxOf(stroke, edge)
        val top = inset
        val bottom = size.height - inset
        val left = edge
        val right = size.width - edge
        val low = values.min()
        val high = values.max()
        val span = (high - low).takeIf { it > 0.0001f }

        val points = values.mapIndexed { index, value ->
            val x = left + (right - left) * index / (values.size - 1).toFloat()
            // A flat run sits on the middle of the box rather than on the
            // floor, which would read as "nothing happened" instead of
            // "nothing changed".
            val t = span?.let { (value - low) / it } ?: 0.5f

            Offset(x, bottom - t * (bottom - top))
        }

        if (guide != null && guides > 1) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(stroke * 1.6f, stroke * 2.4f))

            repeat(guides) { index ->
                val x = left + (right - left) * index / (guides - 1).toFloat()

                drawLine(
                    color = guide,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke * 0.42f,
                    pathEffect = dash,
                )
            }
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)

            // A Catmull-Rom style smoothing: each segment's control points
            // lean towards its neighbours, which keeps the curve passing
            // through every reading instead of rounding the peaks off.
            points.zipWithNext().forEachIndexed { index, (from, to) ->
                val previous = points.getOrElse(index - 1) { from }
                val next = points.getOrElse(index + 2) { to }

                cubicTo(
                    from.x + (to.x - previous.x) / 6f,
                    from.y + (to.y - previous.y) / 6f,
                    to.x - (next.x - from.x) / 6f,
                    to.y - (next.y - from.y) / 6f,
                    to.x,
                    to.y,
                )
            }
        }

        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }

            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(colour.copy(alpha = 0.26f), colour.copy(alpha = 0f)),
                ),
            )
        }

        drawPath(path = line, color = colour, style = Stroke(width = stroke))

        if (markerFill != null) {
            val peak = values.indices.maxByOrNull { values[it] } ?: 0

            listOf(0, peak, points.lastIndex).distinct().forEach { index ->
                drawCircle(color = markerFill, radius = radius, center = points[index])

                drawCircle(
                    color = colour,
                    radius = radius,
                    center = points[index],
                    style = Stroke(width = stroke * 0.9f),
                )
            }
        }
    }
}

/** One bar: what it is called, how big it is, and the figure it stands for. */
data class BarValue(val label: String, val value: Float, val caption: String)

/**
 * A short run of bars with every figure printed above its own bar.
 *
 * Seven days or six months is few enough to label them all, which is worth
 * more than an axis: there is nothing left to estimate, and the one bar that
 * matters can be picked out in the accent without the others becoming
 * unreadable.
 */
@Composable
fun ValueBarChart(
    bars: List<BarValue>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    barMaxHeight: Dp = 104.dp,
    onSelect: ((Int) -> Unit)? = null,
) {
    val accents = MaterialTheme.accents
    val ceiling = bars.maxOfOrNull { it.value }?.takeIf { it > 0f }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            val selected = index == highlight
            val fraction = ceiling?.let { bar.value / it } ?: 0f

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (onSelect != null) {
                            Modifier.clickable { onSelect(index) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = bar.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) accents.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // The lit bar is a gradient rather than a flat fill: against
                // a hundred points of plain track, a face that catches the
                // light is what makes the heaviest bar the first thing seen.
                //
                // A quiet day still gets a bar deep enough to read as one —
                // below that the proportion is honest but the shape is a
                // hairline nobody can see, let alone compare.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barMaxHeight * fraction.coerceIn(0.08f, 1f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                Brush.verticalGradient(
                                    listOf(lerp(accents.accent, Color.White, 0.24f), accents.accent),
                                )
                            } else {
                                SolidColor(accents.track)
                            },
                        ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A sentence the figures earned: something true about this month that the
 * charts show but do not say.
 */
@Composable
fun InsightBanner(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val accents = MaterialTheme.accents

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(accents.accentSoft)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color = accents.accent, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accents.onAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = accents.onAccentSoft,
        )
    }
}

/** A small labelled fact, two to a row under the charts. */
@Composable
fun InsightTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    SoftCard(modifier = modifier, padding = 14.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(color = tint.copy(alpha = 0.16f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
    }
}
