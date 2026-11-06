package com.budgettracker.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DatesTest {

    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `parses the wire format and ignores a trailing time`() {
        assertEquals(LocalDate.of(2026, 3, 15), Dates.parse("2026-03-15"))
        assertEquals(LocalDate.of(2026, 3, 15), Dates.parse("2026-03-15T08:30:00+08:00"))
    }

    @Test
    fun `returns null for a date it cannot read, rather than inventing one`() {
        assertNull(Dates.parse(null))
        assertNull(Dates.parse(""))
        assertNull(Dates.parse("not a date"))
        assertNull(Dates.parse("15/03/2026"))
    }

    @Test
    fun `writes dates in the only format the API accepts`() {
        assertEquals("2026-03-15", Dates.wire(today))
        assertEquals("2026-03", Dates.wireMonth(YearMonth.of(2026, 3)))
    }

    @Test
    fun `labels recent dates in words and older ones by date`() {
        assertEquals("Today", Dates.relative(today, today))
        assertEquals("Yesterday", Dates.relative(today.minusDays(1), today))
        assertEquals("Tomorrow", Dates.relative(today.plusDays(1), today))
    }

    @Test
    fun `drops the year for this year and keeps it for another`() {
        assertEquals("Jan 4", Dates.relative(LocalDate.of(2026, 1, 4), today))
        assertEquals("Dec 4, 2025", Dates.relative(LocalDate.of(2025, 12, 4), today))
    }

    @Test
    fun `says plainly whether a bill is due or late`() {
        assertEquals("Due today", Dates.dueLabel(0))
        assertEquals("Due tomorrow", Dates.dueLabel(1))
        assertEquals("Due in 5 days", Dates.dueLabel(5))
        assertEquals("1 day overdue", Dates.dueLabel(-1))
        assertEquals("4 days overdue", Dates.dueLabel(-4))
    }

    @Test
    fun `shows an unreadable date as it came rather than as a blank`() {
        assertEquals("sometime", Dates.relativeOrRaw("sometime"))
        assertEquals("", Dates.relativeOrRaw(null))
    }

    @Test
    fun `reads a month back out of the wire format`() {
        assertEquals(YearMonth.of(2026, 3), Dates.month("2026-03"))
        assertEquals(YearMonth.of(2026, 3), Dates.month("2026-03-15"))
        assertNull(Dates.month("nope"))
    }
}
