package com.budgettracker.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.ui.theme.MoneyDisplay

/**
 * The hero figure, with its cents raised and set small.
 *
 * At display size the two decimal places are the least important thing on the
 * screen and among the widest-running digits on it. Lifting them to a
 * superscript keeps the amount honest — nothing is rounded away — while
 * letting the whole units carry the line.
 *
 * The split is on a trailing `.` and one or two digits, which is what every
 * figure the API formats looks like; anything else is drawn whole rather than
 * guessed at.
 */
@Composable
fun AmountDisplay(
    formatted: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyDisplay,
    color: Color = LocalContentColor.current,
    centsScale: Float = 0.52f,
) {
    val (whole, cents) = splitCents(formatted)

    Text(
        text = buildAnnotatedString {
            append(whole)

            if (cents != null) {
                withStyle(
                    SpanStyle(
                        fontSize = style.fontSize * centsScale,
                        baselineShift = BaselineShift(0.42f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    ),
                ) {
                    append(cents)
                }
            }
        },
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The same figure, from minor units, for the places the API sends no string. */
@Composable
fun AmountDisplay(
    minor: Long,
    currency: Currency,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyDisplay,
    color: Color = LocalContentColor.current,
) {
    AmountDisplay(
        formatted = Money.format(minor, currency),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/** `₱4,820.50` -> `₱4,820` and `.50`; `₱4,820` -> itself and nothing. */
internal fun splitCents(formatted: String): Pair<String, String?> {
    val dot = formatted.lastIndexOf('.')

    if (dot <= 0 || dot == formatted.lastIndex) return formatted to null

    val tail = formatted.substring(dot + 1)

    return if (tail.length <= 2 && tail.all { it.isDigit() }) {
        formatted.substring(0, dot) to formatted.substring(dot)
    } else {
        formatted to null
    }
}
