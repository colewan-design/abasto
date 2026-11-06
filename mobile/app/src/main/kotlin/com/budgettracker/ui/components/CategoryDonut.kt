package com.budgettracker.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.data.model.CategoryBreakdown
import com.budgettracker.ui.theme.MoneyLarge
import com.budgettracker.ui.theme.Overline
import com.budgettracker.ui.theme.accents
import com.budgettracker.ui.theme.hexColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How much of the ring's width is ring, and how much is the hole in it. */
private const val RingThickness = 0.12f

/** The alpha a slice falls to while another one is the one being read. */
private const val RingDim = 0.38f

/** The corner a slice is rounded by, clamped down when a slice is small. */
private val RingCorner = 9.dp

/** The surface-wide gap between neighbouring slices. */
private val SliceGap = 3.dp

/** How far the slice being read lifts out of the ring. */
private val SliceLift = 3.dp

/**
 * Share of spending by category.
 *
 * Each arc wears the category's own colour, so a category reads the same here
 * as it does in every list — colour follows the category, never its rank in
 * this particular month. Those colours are chosen per category, which means
 * two neighbouring arcs can land on close hues; the ring is therefore never
 * the only encoding. Every slice is named in the legend beside its amount and
 * share, and a gap cut down to the track separates adjacent arcs so the
 * boundary is visible even when the two colours are not easy to tell apart.
 *
 * The ring is drawn in the same grammar as the bar and line charts. Slices are
 * rounded sectors rather than flat-ended strokes, the way a bar is rounded at
 * the end that carries its value, with the corner clamped down on a slice too
 * small to take it. They sit on the same quiet track a meter sits on, so the
 * gaps show track rather than punching holes in the card. One slice is lit at
 * a time — a gradient face that catches the light, exactly what marks the
 * heaviest bar in [ValueBarChart] — and until something is tapped that is the
 * largest slice, which is the one worth finding.
 *
 * Tapping an arc or a legend row is the mobile stand-in for hovering, as it is
 * on the trend chart: the slice lifts out of the ring, the rest fall back, and
 * the figure in the hole switches to that category instead of labelling nine
 * arcs at once. Tapping it again, or tapping the hole, puts the total back.
 *
 * Past [limit] slices the tail is pooled into one grey "Other" rather than
 * drawing a ring of slivers nobody can read or tap.
 */
@Composable
fun CategoryDonut(
    breakdown: List<CategoryBreakdown>,
    centreLabel: String,
    centreValue: String,
    currency: Currency,
    modifier: Modifier = Modifier,
    limit: Int = 8,
    markSize: Dp = 204.dp,
    onSliceClick: ((CategoryBreakdown) -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.accents.track
    val fallback = remember(muted) { fallbackSliceColours(muted) }

    val slices = remember(breakdown, limit, currency) { pooled(breakdown, limit, currency) }
    val total = remember(slices) { slices.sumOf { it.amountMinor } }
    val colours = remember(slices, fallback) {
        slices.mapIndexed { index, slice -> sliceColour(slice, index, fallback) }
    }

    var focused by remember(slices) { mutableStateOf<Int?>(null) }

    // The ring sweeps in from twelve o'clock on arrival, the way a meter fills
    // rather than appearing already full.
    var revealed by remember(slices) { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "donut",
    )

    LaunchedEffect(slices) { revealed = true }

    val focus = focused?.let(slices::getOrNull)
    val hole = markSize * (1f - RingThickness * 2f) - SliceLift * 2

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(markSize)
                    .pointerInput(slices, total) {
                        detectTapGestures { offset ->
                            focused = sliceAt(offset, size.width, size.height, slices, total, focused)
                        }
                    },
            ) {
                val metrics = ringMetrics(size.minDimension)
                val centre = Offset(size.width / 2f, size.height / 2f)

                // The track the slices sit on. It shows through the gaps, so a
                // boundary between two arcs is a groove rather than a hole,
                // and an empty month still reads as a ring.
                drawCircle(
                    color = track,
                    radius = metrics.mid,
                    center = centre,
                    style = Stroke(width = metrics.thickness),
                )

                if (total <= 0L) return@Canvas

                val sweeps = sweepsFor(slices, total, metrics.minSweep)
                val gap = if (sweeps.count { it > 0f } > 1) metrics.gap else 0f
                val corner = RingCorner.toPx()
                val lift = SliceLift.toPx()

                var start = -90f

                sweeps.forEachIndexed { index, sweep ->
                    val span = sweep * reveal

                    if (span <= 0.05f) {
                        start += span

                        return@forEachIndexed
                    }

                    // Nothing tapped yet: the largest slice is the lit one,
                    // the same way the heaviest bar is the only one the
                    // accent is spent on.
                    val lit = focused?.let { it == index } ?: (index == 0)

                    drawDonutSlice(
                        centre = centre,
                        outer = if (focused == index) metrics.outer + lift else metrics.outer,
                        inner = metrics.inner,
                        start = start + gap / 2f,
                        sweep = (span - gap).coerceAtLeast(span * 0.5f),
                        corner = corner,
                        colour = colours[index],
                        alpha = if (focused == null || focused == index) 1f else RingDim,
                        lit = lit,
                    )

                    start += span
                }
            }

            DonutCentre(
                label = focus?.name ?: centreLabel,
                value = focus?.formatted ?: centreValue,
                caption = focus?.let { slice ->
                    "${(slice.share * 100).roundToInt()}% · " +
                        "${slice.transactions} ${if (slice.transactions == 1) "entry" else "entries"}"
                },
                tint = focused?.let { colours.getOrNull(it) } ?: muted,
                maxWidth = hole * 0.86f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            slices.forEachIndexed { index, slice ->
                LegendRow(
                    swatch = colours[index],
                    name = slice.name,
                    value = slice.formatted,
                    share = slice.share,
                    focused = focused == index,
                    dimmed = focused != null && focused != index,
                    onClick = {
                        focused = if (focused == index) null else index
                        onSliceClick?.invoke(slice)
                    },
                )
            }
        }
    }
}

/**
 * What sits in the hole: the total, or the slice being read.
 *
 * The figure is drawn the way every other hero figure in the app is, cents
 * raised and set small, and steps down a size when the amount is long enough
 * to run into the ring.
 */
@Composable
private fun DonutCentre(
    label: String,
    value: String,
    caption: String?,
    tint: Color,
    maxWidth: Dp,
) {
    val style: TextStyle = when {
        value.length <= 8 -> MoneyLarge
        value.length <= 11 -> MoneyLarge.copy(
            fontSize = 27.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.6).sp,
        )
        else -> MoneyLarge.copy(
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.4).sp,
        )
    }

    Column(
        modifier = Modifier.widthIn(max = maxWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = Overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        AmountDisplay(formatted = value, style = style)

        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LegendRow(
    swatch: Color,
    name: String,
    value: String,
    share: Double,
    focused: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // The row follows the ring: the one being read keeps its contrast, the
    // rest step back to the same degree their arcs do. Nothing moves, so
    // tapping down the list never shifts the list under the finger.
    val content = if (dimmed) muted else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = if (focused) 14.dp else 10.dp, height = 10.dp)
                .background(
                    color = if (dimmed) swatch.copy(alpha = RingDim) else swatch,
                    shape = RoundedCornerShape(3.dp),
                ),
        )

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "${(share * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) swatch else muted,
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = content,
        )
    }
}

/** The ring's dimensions, shared by the drawing and the tapping. */
private data class RingMetrics(
    val thickness: Float,
    val outer: Float,
    val inner: Float,
    val mid: Float,
    val gap: Float,
    val minSweep: Float,
)

private fun Density.ringMetrics(side: Float): RingMetrics {
    val thickness = side * RingThickness
    // The lifted slice has to have somewhere to lift to, so the ring is drawn
    // inside the canvas by that much rather than against its edge.
    val outer = (side / 2f - SliceLift.toPx()).coerceAtLeast(2f)
    val inner = (outer - thickness).coerceAtLeast(1f)
    val mid = (outer + inner) / 2f

    val gap = degrees(SliceGap.toPx() / mid)

    return RingMetrics(
        thickness = thickness,
        outer = outer,
        inner = inner,
        mid = mid,
        gap = gap,
        // A slice is never thinner than a stub: its own gap, plus enough arc
        // left over to see. Below that a one-percent category is a hairline
        // that cannot be seen, let alone tapped.
        minSweep = gap + degrees(thickness * 0.3f / mid),
    )
}

/**
 * Each slice's sweep in degrees, summing to the whole circle.
 *
 * Slices too small to see are lifted to [minSweep] and the degrees that costs
 * are taken back off the slices that have room to spare, in proportion. The
 * alternative — letting the minimums push the total past 360 — would have the
 * last slice drawn over the first, and a ring that does not close is worse
 * than one whose smallest slice is a shade generous.
 */
private fun sweepsFor(slices: List<CategoryBreakdown>, total: Long, minSweep: Float): FloatArray {
    val sweeps = FloatArray(slices.size)

    if (total <= 0L) return sweeps

    slices.forEachIndexed { index, slice ->
        if (slice.amountMinor > 0L) {
            val share = slice.amountMinor.toFloat() / total.toFloat()

            sweeps[index] = (share * 360f).coerceAtLeast(minSweep)
        }
    }

    val excess = sweeps.sum() - 360f

    if (excess <= 0.01f) return sweeps

    val spare = sweeps.filter { it > minSweep }.map { it - minSweep }.sum()

    if (spare <= 0f) return sweeps

    val taken = (excess / spare).coerceAtMost(1f)

    for (index in sweeps.indices) {
        if (sweeps[index] > minSweep) {
            sweeps[index] -= (sweeps[index] - minSweep) * taken
        }
    }

    return sweeps
}

/**
 * The slice under a tap, or null for a tap on the hole or outside the ring —
 * and null again for a second tap on the slice already being read, so the
 * total comes back without having to find somewhere empty to tap.
 */
private fun Density.sliceAt(
    offset: Offset,
    width: Int,
    height: Int,
    slices: List<CategoryBreakdown>,
    total: Long,
    focused: Int?,
): Int? {
    if (total <= 0L) return null

    val metrics = ringMetrics(minOf(width, height).toFloat())
    val centre = Offset(width / 2f, height / 2f)
    val distance = (offset - centre).getDistance()

    // The band is read generously: a finger aimed at a 24dp ring lands a few
    // points either side of it more often than not.
    val slack = 8.dp.toPx()

    if (distance < metrics.inner - slack || distance > metrics.outer + slack) return null

    val radians = atan2(offset.y - centre.y, offset.x - centre.x)
    val angle = (degrees(radians) + 90f).mod(360f)

    val sweeps = sweepsFor(slices, total, metrics.minSweep)
    var cursor = 0f

    sweeps.forEachIndexed { index, sweep ->
        // Measured without the gaps, so a tap that lands in one still picks
        // the slice it is nearest rather than nothing at all.
        if (angle >= cursor && angle < cursor + sweep) {
            return if (focused == index) null else index
        }

        cursor += sweep
    }

    return null
}

/**
 * One slice: an arc with its corners rounded, the way a bar is rounded at the
 * end that carries its value.
 *
 * The corner is clamped to what the slice can take — half the ring's thickness
 * and half its own arc — so a thin slice is rounded as far as it can be and
 * stays exactly as wide as its share, which a round stroke cap could not
 * promise: a cap adds half the ring's thickness to both ends of every arc,
 * which on a small slice is most of its width again.
 */
private fun DrawScope.drawDonutSlice(
    centre: Offset,
    outer: Float,
    inner: Float,
    start: Float,
    sweep: Float,
    corner: Float,
    colour: Color,
    alpha: Float,
    lit: Boolean,
) {
    if (sweep <= 0f || outer <= inner) return

    // A single slice is the whole ring: drawn as a sector it would carry a
    // seam at twelve o'clock, so it is stroked instead.
    if (sweep >= 359.5f) {
        drawCircle(
            color = colour,
            radius = (outer + inner) / 2f,
            center = centre,
            alpha = alpha,
            style = Stroke(width = outer - inner),
        )

        return
    }

    val end = start + sweep
    val radians = Math.toRadians(sweep.toDouble()).toFloat()
    val k = corner
        .coerceAtMost((outer - inner) / 2f)
        .coerceAtMost(radians * inner / 2f)
        .coerceAtLeast(0f)

    val outerStep = degrees(k / outer).coerceAtMost(sweep / 2f)
    val innerStep = degrees(k / inner).coerceAtMost(sweep / 2f)

    fun polar(radius: Float, degrees: Float): Offset {
        val theta = Math.toRadians(degrees.toDouble())

        return Offset(
            x = centre.x + radius * cos(theta).toFloat(),
            y = centre.y + radius * sin(theta).toFloat(),
        )
    }

    val head = polar(outer, start + outerStep)

    val path = Path().apply {
        moveTo(head.x, head.y)

        arcTo(
            rect = Rect(center = centre, radius = outer),
            startAngleDegrees = start + outerStep,
            sweepAngleDegrees = sweep - outerStep * 2f,
            forceMoveTo = false,
        )

        corner(polar(outer, end), polar(outer - k, end))
        polar(inner + k, end).let { lineTo(it.x, it.y) }
        corner(polar(inner, end), polar(inner, end - innerStep))

        arcTo(
            rect = Rect(center = centre, radius = inner),
            startAngleDegrees = end - innerStep,
            sweepAngleDegrees = -(sweep - innerStep * 2f),
            forceMoveTo = false,
        )

        corner(polar(inner, start), polar(inner + k, start))
        polar(outer - k, start).let { lineTo(it.x, it.y) }
        corner(polar(outer, start), head)

        close()
    }

    val bounds = path.getBounds()

    // The lit slice is a gradient rather than a flat fill: against a ring of
    // plain colour, a face that catches the light is what makes the slice
    // being read the first thing seen.
    val brush = if (lit && bounds.height > 1f) {
        Brush.verticalGradient(
            colors = listOf(lerp(colour, Color.White, 0.24f), colour),
            startY = bounds.top,
            endY = bounds.bottom,
        )
    } else {
        SolidColor(colour)
    }

    drawPath(path = path, brush = brush, alpha = alpha)
}

/** A fillet across a corner, with the corner itself as the control point. */
private fun Path.corner(control: Offset, to: Offset) {
    cubicTo(
        control.x,
        control.y,
        control.x,
        control.y,
        to.x,
        to.y,
    )
}

private fun degrees(radians: Float): Float = Math.toDegrees(radians.toDouble()).toFloat()

/**
 * Greys for slices with no colour of their own, and for the pooled tail. Grey
 * is deliberate: those slices are not an identity, they are a remainder.
 */
private fun fallbackSliceColours(muted: Color): List<Color> = listOf(
    muted,
    muted.copy(alpha = 0.72f),
    muted.copy(alpha = 0.54f),
    muted.copy(alpha = 0.4f),
)

private fun sliceColour(
    slice: CategoryBreakdown,
    index: Int,
    fallback: List<Color>,
): Color = hexColor(slice.color, fallback[index % fallback.size])

private fun pooled(
    breakdown: List<CategoryBreakdown>,
    limit: Int,
    currency: Currency,
): List<CategoryBreakdown> {
    val sorted = breakdown.sortedByDescending { it.amountMinor }

    if (sorted.size <= limit) return sorted

    val head = sorted.take(limit)
    val tail = sorted.drop(limit)
    val pooledMinor = tail.sumOf { it.amountMinor }

    return head + CategoryBreakdown(
        categoryId = null,
        name = "Other (${tail.size})",
        amountMinor = pooledMinor,
        formatted = Money.format(pooledMinor, currency),
        transactions = tail.sumOf { it.transactions },
        share = tail.sumOf { it.share },
    )
}
