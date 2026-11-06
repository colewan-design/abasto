package com.budgettracker.feature.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.BudgetView
import com.budgettracker.data.model.Category
import com.budgettracker.data.remote.request.BudgetLineRequest
import com.budgettracker.data.remote.request.UpdateBudgetRequest
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.BudgetRepository
import com.budgettracker.data.repository.LedgerEvents
import com.budgettracker.data.repository.ReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * The editor's working copy: a limit per category as typed text, plus the
 * overall figure. Kept separate from the loaded budget so cancelling an edit
 * costs nothing and a failed save does not wipe what was typed.
 */
data class BudgetDraft(
    val overall: String = "",
    val limits: Map<Long, String> = emptyMap(),
    val rollover: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

data class BudgetUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val message: String? = null,
    val month: YearMonth = YearMonth.now(),
    val view: BudgetView = BudgetView(),
    val currency: Currency = Currency.Default,
    val categories: List<Category> = emptyList(),
    val draft: BudgetDraft? = null,
) {
    val isSet: Boolean get() = view.isSet
    val overall get() = view.meta.overall
    val lines get() = view.meta.categories
    val suggestions get() = view.meta.suggestions
}

/**
 * Monthly limits, by category.
 *
 * `PUT /budget` replaces the month's lines wholesale, so the editor always
 * sends every line it knows about — dropping one is how a limit is removed.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    private val reference: ReferenceRepository,
    ledger: LedgerEvents,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetUiState(currency = auth.currency))
    val state: StateFlow<BudgetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reference.ensureLoaded()
            _state.update { it.copy(categories = reference.expenseCategories()) }
        }

        load()

        // Spending recorded elsewhere changes what is left in each category.
        viewModelScope.launch {
            ledger.changed.collect { if (_state.value.draft == null) load() }
        }
    }

    fun load(month: YearMonth = _state.value.month) {
        viewModelScope.launch {
            _state.update { it.copy(month = month, loading = true, error = null) }

            when (val outcome = budgets.forMonth(month)) {
                is Outcome.Ok -> _state.update { it.copy(loading = false, view = outcome.value) }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    fun showMonth(month: YearMonth) = load(month)

    /* ------------------------------------------------------------ editing */

    fun startEditing() = _state.update { current ->
        val exponent = current.currency.exponent

        current.copy(
            draft = BudgetDraft(
                overall = current.view.data?.overallAmountMinor
                    ?.let { Money.majorInput(it, exponent) }
                    .orEmpty(),
                limits = current.lines.associate { line ->
                    line.categoryId to Money.majorInput(line.amountMinor, exponent)
                },
                rollover = current.view.data?.rolloverEnabled ?: false,
            ),
        )
    }

    /** Fills the editor from the server's suggestions, built on past spending. */
    fun applySuggestions() = _state.update { current ->
        val exponent = current.currency.exponent
        val suggested = current.suggestions.associate { suggestion ->
            suggestion.categoryId to Money.majorInput(suggestion.suggestedMinor, exponent)
        }

        current.copy(
            draft = (current.draft ?: BudgetDraft()).let { draft ->
                draft.copy(limits = draft.limits + suggested)
            },
        )
    }

    fun onOverallChange(value: String) = editDraft { it.copy(overall = value) }

    fun onLimitChange(categoryId: Long, value: String) = editDraft { draft ->
        draft.copy(limits = draft.limits + (categoryId to value))
    }

    fun removeLimit(categoryId: Long) = editDraft { draft ->
        draft.copy(limits = draft.limits - categoryId)
    }

    fun toggleRollover() = editDraft { it.copy(rollover = !it.rollover) }

    fun cancelEditing() = _state.update { it.copy(draft = null) }

    fun save() {
        val current = _state.value
        val draft = current.draft ?: return
        val exponent = current.currency.exponent

        viewModelScope.launch {
            editDraft { it.copy(saving = true, error = null) }

            val lines = draft.limits.mapNotNull { (categoryId, text) ->
                Money.parse(text, exponent)
                    ?.takeIf { it > 0L }
                    ?.let { BudgetLineRequest(categoryId = categoryId, amountMinor = it) }
            }

            val request = UpdateBudgetRequest(
                overallAmountMinor = Money.parse(draft.overall, exponent) ?: 0L,
                rolloverEnabled = draft.rollover,
                categories = lines,
            )

            when (val outcome = budgets.save(current.month, request)) {
                is Outcome.Ok -> _state.update {
                    it.copy(view = outcome.value, draft = null, message = "Budget saved.")
                }

                is Outcome.Err -> editDraft {
                    it.copy(saving = false, error = outcome.error.message)
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun editDraft(transform: (BudgetDraft) -> BudgetDraft) = _state.update { current ->
        current.copy(draft = (current.draft ?: BudgetDraft()).let(transform))
    }
}
