package com.budgettracker.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.AccountType
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.CurrencyOption
import com.budgettracker.data.model.OnboardingOptions
import com.budgettracker.data.remote.request.CompleteOnboardingRequest
import com.budgettracker.data.remote.request.OnboardingAccount
import com.budgettracker.data.remote.request.OnboardingBudget
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.OnboardingRepository
import com.budgettracker.data.repository.ReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** One account being set up, while it is still being typed. */
data class AccountDraft(
    val key: Int,
    val name: String = "",
    val type: AccountType = AccountType.Cash,
    val balance: String = "",
    val creditLimit: String = "",
) {
    val isLiability: Boolean get() = type == AccountType.CreditCard
}

enum class SetupStep { Currency, Accounts, Categories, Budget }

data class OnboardingUiState(
    val step: SetupStep = SetupStep.Currency,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val options: OnboardingOptions? = null,
    val currencyCode: String = "PHP",
    val accounts: List<AccountDraft> = listOf(AccountDraft(key = 0, name = "Cash")),
    val chosenCategories: Set<String> = emptySet(),
    val overallBudget: String = "",
) {
    val currency: Currency
        get() = options?.currencies
            ?.firstOrNull { it.code == currencyCode }
            ?.let { Currency(code = it.code, symbol = it.symbol, exponent = it.exponent) }
            ?: Currency.Default

    /** Every account needs a name before the ledger can mean anything. */
    val accountsReady: Boolean
        get() = accounts.isNotEmpty() && accounts.all { it.name.isNotBlank() }

    val canContinue: Boolean
        get() = when (step) {
            SetupStep.Currency -> currencyCode.isNotBlank()
            SetupStep.Accounts -> accountsReady
            SetupStep.Categories -> chosenCategories.isNotEmpty()
            SetupStep.Budget -> true
        }

    val progress: Float get() = (SetupStep.entries.indexOf(step) + 1) / SetupStep.entries.size.toFloat()
}

/**
 * Guided setup.
 *
 * Everything is collected locally and written in one request at the end, so
 * abandoning setup halfway leaves no half-built account behind. The catalog of
 * currencies, account types and starter categories comes from the server, which
 * is the same catalog the web client offers.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboarding: OnboardingRepository,
    private val auth: AuthRepository,
    private val reference: ReferenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var nextKey = 1

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            when (val outcome = onboarding.options()) {
                is Outcome.Ok -> _state.update { current ->
                    current.copy(
                        loading = false,
                        options = outcome.value,
                        currencyCode = outcome.value.detectedCurrency,
                        // The server marks the categories most people want; they
                        // start ticked so the common path is one tap.
                        chosenCategories = outcome.value.recommendedCategories.toSet(),
                    )
                }

                is Outcome.Err -> _state.update {
                    it.copy(loading = false, error = outcome.error.message)
                }
            }
        }
    }

    fun chooseCurrency(option: CurrencyOption) = _state.update { it.copy(currencyCode = option.code) }

    fun back() = _state.update { current ->
        val index = SetupStep.entries.indexOf(current.step)

        current.copy(step = SetupStep.entries[(index - 1).coerceAtLeast(0)])
    }

    fun next() = _state.update { current ->
        val index = SetupStep.entries.indexOf(current.step)

        current.copy(step = SetupStep.entries[(index + 1).coerceAtMost(SetupStep.entries.lastIndex)])
    }

    /* ---------------------------------------------------------- accounts */

    fun addAccount() = _state.update { current ->
        current.copy(accounts = current.accounts + AccountDraft(key = nextKey++))
    }

    fun removeAccount(key: Int) = _state.update { current ->
        current.copy(accounts = current.accounts.filterNot { it.key == key })
    }

    fun editAccount(key: Int, transform: (AccountDraft) -> AccountDraft) = _state.update { current ->
        current.copy(accounts = current.accounts.map { if (it.key == key) transform(it) else it })
    }

    /* -------------------------------------------------------- categories */

    fun toggleCategory(name: String) = _state.update { current ->
        current.copy(
            chosenCategories = if (name in current.chosenCategories) {
                current.chosenCategories - name
            } else {
                current.chosenCategories + name
            },
        )
    }

    fun toggleGroup(names: List<String>) = _state.update { current ->
        val allChosen = names.all { it in current.chosenCategories }

        current.copy(
            chosenCategories = if (allChosen) {
                current.chosenCategories - names.toSet()
            } else {
                current.chosenCategories + names
            },
        )
    }

    fun onBudgetChange(value: String) = _state.update { it.copy(overallBudget = value) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Writes the whole setup. On success the cached user is replaced, which is
     * what moves the app out of the onboarding graph — no navigation here.
     */
    fun finish(onDone: () -> Unit) {
        val current = _state.value
        val exponent = current.currency.exponent

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }

            val request = CompleteOnboardingRequest(
                currencyCode = current.currencyCode,
                locale = Locale.getDefault().toLanguageTag().take(10),
                timezone = TimeZone.getDefault().id,
                accounts = current.accounts.map { draft ->
                    OnboardingAccount(
                        name = draft.name.trim(),
                        type = draft.type.wire,
                        openingBalanceMinor = Money.parse(draft.balance, exponent) ?: 0L,
                        creditLimitMinor = draft.creditLimit
                            .takeIf { draft.isLiability && it.isNotBlank() }
                            ?.let { Money.parse(it, exponent) },
                    )
                },
                categories = current.chosenCategories.toList(),
                budget = Money.parse(current.overallBudget, exponent)
                    ?.takeIf { it > 0L }
                    ?.let { OnboardingBudget(overallAmountMinor = it) },
            )

            when (val outcome = onboarding.complete(request)) {
                is Outcome.Ok -> {
                    auth.cacheUser(outcome.value.user)
                    reference.refresh()
                    _state.update { it.copy(saving = false) }
                    onDone()
                }

                is Outcome.Err -> _state.update {
                    it.copy(saving = false, error = outcome.error.message)
                }
            }
        }
    }

    /** The expense side of the catalog, which is what setup offers to pick. */
    fun expenseCatalog() = _state.value.options
        ?.catalog
        ?.firstOrNull { it.type == CategoryType.Expense }
        ?.groups
        .orEmpty()
}
