package com.budgettracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgettracker.ui.theme.MoneyLarge

/**
 * A category as a filled card: its own colour behind white text, what it has
 * taken of the month on the right, and the limit it is taking it from
 * underneath.
 *
 * The card is painted rather than outlined because the colour is the label —
 * the same hue marks this category in the spend bar above, and a row of these
 * is read by colour long before any of the words are.
 */
@Composable
fun CategoryTile(
    name: String,
    icon: ImageVector,
    colour: Color,
    amount: String,
    caption: String,
    modifier: Modifier = Modifier,
    limitLabel: String? = null,
    flag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color = colour, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        TileCrest(modifier = Modifier.matchParentSize())

        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (flag != null) {
                        StatusChip(
                            text = flag,
                            container = Color.White.copy(alpha = 0.25f),
                            content = Color.White,
                        )
                    }
                }

                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                AmountDisplay(
                    formatted = amount,
                    style = MoneyLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    color = Color.White,
                    centsScale = 0.62f,
                )

                if (limitLabel != null) {
                    Text(
                        text = limitLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

/**
 * The pair of hills across the foot of a painted card.
 *
 * They are the same white the text is, laid on at a tenth of its weight, so
 * every category gets the light on its own hue rather than on a tint mixed
 * for one of them. Where the two overlap is the brighter ridge — the shape
 * comes out of the overlap rather than out of a third colour.
 *
 * It sits behind the content and stops at the right half, which is where the
 * figures have the most room around them and the least to lose from it.
 */
@Composable
private fun TileCrest(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val wash = Color.White.copy(alpha = 0.09f)

        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.34f, size.height)

                cubicTo(
                    size.width * 0.55f, size.height * 0.92f,
                    size.width * 0.66f, size.height * 0.28f,
                    size.width, size.height * 0.16f,
                )

                lineTo(size.width, size.height)
                close()
            },
            color = wash,
        )

        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.52f, size.height)

                cubicTo(
                    size.width * 0.70f, size.height * 0.96f,
                    size.width * 0.78f, size.height * 0.58f,
                    size.width, size.height * 0.48f,
                )

                lineTo(size.width, size.height)
                close()
            },
            color = wash,
        )
    }
}
