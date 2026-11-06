package com.budgettracker.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Transaction
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.DateHeader
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FlowTile
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.RoundIconButton
import com.budgettracker.ui.components.ScreenHeader
import com.budgettracker.ui.components.SoftCard
import com.budgettracker.ui.components.TabSurface
import com.budgettracker.ui.components.TransactionRow
import com.budgettracker.ui.theme.money

/**
 * Everything recorded, newest first, grouped by the day it happened.
 *
 * Each day is one card under its own heading, so a week of entries reads as
 * a handful of blocks rather than as one unbroken column — the day is the
 * only landmark a ledger of near-identical rows has.
 *
 * The totals above the list describe the whole filtered set rather than the
 * page on screen — otherwise narrowing to a category would show a total that
 * grows as you scroll.
 */
@Composable
fun ActivityScreen(
    onOpenTransaction: (Transaction) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var filtering by remember { mutableStateOf(false) }

    // Paging: when the last few rows come into view, ask for the next page.
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            lastVisible to listState.layoutInfo.totalItemsCount
        }.collect { (lastVisible, count) ->
            if (count > 0 && lastVisible >= count - 4) {
                viewModel.loadMore()
            }
        }
    }

    TabSurface(message = state.message, onMessageShown = viewModel::messageShown) { padding ->
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
            ScreenHeader(
                title = "Activity",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            ) {
                RoundIconButton(
                    icon = Icons.Outlined.FilterList,
                    contentDescription = "Filters",
                    onClick = { filtering = true },
                    size = 46.dp,
                    shape = RoundedCornerShape(16.dp),
                    // The count rather than a dot: a filtered list that looks
                    // short is only alarming until you can see how many
                    // things are narrowing it.
                    badge = state.filters.appliedCount.takeIf { it > 0 }?.toString(),
                )
            }

            SearchField(
                query = state.filters.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val money = MaterialTheme.money

                FlowTile(
                    label = "In",
                    value = state.totals.formatted.income,
                    icon = Icons.Outlined.Download,
                    tint = money.positive,
                    modifier = Modifier.weight(1f),
                )

                FlowTile(
                    label = "Out",
                    value = state.totals.formatted.expense,
                    icon = Icons.Outlined.Upload,
                    tint = money.negative,
                    modifier = Modifier.weight(1f),
                )

                FlowTile(
                    label = "Net",
                    value = state.totals.formatted.net,
                    icon = Icons.Outlined.Balance,
                    tint = if (state.totals.netMinor < 0L) money.negative else money.positive,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.filters.isNarrowed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.total} ${if (state.total == 1) "entry" else "entries"} match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    TextButton(onClick = viewModel::clearFilters) {
                        Text("Clear")
                    }
                }
            }

            when {
                state.loading -> CentredBox { FullScreenLoader() }

                state.transactions.isEmpty() && state.error != null -> CentredBox {
                    ErrorState(error = state.error!!, onRetry = { viewModel.load(reset = true) })
                }

                state.transactions.isEmpty() -> CentredBox {
                    if (state.filters.isNarrowed) {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "Nothing matches",
                            message = "Try a different search, or clear the filters.",
                            actionLabel = "Clear filters",
                            onAction = viewModel::clearFilters,
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.SwapVert,
                            title = "No entries yet",
                            message = "Record an expense, log income, or scan a receipt to " +
                                "get started.",
                        )
                    }
                }

                else -> {
                    val grouped = remember(state.transactions) {
                        state.transactions.groupBy { it.occurredOn }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            // Room for the record button, which floats over
                            // the foot of the list.
                            bottom = padding.calculateBottomPadding() + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        grouped.forEach { (day, rows) ->
                            item(key = "header-$day") {
                                DateHeader(label = Dates.relativeOrRaw(day), total = null)
                            }

                            item(key = "group-$day") {
                                SoftCard(padding = 0.dp) {
                                    rows.forEachIndexed { index, transaction ->
                                        TransactionRow(
                                            transaction = transaction,
                                            onClick = { onOpenTransaction(transaction) },
                                        )

                                        if (index != rows.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.padding(start = 68.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.loadingMore) {
                            item(key = "loading-more") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        if (!state.hasMore && state.transactions.size > 12) {
                            item(key = "end") {
                                Text(
                                    text = "That is everything.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filtering) {
            FilterSheet(
                state = state,
                onApply = { filters ->
                    viewModel.applyFilters(filters)
                    filtering = false
                },
                onDismiss = { filtering = false },
            )
        }
    }
}

/**
 * The search box: a capsule the width of the screen, filled rather than
 * boxed, so it reads as the one thing on the page you can type into.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search merchant, note or amount") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}
