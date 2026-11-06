package com.budgettracker.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category
import com.budgettracker.data.model.RecurringRule
import com.budgettracker.data.model.TransactionType
import com.budgettracker.data.remote.request.RecurringRuleRequest
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.RecurringRepository
import com.budgettracker.data.repository.ReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RuleForm(
    val id: Long? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.Expense,
    val amount: String = "",
    val accountId: Long? = null,
    val transferAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val frequency: String = "monthly",
    val startDate: LocalDate = Dates.today(),
    val autoPost: Boolean = true,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isEditing: Boolean get() = id != null
    val isTransfer: Boolean get() = type == TransactionType.Transfer

    val canSave: Boolean
        get() = name.isNotBlank() &&
            amount.isNotBlank() &&
            accountId != null &&
            (!isTransfer || transferAccountId != null) &&
            !saving
}

data class RecurringUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val message: String? = null,
    val rules: List<RecurringRule> = emptyList(),
    val currency: Currency = Currency.Default,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val form: RuleForm? = null,
) {
    val active: List<RecurringRule> get() = rules.filter { it.isActive }
    val paused: List<RecurringRule> get() = rules.filterNot { it.isActive }
}

/**
 * Recurring transactions.
 *
 * The server materialises due occurrences nightly; "Run now" posts the ones
 * already due without waiting, which is also how a rule can be tested right
 * after it is created.
 */
@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    private val reference: ReferenceRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecurringUiState(currency = auth.currency))
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reference.ensureLoaded()

            _state.update {
                it.copy(
                    accounts = reference.accounts.value.filterNot { a -> a.isArchived },
                    categories = reference.categories.value,
                )
            }
        }

        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.rules.isEmpty(), error = null) }

            when (val outcome = recurring.list()) {
                is Outcome.Ok -> _state.update { it.copy(loading = false, rules = outcome.value) }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    fun addRule() = _state.update { current ->
        current.copy(form = RuleForm(accountId = reference.defaultAccount()?.id))
    }

    fun editRule(rule: RecurringRule) = _state.update { current ->
        current.copy(
            form = RuleForm(
                id = rule.id,
                name = rule.name,
                type = rule.type,
                amount = Money.majorInput(rule.amountMinor, current.currency.exponent),
                accountId = rule.accountId,
                transferAccountId = rule.transferAccountId,
                categoryId = rule.categoryId,
                merchant = rule.merchant.orEmpty(),
                frequency = rule.frequency,
                startDate = Dates.parse(rule.startDate) ?: Dates.today(),
                autoPost = rule.autoPost,
            ),
        )
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    fun editForm(transform: (RuleForm) -> RuleForm) = _state.update { current ->
        current.copy(form = current.form?.let(transform))
    }

    fun saveForm() {
        val current = _state.value
        val form = current.form ?: return
        val amountMinor = Money.parse(form.amount, current.currency.exponent)

        if (amountMinor == null || amountMinor <= 0L) {
            editForm { it.copy(fieldErrors = mapOf("amount_minor" to "Enter an amount.")) }

            return
        }

        viewModelScope.launch {
            editForm { it.copy(saving = true, fieldErrors = emptyMap()) }

            val request = RecurringRuleRequest(
                name = form.name.trim(),
                type = form.type.wire,
                accountId = form.accountId,
                transferAccountId = form.transferAccountId.takeIf { form.isTransfer },
                categoryId = form.categoryId.takeIf { !form.isTransfer },
                amountMinor = amountMinor,
                merchant = form.merchant.trim().takeIf { it.isNotBlank() },
                frequency = form.frequency,
                startDate = Dates.wire(form.startDate),
                autoPost = form.autoPost,
            )

            val outcome = when (val id = form.id) {
                null -> recurring.create(request)
                else -> recurring.update(id, request)
            }

            when (outcome) {
                is Outcome.Ok -> {
                    _state.update { it.copy(form = null, message = "${outcome.value.name} saved.") }
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

    fun setActive(rule: RecurringRule, active: Boolean) {
        viewModelScope.launch {
            when (recurring.update(rule.id, RecurringRuleRequest(isActive = active))) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(message = if (active) "${rule.name} resumed." else "${rule.name} paused.")
                    }

                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = "That could not be changed.") }
            }
        }
    }

    fun runNow(rule: RecurringRule) {
        viewModelScope.launch {
            when (val outcome = recurring.run(rule.id)) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = outcome.value) }
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun delete(rule: RecurringRule) {
        viewModelScope.launch {
            when (val outcome = recurring.delete(rule.id)) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = "${rule.name} removed.") }
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
