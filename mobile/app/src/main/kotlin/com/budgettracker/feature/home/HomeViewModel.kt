package com.budgettracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.Dashboard
import com.budgettracker.data.model.User
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.LedgerEvents
import com.budgettracker.data.repository.NotificationRepository
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.data.repository.ReportRepository
import com.budgettracker.data.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: ApiException? = null,
    val month: YearMonth = YearMonth.now(),
    val dashboard: Dashboard? = null,
    val user: User? = null,
    val unreadNotifications: Int = 0,
) {
    val currency: Currency get() = user?.currency ?: Currency.Default
    val isDemo: Boolean get() = user?.isGuest == true
}

/**
 * The home screen reads from one endpoint — the dashboard — so what it shows
 * is always internally consistent: the balances, the budget progress and the
 * recent rows all describe the same instant.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val reports: ReportRepository,
    private val notifications: NotificationRepository,
    private val reference: ReferenceRepository,
    private val ledger: LedgerEvents,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.state.collect { session ->
                _state.update {
                    it.copy(user = (session as? SessionState.SignedIn)?.user)
                }
            }
        }

        load()

        // The forms on every other screen need these lists; fetching them
        // here means the first composer opens without a wait.
        viewModelScope.launch { reference.ensureLoaded() }

        // Anything recorded anywhere in the app changes these figures.
        viewModelScope.launch {
            ledger.changed.collect { load() }
        }
    }

    fun load(month: YearMonth = _state.value.month) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    month = month,
                    loading = it.dashboard == null,
                    refreshing = it.dashboard != null,
                    error = null,
                )
            }

            when (val outcome = reports.dashboard(month)) {
                is Outcome.Ok -> _state.update {
                    it.copy(loading = false, refreshing = false, dashboard = outcome.value)
                }

                is Outcome.Err -> _state.update {
                    it.copy(loading = false, refreshing = false, error = outcome.error)
                }
            }

            // A failure here is not worth a message; the badge simply stays put.
            notifications.unreadCount().let { outcome ->
                if (outcome is Outcome.Ok) {
                    _state.update { it.copy(unreadNotifications = outcome.value) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            reference.refresh()
            load()
        }
    }

    fun showMonth(month: YearMonth) = load(month)
}
