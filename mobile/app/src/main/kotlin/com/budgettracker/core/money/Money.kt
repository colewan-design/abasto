package com.budgettracker.core.money

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The currency the signed-in account keeps its books in, as reported by the
 * API. `exponent` is how many decimal places the currency has, which is what
 * turns a minor-unit integer back into something a person reads.
 */
@Serializable
data class Currency(
    val code: String = "PHP",
    val symbol: String = "₱",
    val exponent: Int = 2,
) {
    companion object {
        val Default = Currency()
    }
}

/**
 * Amounts travel as integer minor units everywhere — in the API, in this app's
 * state, and in every calculation. They become a string only at the edge, in a
 * composable, which is the only thing this object exists for.
 *
 * The API already formats most figures it returns; these helpers cover the ones
 * it does not (a running total being typed into a form, a chart axis) and the
 * reverse trip from typed text back to minor units.
 */
object Money {

    /** `12345` -> `₱123.45`, or `-₱123.45`; `signed` adds an explicit `+`. */
    fun format(
        minor: Long,
        currency: Currency = Currency.Default,
        signed: Boolean = false,
        decimals: Boolean = true,
    ): String {
        val formatter = formatter(currency.exponent.takeIf { decimals } ?: 0)
        val magnitude = formatter.format(major(kotlin.math.abs(minor), currency.exponent))
        val sign = when {
            minor < 0L -> "−" // a true minus sign, which lines up with digits
            signed && minor > 0L -> "+"
            else -> ""
        }

        return "$sign${currency.symbol}$magnitude"
    }

    /**
     * Shortened for places where the full figure does not fit — a chart axis or
     * a dense tile. Thousands and millions only; smaller amounts are left alone
     * because rounding them away loses the point.
     */
    fun formatCompact(minor: Long, currency: Currency = Currency.Default): String {
        val units = major(kotlin.math.abs(minor), currency.exponent).toDouble()
        val sign = if (minor < 0L) "−" else ""

        val (value, suffix) = when {
            units >= 1_000_000_000 -> units / 1_000_000_000 to "B"
            units >= 1_000_000 -> units / 1_000_000 to "M"
            units >= 1_000 -> units / 1_000 to "k"
            else -> return format(minor, currency, decimals = units % 1.0 != 0.0)
        }

        // One decimal below ten (1.2k reads better than 1k); none above, where
        // the extra digit is noise.
        val pattern = if (value < 10) "0.#" else "0"

        return "$sign${currency.symbol}${DecimalFormat(pattern).format(value)}$suffix"
    }

    /** The amount as a decimal, for maths that has to leave minor units. */
    fun major(minor: Long, exponent: Int): BigDecimal =
        BigDecimal.valueOf(minor, exponent)

    /**
     * Plain digits for prefilling a text field: no symbol and no grouping, so
     * the value can be edited and parsed back without stripping anything.
     */
    fun majorInput(minor: Long, exponent: Int): String =
        if (minor == 0L) "" else major(minor, exponent).toPlainString()

    /**
     * Typed text back to minor units. Grouping separators and a currency symbol
     * are tolerated because people paste amounts in; anything that is not a
     * number at all returns null so the caller can show a validation message
     * instead of silently recording zero.
     */
    fun parse(input: String, exponent: Int): Long? {
        val cleaned = input.trim()
            .replace("−", "-")
            .filter { it.isDigit() || it == '.' || it == '-' }

        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null

        return runCatching {
            BigDecimal(cleaned)
                .movePointRight(exponent)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
    }

    /** A percentage of a limit, clamped so a wild overspend still renders. */
    fun share(part: Long, whole: Long): Float =
        if (whole <= 0L) 0f else (part.toDouble() / whole.toDouble()).toFloat()

    private fun formatter(fractionDigits: Int): DecimalFormat =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
}
