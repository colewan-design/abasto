package com.budgettracker.ui

import com.budgettracker.core.money.Money
import com.budgettracker.data.model.TransactionFilters
import com.budgettracker.ui.components.sanitize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The amount field holds text while it is being typed, so what it allows
 * through decides whether the value can be parsed when the form is saved.
 */
class AmountInputTest {

    @Test
    fun `keeps a half-typed amount intact instead of rewriting it`() {
        assertEquals("12.", sanitize("12.", exponent = 2))
        assertEquals("0.0", sanitize("0.0", exponent = 2))
        assertEquals("", sanitize("", exponent = 2))
    }

    @Test
    fun `drops characters that could never be part of an amount`() {
        assertEquals("1234.56", sanitize("₱1234.56abc", exponent = 2))
    }

    @Test
    fun `treats a comma as grouping when a decimal point is there too`() {
        assertEquals("1234.56", sanitize("1,234.56", exponent = 2))
        assertEquals("1234567.89", sanitize("1,234,567.89", exponent = 2))
    }

    @Test
    fun `treats a lone comma as the decimal separator`() {
        assertEquals("1234.56", sanitize("1234,56", exponent = 2))
    }

    @Test
    fun `allows only one separator, however many are typed`() {
        assertEquals("12.34", sanitize("12.3.4", exponent = 2))
    }

    @Test
    fun `will not accept more decimals than the currency has`() {
        assertEquals("12.34", sanitize("12.3456", exponent = 2))
    }

    @Test
    fun `drops the decimals entirely for a currency that has none`() {
        assertEquals("12", sanitize("12.34", exponent = 0))
        assertEquals("1500", sanitize("1500", exponent = 0))
    }

    @Test
    fun `whatever it allows through stays parseable`() {
        listOf("12", "12.", "12.3", "12.34", "0.05", "999999.99").forEach { typed ->
            val cleaned = sanitize(typed, exponent = 2)

            assertEquals(cleaned, sanitize(cleaned, exponent = 2))

            if (cleaned.isNotEmpty() && cleaned != ".") {
                assertTrue(
                    "expected `$cleaned` to parse",
                    Money.parse(cleaned, exponent = 2) != null,
                )
            }
        }
    }
}

/** The filter button's badge counts what is actually narrowing the list. */
class TransactionFiltersTest {

    @Test
    fun `the default view counts as unnarrowed`() {
        val filters = TransactionFilters()

        assertEquals(0, filters.appliedCount)
        assertFalse(filters.isNarrowed)
    }

    @Test
    fun `each kind of narrowing counts once`() {
        val filters = TransactionFilters(
            accountIds = listOf(1L, 2L),
            categoryIds = listOf(3L),
            hasReceipt = true,
        )

        assertEquals(3, filters.appliedCount)
        assertTrue(filters.isNarrowed)
    }

    @Test
    fun `a search on its own narrows without counting towards the badge`() {
        val filters = TransactionFilters(query = "coffee")

        assertEquals(0, filters.appliedCount)
        assertTrue(filters.isNarrowed)
    }

    @Test
    fun `clearing keeps the search, which lives in its own box`() {
        val cleared = TransactionFilters(
            query = "coffee",
            accountIds = listOf(1L),
            uncategorized = true,
        ).cleared()

        assertEquals("coffee", cleared.query)
        assertEquals(0, cleared.appliedCount)
    }
}
