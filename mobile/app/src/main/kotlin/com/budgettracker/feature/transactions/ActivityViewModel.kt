package com.budgettracker.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category
import com.budgettracker.data.model.Tag
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionFilters
import com.budgettracker.data.model.TransactionTotals
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.LedgerEvents
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: ApiException? = null,
    val message: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val totals: TransactionTotals = TransactionTotals(),
    val filters: TransactionFilters = TransactionFilters(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val total: Int = 0,
    val currency: Currency = Currency.Default,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

/**
 * The ledger list: search, filters, and paging.
 *
 * Typing in the search box does not fire a request per keystroke — the query
 * is debounced — and the totals shown above the list are for the whole
 * filtered set, which the API returns alongside the page.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val reference: ReferenceRepository,
    ledger: LedgerEvents,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState(currency = auth.currency))
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    init {
        viewModelScope.launch {
            reference.ensureLoaded()

            _state.update {
                it.copy(
                    accounts = reference.accounts.value,
                    categories = reference.categories.value,
                    tags = reference.tags.value,
                )
            }
        }

        // The first page is fetched directly; later queries come through the
        // debounced stream so a fast typist makes one request, not ten.
        load(reset = true)

        viewModelScope.launch {
            _state
                .map { it.filters.query }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .collect { load(reset = true) }
        }

        // A receipt confirmed, a bill paid, an entry saved from another screen
        // all belong in this list.
        viewModelScope.launch {
            ledger.changed.collect { load(reset = true) }
        }
    }

    fun load(reset: Boolean) {
        inFlight?.cancel()

        val page = if (reset) 1 else _state.value.page + 1

        inFlight = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = reset && it.transactions.isEmpty(),
                    loadingMore = !reset,
                    error = null,
                )
            }

            when (val outcome = transactions.list(_state.value.filters, page = page)) {
                is Outcome.Ok -> _state.update { current ->
                    val rows = outcome.value.data

                    current.copy(
                        loading = false,
                        loadingMore = false,
                        transactions = if (reset) rows else current.transactions + rows,
                        totals = outcome.value.meta.totals,
                        page = outcome.value.meta.currentPage,
                        hasMore = outcome.value.meta.hasMore,
                        total = outcome.value.meta.total,
                    )
                }

                is Outcome.Err -> _state.update {
                    it.copy(loading = false, loadingMore = false, error = outcome.error)
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value

        if (current.hasMore && !current.loadingMore && !current.loading) {
            load(reset = false)
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(filters = it.filters.copy(query = value)) }

    fun applyFilters(filters: TransactionFilters) {
        _state.update { it.copy(filters = filters) }
        load(reset = true)
    }

    fun clearFilters() {
        _state.update { it.copy(filters = it.filters.cleared()) }
        load(reset = true)
    }

    fun refresh() {
        viewModelScope.launch {
            reference.refresh()
            _state.update {
                it.copy(
                    accounts = reference.accounts.value,
                    categories = reference.categories.value,
                    tags = reference.tags.value,
                )
            }
        }

        load(reset = true)
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            when (val outcome = transactions.delete(transaction.id)) {
                is Outcome.Ok -> {
                    // A transfer is two rows sharing a group; removing one
                    // removes both server-side, so the list is refetched
                    // rather than patched.
                    if (transaction.isTransfer) {
                        load(reset = true)
                    } else {
                        _state.update { current ->
                            current.copy(
                                transactions = current.transactions.filterNot { it.id == transaction.id },
                            )
                        }
                    }

                    _state.update { it.copy(message = "Deleted.") }
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    /** Called after the composer saves, so the list shows the new row. */
    fun onSaved() = load(reset = true)

    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
