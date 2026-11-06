package com.budgettracker.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.CashFlowReport
import com.budgettracker.data.model.CategoryBreakdown
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.MerchantTotal
import com.budgettracker.data.model.MonthlyTrend
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class ReportsUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val month: YearMonth = YearMonth.now(),
    val type: CategoryType = CategoryType.Expense,
    val cashFlow: CashFlowReport = CashFlowReport(),
    val categories: List<CategoryBreakdown> = emptyList(),
    val trends: List<MonthlyTrend> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val currency: Currency = Currency.Default,
)

/**
 * The reports screen pulls four independent views of the same month, so they
 * are fetched together rather than one after another — the screen appears in
 * one step instead of four.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reports: ReportRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState(currency = auth.currency))
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(
        month: YearMonth = _state.value.month,
        type: CategoryType = _state.value.type,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(month = month, type = type, loading = true, error = null) }

            val cashFlow = async { reports.cashFlow(month) }
            val categories = async { reports.byCategory(month, type) }
            val trends = async { reports.trends(months = 6) }
            val merchants = async { reports.merchants(month, limit = 8) }

            val cashFlowResult = cashFlow.await()
            val categoriesResult = categories.await()
            val trendsResult = trends.await()
            val merchantsResult = merchants.await()

            // One failure is reported, but whatever did arrive is still shown.
            val failure = listOf(cashFlowResult, categoriesResult, trendsResult, merchantsResult)
                .filterIsInstance<Outcome.Err>()
                .firstOrNull()

            _state.update { current ->
                current.copy(
                    loading = false,
                    error = failure?.error,
                    cashFlow = (cashFlowResult as? Outcome.Ok)?.value ?: current.cashFlow,
                    categories = (categoriesResult as? Outcome.Ok)?.value ?: emptyList(),
                    trends = (trendsResult as? Outcome.Ok)?.value ?: current.trends,
                    merchants = (merchantsResult as? Outcome.Ok)?.value ?: emptyList(),
                )
            }
        }
    }

    fun showMonth(month: YearMonth) = load(month = month)

    fun showType(type: CategoryType) = load(type = type)
}
