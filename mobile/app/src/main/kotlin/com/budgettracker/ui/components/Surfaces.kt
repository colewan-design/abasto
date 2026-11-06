package com.budgettracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category
import com.budgettracker.ui.icons.AppIcons
import com.budgettracker.ui.theme.MoneyMedium
import com.budgettracker.ui.theme.hexColor

/**
 * The card the secondary screens are built from.
 *
 * It is [SoftCard] under a name the rest of the app already calls it by, so
 * every screen picked up the design's card — deep corners and a soft shadow
 * instead of a hairline border — without each one being rewritten.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    SoftCard(modifier = modifier, padding = padding, content = content)
}

/** A card heading with an optional action on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The icon that stands in for a category or an account everywhere.
 *
 * It takes the colour the account holder chose, at low opacity behind a solid
 * icon, so a long list stays readable rather than turning into confetti.
 *
 * Round by default. The rounded square is for the places a badge carries a
 * thing rather than a person — a bill, a service — where the squarer mark
 * reads closer to the app tile the bill is paid in.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = tint.copy(alpha = 0.14f), shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
fun CategoryBadge(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    IconBadge(
        icon = AppIcons.named(category?.icon ?: category?.group?.icon),
        tint = hexColor(category?.color ?: category?.group?.color, MaterialTheme.colorScheme.primary),
        modifier = modifier,
        size = size,
    )
}

@Composable
fun AccountBadge(
    account: Account,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    IconBadge(
        icon = account.icon?.let(AppIcons::named) ?: AppIcons.forAccount(account.type),
        tint = hexColor(account.color, MaterialTheme.colorScheme.primary),
        modifier = modifier,
        size = size,
    )
}

/** A compact label: a count, a status, a recurrence. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = CircleShape,
        color = container,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * A total with a direction: money in, money out, what is left of the two.
 *
 * The figure itself stays in the plain text colour — a column of coloured
 * numerals is hard to read and says nothing the labels do not — and the
 * direction is carried by the icon and by the wash of colour rising across
 * the foot of the card. Three of these across the top of the ledger say
 * which way the month went before a single figure is read.
 */
@Composable
fun FlowTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            // The card is taller than its three lines need, because the wash
            // wants a band of its own at the foot of it. Drawn against a
            // card sized to the text, the curve runs through the figure.
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(color = MaterialTheme.colorScheme.surface, shape = shape),
    ) {
        FlowWash(tint = tint, modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(17.dp),
                )

                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }

            // Three totals have a third of a phone's width each, and the
            // widest of them decides whether any of them fit. A figure cut
            // mid-digit is worse than one that says it was cut.
            Text(
                text = value,
                style = MoneyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The curve of colour across the foot of a [FlowTile].
 *
 * It is a wave rather than a bar because a flat band reads as a progress
 * meter — something measured — and this is only the card saying which
 * direction it is about. It rises to the right, where the eye leaves the
 * figure, and is brightest where it is deepest.
 */
@Composable
private fun FlowWash(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val crest = Path().apply {
            moveTo(0f, size.height * 0.90f)

            cubicTo(
                size.width * 0.30f, size.height * 0.99f,
                size.width * 0.58f, size.height * 0.86f,
                size.width, size.height * 0.70f,
            )
        }

        val area = Path().apply {
            addPath(crest)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = area,
            brush = Brush.linearGradient(
                colors = listOf(tint.copy(alpha = 0.10f), tint.copy(alpha = 0.44f)),
                start = Offset(0f, size.height * 0.70f),
                end = Offset(size.width, size.height),
            ),
        )

        drawPath(
            path = crest,
            color = tint.copy(alpha = 0.45f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }
}

/** A labelled figure. Three of these across the top is the dashboard header. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: MoneyTone = MoneyTone.Neutral,
    caption: String? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MoneyText(formatted = value, tone = tone)

            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
