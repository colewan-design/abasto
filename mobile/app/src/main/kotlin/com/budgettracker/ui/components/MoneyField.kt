package com.budgettracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgettracker.core.money.Currency

/**
 * The amount input.
 *
 * It holds the typed text rather than a number, so a half-typed "12." is not
 * rewritten under the user's cursor; the caller converts to minor units with
 * `Money.parse` when it saves. Only digits and one decimal separator get
 * through, which removes the whole class of "why won't it accept this" bugs.
 */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: Currency,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    large: Boolean = false,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed -> onValueChange(sanitize(typed, currency.exponent)) },
            label = { Text(label) },
            prefix = { Text(currency.symbol) },
            placeholder = { Text("0") },
            isError = error != null,
            singleLine = true,
            textStyle = if (large) {
                TextStyle(fontSize = 28.sp, textAlign = TextAlign.End)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = imeAction,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            FieldError(error)
        }
    }
}

/**
 * Keeps the text a parseable amount while it is being typed: one separator at
 * most, no more decimals than the currency has, and no stray characters from a
 * paste.
 */
internal fun sanitize(input: String, exponent: Int): String {
    // A comma means grouping when a dot is present too ("1,234.56") and the
    // decimal separator when it is the only one ("1234,56"). Both get pasted
    // in, and guessing wrong moves the decimal point by a factor of a hundred.
    val normalised = if (input.contains('.')) {
        input.replace(",", "")
    } else {
        input.replace(',', '.')
    }

    val filtered = normalised.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')

    if (firstDot < 0) return filtered.take(15)

    val whole = filtered.substring(0, firstDot).take(13)

    // A currency with no minor unit has no decimals to keep, so the fraction
    // is dropped rather than folded into the whole part.
    if (exponent <= 0) return whole

    val fraction = filtered.substring(firstDot + 1)
        .filter(Char::isDigit)
        .take(exponent)

    return "$whole.$fraction"
}

@Composable
fun FieldError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A labelled row of choices, used wherever a form picks one of a few values.
 *
 * The chips are capsules rather than Material's gently rounded rectangle, so
 * a choice looks like every other pill in the app — the month chip, the
 * status chip, the range switch — instead of like a stray form control.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            androidx.compose.material3.FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
            )
        }
    }
}
