package com.budgettracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgettracker.R
import com.budgettracker.core.time.Dates
import com.budgettracker.ui.theme.Overline
import com.budgettracker.ui.theme.accents
import java.time.YearMonth

/**
 * The design's building blocks.
 *
 * Every screen is a stack of [SoftCard]s with at most one [HeroCard] at the
 * top, so a screen never decides what a card looks like — it decides what
 * goes in one.
 */

/**
 * The card every screen is built from: white, deeply rounded, and lifted off
 * the background by a shadow rather than by a border.
 *
 * The shadow is skipped in the dark theme, where a drop shadow under a dark
 * card reads as dirt; there the card separates by being lighter than the
 * background instead.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(color = color, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The one dark card on a screen, carrying the figure the screen is about.
 *
 * Its colours do not come from the Material scheme: it is the same near-black
 * in both themes, so everything inside it reads against a fixed background
 * and `LocalContentColor` is set once here rather than at every `Text`.
 *
 * A soft wash of the accent falls across the top right corner. It is the one
 * place in the app where the accent is decoration rather than a control, and
 * it is what keeps a card this dark from reading as a hole in the page.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    glow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accents = MaterialTheme.accents

    Box(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape, elevation = 14.dp)
            .clip(shape)
            .background(color = accents.ink, shape = shape),
    ) {
        if (glow) {
            // The wash is mixed for the near-black card. At that strength on
            // the light one's off-white it stops being light falling on the
            // card and becomes a second colour painted into the corner, with
            // whatever sits there — the trend chip — stranded on top of it.
            AccentGlow(
                accent = accents.accent,
                strength = if (accents.dark) 1f else 0.5f,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(modifier = Modifier.padding(padding)) {
            CompositionLocalProvider(LocalContentColor provides accents.onInk) {
                content()
            }
        }
    }
}

/**
 * Two overlapping radial washes rather than one: a single circle reads as a
 * spotlight, while the second, dimmer one trailing below and left of it bends
 * the light into the band the design asks for.
 *
 * Every offset is measured in the card's *width*, including the vertical ones,
 * so the wash keeps the same shape whether it is drawn on the short card the
 * reports screen has or the tall one the home screen has. Scaling it by height
 * would stretch it down the tall card into a haze over the content.
 *
 * The first circle is centred just above the top edge, so what lands on the
 * card is the bright outer corner of it rather than its centre — a hot spot
 * sitting fully inside the card reads as a flaw in the panel.
 *
 * It is drawn at the width it is given, which is what lets the same light
 * fall across a whole page — the more screen hangs it off the top right of
 * the window rather than off a card — and [strength] is what keeps that from
 * burning a hole in the light theme, where there is no near-black under it.
 */
@Composable
fun AccentGlow(accent: Color, modifier: Modifier = Modifier, strength: Float = 1f) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to accent.copy(alpha = 0.95f * strength),
                    0.28f to accent.copy(alpha = 0.52f * strength),
                    0.62f to accent.copy(alpha = 0.15f * strength),
                    1.00f to Color.Transparent,
                ),
                center = Offset(x = size.width * 1.02f, y = -size.width * 0.02f),
                radius = size.width * 0.80f,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to accent.copy(alpha = 0.22f * strength),
                    0.50f to accent.copy(alpha = 0.06f * strength),
                    1.00f to Color.Transparent,
                ),
                center = Offset(x = size.width * 0.74f, y = size.width * 0.17f),
                radius = size.width * 0.40f,
            ),
        )

        // Two folds inside the wash, drawn as soft strokes rather than as
        // lines: they give the light an edge to break on, which is what
        // stops a radial gradient reading as a smudge on the panel. They
        // stay faint — a fold the eye can name is a scratch on the card.
        //
        // Both are measured against a band off the top rather than against
        // the card's height, so the motif is the same corner piece on the
        // short card the budget screen has and the tall one the home screen
        // has, instead of being stretched down the long one.
        val band = minOf(size.height, size.width * 0.45f)
        val fold = Stroke(width = size.width * 0.045f, cap = StrokeCap.Round)

        // Each fold is painted with a brush that starts at nothing, so it
        // comes up out of the dark the way light on a fold does. Drawn in a
        // flat colour it ends in a rounded cap with nothing behind it, and a
        // stroke that simply stops reads as a scratch on the card.
        fun fade(from: Float, to: Float, alpha: Float) = Brush.linearGradient(
            colors = listOf(Color.Transparent, accent.copy(alpha = alpha * strength)),
            start = Offset(size.width * from, 0f),
            end = Offset(size.width * to, 0f),
        )

        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.58f, band * 0.62f)

                cubicTo(
                    size.width * 0.76f, band * 0.52f,
                    size.width * 0.84f, band * 0.20f,
                    size.width * 1.03f, band * 0.13f,
                )
            },
            brush = fade(from = 0.56f, to = 0.96f, alpha = 0.16f),
            style = fold,
        )

        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.66f, band * 0.06f)

                cubicTo(
                    size.width * 0.84f, band * 0.24f,
                    size.width * 0.90f, band * 0.48f,
                    size.width * 1.03f, band * 0.52f,
                )
            },
            brush = fade(from = 0.64f, to = 1.00f, alpha = 0.11f),
            style = fold,
        )
    }
}

/**
 * A drop shadow soft enough to read as depth rather than as an outline, and
 * absent in the dark theme where it would only muddy the background.
 */
@Composable
private fun Modifier.softShadow(shape: Shape, elevation: Dp = 8.dp): Modifier {
    return if (MaterialTheme.accents.dark) {
        this
    } else {
        shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color(0x14000C2E),
            spotColor = Color(0x1A000C2E),
        )
    }
}

/**
 * The top of a tab: the screen's name at display size, with whatever controls
 * belong to the screen as a whole on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        trailing()
    }
}

/** The small tracked-out caption that names the figure under it. */
@Composable
fun OverlineLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = Overline,
        color = color,
        modifier = modifier,
    )
}

/** A heading inside the page flow, with an optional count or action beside it. */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )

        if (trailing != null) {
            val colour = if (onTrailingClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = onTrailingClick)
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colour,
                )

                // The chevron only appears when the label leads somewhere, so
                // a plain count beside a heading is not dressed up as a link.
                if (onTrailingClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colour,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * A status in a word: "On track", "MAXED", "↓ 8% vs last".
 *
 * It takes its colours from the caller because the same shape says different
 * things on a white card, on the dark card, and on a filled category tile.
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color = container, shape = CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(15.dp),
            )
        }

        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

/**
 * A round icon button: the bell, the theme toggle, the sparkle on the hero.
 *
 * [shape] is here for the one button that is not round — the filter key on
 * the activity screen, which is a rounded square so it reads as the twin of
 * the bottom bar's keys rather than as another bubble in the header.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = CircleShape,
    badge: String? = null,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(color = container, shape = shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = content,
                modifier = Modifier.size(size * 0.45f),
            )
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * One of the four actions under the balance: a raised key, and the word for
 * what it does underneath. The label is part of the control, not a caption —
 * a row of unlabelled glyphs is a guessing game.
 *
 * The key is a wide rounded rectangle rather than a circle. Four circles on a
 * phone's width have to shrink to fit the gaps between them; the rectangle
 * keeps the same gaps and gives the icon a target the thumb can land on.
 */
@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.accents.inkRaised,
    content: Color = MaterialTheme.accents.onInk,
    labelColor: Color = MaterialTheme.accents.inkMuted,
) {
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 52.dp)
                .clip(shape)
                .background(color = container, shape = shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(23.dp),
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The month the screen is showing, and a menu to change it.
 *
 * A chip with a menu rather than a pair of arrows: the arrows cost one tap
 * per month, which is a long way back to March, and they take up the width
 * the screen's title wants.
 */
@Composable
fun MonthChip(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    allowFuture: Boolean = false,
    months: Int = 12,
) {
    var open by remember { mutableStateOf(false) }

    val options = remember(month, allowFuture, months) {
        val newest = if (allowFuture) YearMonth.now().plusMonths(1) else YearMonth.now()

        (0 until months)
            .map { newest.minusMonths(it.toLong()) }
            .let { if (month in it) it else (it + month).sortedDescending() }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                )
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = Dates.monthLabel(month).substringBefore(' '),
                style = MaterialTheme.typography.titleSmall,
            )

            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "Choose a month",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(Dates.monthLabel(option)) },
                    onClick = {
                        open = false
                        onMonthChange(option)
                    },
                    trailingIcon = {
                        if (option == month) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * A short range switch — W / M / Y — small enough to sit on the same line as
 * the heading of the chart it controls.
 *
 * Material's filter chips are the right control for a form, where each
 * choice is a word; here the choices are single letters beside a title, and
 * three chips would take the heading's width to say three letters.
 */
@Composable
fun <T> CompactSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The chosen letter is the one lifted out of the track, which means
    // lighter than it in both themes — in the dark one that is a step up the
    // tonal scale, not the white `surface` the light theme lifts to.
    val lifted = if (MaterialTheme.accents.dark) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) lifted else Color.Transparent)
                    .selectable(
                        selected = active,
                        role = Role.RadioButton,
                        onClick = { onSelect(value) },
                    )
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * The app's mark.
 *
 * It is the artwork itself rather than a drawn approximation of it: the mark
 * is lit on its own dark plate, and the plate is as much of the logo as the
 * shape on it. Clipped to the same corner the launcher gives it, so the mark
 * in the app and the mark on the home screen are the one thing.
 */
@Composable
fun Brandmark(modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Image(
        painter = painterResource(id = R.drawable.ic_brandmark),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f)),
    )
}

/** Whoever is signed in, as a circle with their initials in it. */
@Composable
fun AvatarBubble(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
