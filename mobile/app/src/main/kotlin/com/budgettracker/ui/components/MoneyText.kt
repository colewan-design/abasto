package com.budgettracker.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.data.model.Transaction
import com.budgettracker.ui.theme.MoneyMedium
import com.budgettracker.ui.theme.money

/**
 * An amount, coloured by direction.
 *
 * Colour here is never decoration: green means money in, red means money out,
 * and a figure that carries no direction (a balance, a limit) stays in the
 * normal text colour so the eye is not told something untrue.
 */
@Composable
fun MoneyText(
    minor: Long,
    currency: Currency,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyMedium,
    coloured: Boolean = false,
    signed: Boolean = false,
    decimals: Boolean = true,
) {
    val money = MaterialTheme.money

    Text(
        text = Money.format(minor, currency, signed = signed, decimals = decimals),
        style = style,
        color = when {
            !coloured -> LocalContentColor.current
            minor > 0L -> money.positive
            minor < 0L -> money.negative
            else -> LocalContentColor.current
        },
        modifier = modifier,
    )
}

/** The pre-formatted string the API sent, with the same colour rules. */
@Composable
fun MoneyText(
    formatted: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyMedium,
    tone: MoneyTone = MoneyTone.Neutral,
) {
    val money = MaterialTheme.money

    Text(
        text = formatted,
        style = style,
        color = when (tone) {
            MoneyTone.Neutral -> LocalContentColor.current
            MoneyTone.Positive -> money.positive
            MoneyTone.Negative -> money.negative
            MoneyTone.Warning -> money.warning
            MoneyTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

enum class MoneyTone { Neutral, Positive, Negative, Warning, Muted }

/**
 * A transaction's amount as a row shows it: signed, coloured, and with a
 * transfer left neutral because moving money is neither income nor spending.
 */
@Composable
fun TransactionAmount(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyMedium,
) {
    val money = MaterialTheme.money
    val colour: Color = when {
        transaction.isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        transaction.isInflow -> money.positive
        else -> LocalContentColor.current
    }

    val prefix = when {
        transaction.isTransfer -> ""
        transaction.isInflow -> "+"
        else -> "−"
    }

    Text(
        text = prefix + transaction.formattedAmount.removePrefix("−").removePrefix("-"),
        style = style,
        color = colour,
        modifier = modifier,
    )
}
