package com.budgettracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoNotDisturbAlt
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category

/**
 * A form field that opens a chooser instead of a keyboard.
 *
 * It is a disabled text field on purpose: it inherits the same height, label
 * behaviour and outline as every other field in the form, so a row of inputs
 * stays visually even whether it is typed into or picked from.
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Box {
            OutlinedTextField(
                value = value ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                isError = error != null,
                leadingIcon = leading,
                trailingIcon = {
                    Icon(imageVector = Icons.Outlined.ExpandMore, contentDescription = null)
                },
                colors = pickerFieldColours(),
                modifier = Modifier.fillMaxWidth(),
            )

            // The field itself is disabled so it cannot be typed into; this
            // layer is what makes the whole row tappable.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick),
            )
        }

        if (error != null) {
            FieldError(error)
        }
    }
}

@Composable
private fun pickerFieldColours() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * The frame every chooser uses.
 *
 * A dialog rather than a bottom sheet, deliberately: most of these are opened
 * from inside a sheet — the composer, the bill form, the receipt review — and
 * a second modal sheet layered over the first leaves the input focus on the
 * window underneath, so neither one can be tapped. A dialog stacks over a
 * sheet correctly, and dismisses with the back gesture as expected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                SheetTitle(title)

                content()
            }
        }
    }
}

@Composable
fun AccountPickerSheet(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Account) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose an account",
) {
    PickerDialog(title = title, onDismiss = onDismiss) {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(accounts, key = { it.id }) { account ->
                ChoiceRow(
                    label = account.name,
                    secondary = "${account.typeLabel} · ${account.formatted.balance}",
                    selected = account.id == selectedId,
                    onClick = { onSelect(account) },
                    leading = { AccountBadge(account = account, size = 36.dp) },
                )
            }
        }
    }
}

@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Category?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose a category",
    allowNone: Boolean = true,
) {
    PickerDialog(title = title, onDismiss = onDismiss) {
        LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
            if (allowNone) {
                item {
                    ChoiceRow(
                        label = "No category",
                        secondary = "Leave it uncategorised for now",
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                        leading = {
                            IconBadge(
                                icon = Icons.Outlined.DoNotDisturbAlt,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 36.dp,
                            )
                        },
                    )
                }
            }

            items(categories, key = { it.id }) { category ->
                ChoiceRow(
                    label = category.name,
                    secondary = category.group?.name,
                    selected = category.id == selectedId,
                    onClick = { onSelect(category) },
                    leading = { CategoryBadge(category = category, size = 36.dp) },
                )
            }
        }
    }
}

/** Picking one of a short list of plain values. */
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(title = title, onDismiss = onDismiss) {
        // Lazy rather than a plain column: the currency list is long enough to
        // need scrolling, and the short ones cost nothing.
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(options) { (value, label) ->
                ChoiceRow(
                    label = label,
                    secondary = null,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )

        action?.invoke()
    }
}

@Composable
fun ChoiceRow(
    label: String,
    secondary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SheetActions(
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }

        TextButton(onClick = onConfirm, enabled = enabled) { Text(confirmLabel) }
    }
}
