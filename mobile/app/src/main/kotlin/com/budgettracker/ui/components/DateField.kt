package com.budgettracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.budgettracker.core.time.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A date field backed by the Material date picker.
 *
 * Dates in this app are calendar days, not instants — the day an expense
 * happened does not move when the phone's timezone does — so the picker's
 * UTC-based millis are converted at the boundary and never kept as a timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date",
    error: String? = null,
    allowFuture: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = Dates.medium(date),
        placeholder = "Pick a date",
        onClick = { showPicker = true },
        error = error,
        modifier = modifier,
    )

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = if (allowFuture) DatePickerDefaults.AllDates else PastOrToday,
        )

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onDateChange(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }

                        showPicker = false
                    },
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, title = null)
        }
    }
}

/**
 * A date field that can be empty.
 *
 * A filter with no date set must not show one — a field reading "Mar 1" when
 * nothing is filtered says something untrue about what the list contains.
 */
@Composable
fun OptionalDateField(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date",
    placeholder: String = "Any",
    fallback: LocalDate = Dates.today(),
) {
    var showPicker by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = date?.let(Dates::medium),
        placeholder = placeholder,
        onClick = { showPicker = true },
        modifier = modifier,
    )

    if (showPicker) {
        DatePickerDialogFor(
            initial = date ?: fallback,
            onDismiss = { showPicker = false },
            onPicked = { picked ->
                onDateChange(picked)
                showPicker = false
            },
            clearable = date != null,
            onClear = {
                onDateChange(null)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogFor(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    clearable: Boolean,
    onClear: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = if (clearable) onClear else onDismiss) {
                Text(if (clearable) "Clear" else "Cancel")
            }
        },
    ) {
        DatePicker(state = state, title = null)
    }
}

/** Blocks future dates, for things that can only have already happened. */
@OptIn(ExperimentalMaterial3Api::class)
private val PastOrToday = object : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant.ofEpochMilli(utcTimeMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .isAfter(LocalDate.now(ZoneId.systemDefault()))

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

/** Steps through months, as the budget and report screens do. */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    allowFuture: Boolean = false,
) {
    // Nothing has happened next month yet, so stepping into it is normally off.
    val canGoForward = allowFuture || month.isBefore(YearMonth.now())

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = "Previous month",
            )
        }

        Text(
            text = Dates.monthLabel(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        IconButton(
            onClick = { onMonthChange(month.plusMonths(1)) },
            enabled = canGoForward,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Next month",
            )
        }
    }
}
