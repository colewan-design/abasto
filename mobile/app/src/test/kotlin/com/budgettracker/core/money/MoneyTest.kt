package com.budgettracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Money is the one place a bug is unforgivable, and the risky direction is
 * parsing: whatever a person types has to become the right number of minor
 * units or be rejected outright — never silently become zero.
 */
class MoneyTest {

    private val peso = Currency(code = "PHP", symbol = "₱", exponent = 2)
    private val yen = Currency(code = "JPY", symbol = "¥", exponent = 0)

    @Test
    fun `formats minor units with grouping and the currency symbol`() {
        assertEquals("₱1,234.56", Money.format(123_456, peso))
        assertEquals("₱0.00", Money.format(0, peso))
    }

    @Test
    fun `formats a negative amount with a minus sign, not a bracket`() {
        assertEquals("−₱1,234.56", Money.format(-123_456, peso))
    }

    @Test
    fun `adds an explicit plus only when asked and only when positive`() {
        assertEquals("+₱12.00", Money.format(1_200, peso, signed = true))
        assertEquals("₱12.00", Money.format(1_200, peso))
        assertEquals("₱0.00", Money.format(0, peso, signed = true))
    }

    @Test
    fun `respects a currency with no decimal places`() {
        assertEquals("¥1,500", Money.format(1_500, yen))
    }

    @Test
    fun `parses plain decimals into minor units`() {
        assertEquals(123_456L, Money.parse("1234.56", exponent = 2))
        assertEquals(1_200L, Money.parse("12", exponent = 2))
        assertEquals(50L, Money.parse("0.5", exponent = 2))
    }

    @Test
    fun `parses pasted amounts carrying grouping and a symbol`() {
        assertEquals(123_456L, Money.parse("₱1,234.56", exponent = 2))
        assertEquals(123_456L, Money.parse(" 1 234.56 ", exponent = 2))
    }

    @Test
    fun `rounds half up rather than truncating a third decimal`() {
        assertEquals(1_235L, Money.parse("12.345", exponent = 2))
        assertEquals(1_234L, Money.parse("12.344", exponent = 2))
    }

    @Test
    fun `parses a negative amount, for an opening balance that is owed`() {
        assertEquals(-123_456L, Money.parse("-1234.56", exponent = 2))
        assertEquals(-123_456L, Money.parse("−1234.56", exponent = 2))
    }

    @Test
    fun `refuses text that is not an amount instead of returning zero`() {
        assertNull(Money.parse("", exponent = 2))
        assertNull(Money.parse("   ", exponent = 2))
        assertNull(Money.parse("abc", exponent = 2))
        assertNull(Money.parse("-", exponent = 2))
        assertNull(Money.parse(".", exponent = 2))
    }

    @Test
    fun `round trips an amount through the input format`() {
        val minor = 987_65L
        val typed = Money.majorInput(minor, exponent = 2)

        assertEquals("987.65", typed)
        assertEquals(minor, Money.parse(typed, exponent = 2))
    }

    @Test
    fun `leaves an empty field empty rather than showing a zero to overwrite`() {
        assertEquals("", Money.majorInput(0, exponent = 2))
    }

    @Test
    fun `shortens large figures for a chart axis`() {
        assertEquals("₱1.2k", Money.formatCompact(123_456, peso))
        assertEquals("₱12k", Money.formatCompact(1_234_567, peso))
        assertEquals("₱1.2M", Money.formatCompact(123_456_789, peso))
    }

    @Test
    fun `share is a fraction of the limit, and zero when there is no limit`() {
        assertEquals(0.5f, Money.share(50, 100), 0.001f)
        assertEquals(0f, Money.share(50, 0), 0.001f)
        assertEquals(1.5f, Money.share(150, 100), 0.001f)
    }
}
