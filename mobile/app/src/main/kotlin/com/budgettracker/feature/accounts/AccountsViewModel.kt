package com.budgettracker.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.AccountType
import com.budgettracker.data.model.Balances
import com.budgettracker.data.remote.request.AccountRequest
import com.budgettracker.data.repository.AccountRepository
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.ReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The account form, while it is being filled in. */
data class AccountForm(
    val id: Long? = null,
    val name: String = "",
    val type: AccountType = AccountType.Cash,
    val openingBalance: String = "",
    val creditLimit: String = "",
    val institution: String = "",
    val includeInTotal: Boolean = true,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isEditing: Boolean get() = id != null
    val isLiability: Boolean get() = type == AccountType.CreditCard
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

data class AccountsUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val message: String? = null,
    val accounts: List<Account> = emptyList(),
    val balances: Balances = Balances(),
    val showArchived: Boolean = false,
    val currency: Currency = Currency.Default,
    val form: AccountForm? = null,
) {
    val active: List<Account> get() = accounts.filterNot { it.isArchived }
    val archived: List<Account> get() = accounts.filter { it.isArchived }
}

/**
 * Accounts and what they add up to.
 *
 * An opening balance is editable after the fact because people enter one
 * roughly and correct it later; the server rebuilds the running balance from
 * the ledger when it changes.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val reference: ReferenceRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState(currency = auth.currency))
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.accounts.isEmpty(), error = null) }

            when (val outcome = accounts.list(includeArchived = true)) {
                is Outcome.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        accounts = outcome.value.data,
                        balances = outcome.value.meta,
                    )
                }

                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    fun toggleArchived() = _state.update { it.copy(showArchived = !it.showArchived) }

    /* ------------------------------------------------------------- the form */

    fun addAccount() = _state.update { it.copy(form = AccountForm()) }

    fun editAccount(account: Account) = _state.update { current ->
        current.copy(
            form = AccountForm(
                id = account.id,
                name = account.name,
                type = account.type,
                openingBalance = Money.majorInput(
                    account.openingBalanceMinor,
                    current.currency.exponent,
                ),
                creditLimit = account.creditLimitMinor
                    ?.let { Money.majorInput(it, current.currency.exponent) }
                    .orEmpty(),
                institution = account.institution.orEmpty(),
                includeInTotal = account.includeInTotal,
            ),
        )
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    fun editForm(transform: (AccountForm) -> AccountForm) = _state.update { current ->
        current.copy(form = current.form?.let(transform))
    }

    fun saveForm() {
        val form = _state.value.form ?: return
        val exponent = _state.value.currency.exponent

        viewModelScope.launch {
            editForm { it.copy(saving = true, fieldErrors = emptyMap()) }

            val request = AccountRequest(
                name = form.name.trim(),
                type = form.type.wire,
                openingBalanceMinor = Money.parse(form.openingBalance, exponent) ?: 0L,
                creditLimitMinor = form.creditLimit
                    .takeIf { form.isLiability && it.isNotBlank() }
                    ?.let { Money.parse(it, exponent) },
                institution = form.institution.trim().takeIf { it.isNotBlank() },
                includeInTotal = form.includeInTotal,
            )

            val outcome = when (val id = form.id) {
                null -> accounts.create(request)
                else -> accounts.update(id, request)
            }

            when (outcome) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(form = null, message = "${outcome.value.name} saved.")
                    }

                    reference.refresh()
                    load()
                }

                is Outcome.Err -> editForm {
                    it.copy(
                        saving = false,
                        fieldErrors = outcome.error.errors.mapValues { (_, m) -> m.first() },
                    )
                }
            }
        }
    }

    /* ----------------------------------------------------------- lifecycle */

    fun archive(account: Account) = mutate("${account.name} archived.") {
        accounts.archive(account.id)
    }

    fun restore(account: Account) = mutate("${account.name} restored.") {
        accounts.restore(account.id)
    }

    /**
     * Deleting an account that has history archives it instead — the server
     * decides, and says which it did, so the message is never a lie.
     */
    fun delete(account: Account) {
        viewModelScope.launch {
            when (val outcome = accounts.delete(account.id)) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(message = outcome.value.message ?: "${account.name} removed.")
                    }

                    reference.refresh()
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun recalculate() {
        viewModelScope.launch {
            when (val outcome = accounts.recalculate()) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = outcome.value) }
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun mutate(success: String, block: suspend () -> Outcome<Account>) {
        viewModelScope.launch {
            when (val outcome = block()) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = success) }
                    reference.refresh()
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }
}
