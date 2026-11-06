package com.budgettracker.core.time

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dates on the wire are `Y-m-d` (a calendar day, in the account's own
 * timezone) or ISO-8601 (an instant). Nothing here guesses: a string that does
 * not parse comes back as null and the caller shows the raw value rather than
 * an invented date.
 */
object Dates {

    /**
     * The locale is applied when a date is formatted rather than when the
     * formatter is built: these live for the whole process, and someone who
     * changes their language should see the change without restarting the app.
     */
    private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d")
    private val MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val WEEKDAY_LONG = DateTimeFormatter.ofPattern("EEEE, MMM d")
    private val MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")

    private fun LocalDate.render(formatter: DateTimeFormatter): String =
        format(formatter.withLocale(Locale.getDefault()))

    fun today(): LocalDate = LocalDate.now()

    fun parse(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null

        return try {
            LocalDate.parse(value.take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseInstant(value: String?): ZonedDateTime? {
        if (value.isNullOrBlank()) return null

        return try {
            ZonedDateTime.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** `Y-m-d`, the only date format the API accepts on a write. */
    fun wire(date: LocalDate): String = date.toString()

    /** `Y-m` for the month-scoped endpoints (budget, reports). */
    fun wireMonth(month: YearMonth): String = month.toString()

    fun month(value: String?): YearMonth? {
        if (value.isNullOrBlank()) return null

        return try {
            YearMonth.parse(value.take(7))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun monthLabel(month: YearMonth): String = month.atDay(1).render(MONTH_LABEL)

    fun shortMonthLabel(month: YearMonth): String = month.atDay(1).render(MONTH_SHORT)

    /**
     * How a date reads in a list: relative while it is still fresh in mind,
     * absolute once it is not, and the year only when it is not this one.
     */
    fun relative(date: LocalDate, today: LocalDate = today()): String {
        val days = ChronoUnit.DAYS.between(date, today)

        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days == -1L -> "Tomorrow"
            days in 2L..6L -> date.render(WEEKDAY_LONG)
            date.year == today.year -> date.render(MONTH_DAY)
            else -> date.render(MONTH_DAY_YEAR)
        }
    }

    fun medium(date: LocalDate): String = date.render(MONTH_DAY_YEAR)

    fun short(date: LocalDate): String = date.render(MONTH_DAY)

    /** "in 3 days" / "4 days overdue", from the API's `days_until_due`. */
    fun dueLabel(daysUntilDue: Int): String = when {
        daysUntilDue == 0 -> "Due today"
        daysUntilDue == 1 -> "Due tomorrow"
        daysUntilDue > 1 -> "Due in $daysUntilDue days"
        daysUntilDue == -1 -> "1 day overdue"
        else -> "${-daysUntilDue} days overdue"
    }

    /** Label for a date string straight off the wire, unparsed values intact. */
    fun relativeOrRaw(value: String?): String =
        parse(value)?.let { relative(it) } ?: value.orEmpty()
}
