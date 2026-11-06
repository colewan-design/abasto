package com.budgettracker.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category
import com.budgettracker.data.model.CategorySuggestion
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.Tag
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionType
import com.budgettracker.data.remote.request.TransactionRequest
import com.budgettracker.data.remote.request.TransferRequest
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ComposerUiState(
    val editingId: Long? = null,
    val type: TransactionType = TransactionType.Expense,
    val amount: String = "",
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val date: LocalDate = Dates.today(),
    val merchant: String = "",
    val note: String = "",
    val tagIds: Set<Long> = emptySet(),
    val saving: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val suggestion: CategorySuggestion? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val currency: Currency = Currency.Default,
) {
    val isEditing: Boolean get() = editingId != null
    val isTransfer: Boolean get() = type == TransactionType.Transfer

    /** Accounts cannot be re-pointed on an existing transfer; the pair is fixed. */
    val accountsLocked: Boolean get() = isEditing && isTransfer

    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val toAccount: Account? get() = accounts.firstOrNull { it.id == toAccountId }
    val category: Category? get() = categories.firstOrNull { it.id == categoryId }

    val amountMinor: Long? get() = Money.parse(amount, currency.exponent)?.takeIf { it > 0L }

    val canSave: Boolean
        get() = !saving &&
            amountMinor != null &&
            accountId != null &&
            (!isTransfer || (toAccountId != null && toAccountId != accountId))

    val title: String
        get() = when {
            isEditing -> "Edit ${type.label.lowercase()}"
            else -> "New ${type.label.lowercase()}"
        }
}

/**
 * Entering and editing a transaction.
 *
 * Income and expense go through the transactions endpoint; a transfer goes
 * through its own, because it is written as two linked rows and must never be
 * recorded as spending.
 *
 * While a merchant is being typed the server is asked which category it
 * usually belongs to. The answer is offered, never applied — and correcting it
 * is what teaches the suggester, so a wrong guess is still useful.
 */
@HiltViewModel
class ComposerViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val reference: ReferenceRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposerUiState())
    val state: StateFlow<ComposerUiState> = _state.asStateFlow()

    private var suggesting: Job? = null

    init {
        viewModelScope.launch {
            reference.ensureLoaded()
            fillReference()
        }
    }

    /** Opens the composer fresh, for the type the caller wants. */
    fun start(type: TransactionType = TransactionType.Expense) {
        _state.value = ComposerUiState(
            type = type,
            currency = auth.currency,
            accountId = reference.defaultAccount()?.id,
        )

        fillReference()
    }

    /** Opens the composer over an existing row. */
    fun edit(transaction: Transaction) {
        _state.value = ComposerUiState(
            editingId = transaction.id,
            type = transaction.type,
            amount = Money.majorInput(transaction.amountMinor, auth.currency.exponent),
            accountId = transaction.accountId,
            toAccountId = transaction.transferAccountId,
            categoryId = transaction.categoryId,
            date = Dates.parse(transaction.occurredOn) ?: Dates.today(),
            merchant = transaction.merchant.orEmpty(),
            note = transaction.note.orEmpty(),
            tagIds = transaction.tags.map { it.id }.toSet(),
            currency = auth.currency,
        )

        fillReference()
    }

    fun onTypeChange(type: TransactionType) = _state.update { current ->
        current.copy(
            type = type,
            // An expense category cannot hold income, so the choice is dropped
            // rather than silently sent and rejected.
            categoryId = null,
            suggestion = null,
            categories = categoriesFor(type),
        )
    }

    fun onAmountChange(value: String) = _state.update {
        it.copy(amount = value, fieldErrors = it.fieldErrors - "amount_minor")
    }

    fun onAccountChange(id: Long) = _state.update { it.copy(accountId = id) }

    fun onToAccountChange(id: Long) = _state.update { it.copy(toAccountId = id) }

    fun onCategoryChange(id: Long?) = _state.update { it.copy(categoryId = id, suggestion = null) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date) }

    fun onNoteChange(value: String) = _state.update { it.copy(note = value) }

    fun toggleTag(id: Long) = _state.update { current ->
        current.copy(
            tagIds = if (id in current.tagIds) current.tagIds - id else current.tagIds + id,
        )
    }

    fun onMerchantChange(value: String) {
        _state.update { it.copy(merchant = value) }

        suggesting?.cancel()

        if (_state.value.isTransfer || value.trim().length < 3) return

        suggesting = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)

            val type = if (_state.value.type == TransactionType.Income) {
                CategoryType.Income
            } else {
                CategoryType.Expense
            }

            when (val outcome = reference.suggestCategory(merchant = value, type = type)) {
                is Outcome.Ok -> _state.update { current ->
                    // Only offer it while the person has not chosen for
                    // themselves, and only when the server is reasonably sure.
                    val worth = outcome.value.category != null && outcome.value.confidence >= 0.5

                    current.copy(suggestion = outcome.value.takeIf { worth && current.categoryId == null })
                }

                is Outcome.Err -> Unit
            }
        }
    }

    fun acceptSuggestion() = _state.update { current ->
        current.copy(categoryId = current.suggestion?.category?.id, suggestion = null)
    }

    fun dismissSuggestion() = _state.update { it.copy(suggestion = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val amountMinor = current.amountMinor ?: return
        val accountId = current.accountId ?: return

        // A transfer needs both sides before anything is sent; the button is
        // disabled without them, and this makes that a fact rather than a
        // convention the next caller has to know about.
        if (current.isTransfer && !current.isEditing && current.toAccountId == null) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, fieldErrors = emptyMap()) }

            val outcome: Outcome<Transaction> = when {
                current.isTransfer && !current.isEditing -> transactions.transfer(
                    TransferRequest(
                        fromAccountId = accountId,
                        toAccountId = current.toAccountId!!,
                        amountMinor = amountMinor,
                        occurredOn = Dates.wire(current.date),
                        note = current.note.trim().takeIf { it.isNotBlank() },
                    ),
                )

                current.isEditing -> transactions.update(
                    id = current.editingId!!,
                    request = TransactionRequest(
                        // A transfer leg keeps its account; the pair is synced
                        // server-side from the amount and date alone.
                        accountId = accountId.takeIf { !current.accountsLocked },
                        categoryId = current.categoryId,
                        amountMinor = amountMinor,
                        occurredOn = Dates.wire(current.date),
                        merchant = current.merchant.trim().takeIf { it.isNotBlank() },
                        note = current.note.trim().takeIf { it.isNotBlank() },
                        tags = current.tagIds.toList(),
                    ),
                )

                else -> transactions.create(
                    TransactionRequest(
                        type = current.type.wire,
                        accountId = accountId,
                        categoryId = current.categoryId,
                        amountMinor = amountMinor,
                        occurredOn = Dates.wire(current.date),
                        merchant = current.merchant.trim().takeIf { it.isNotBlank() },
                        note = current.note.trim().takeIf { it.isNotBlank() },
                        tags = current.tagIds.toList().takeIf { it.isNotEmpty() },
                    ),
                )
            }

            when (outcome) {
                is Outcome.Ok -> {
                    _state.update { it.copy(saving = false) }
                    onSaved()
                }

                is Outcome.Err -> showError(outcome.error)
            }
        }
    }

    private fun showError(error: ApiException) = _state.update { current ->
        current.copy(
            saving = false,
            error = if (error.isValidation) "Check the highlighted fields." else error.message,
            fieldErrors = error.errors.mapValues { (_, messages) -> messages.first() },
        )
    }

    private fun fillReference() = _state.update { current ->
        current.copy(
            accounts = reference.accounts.value.filterNot { it.isArchived },
            categories = categoriesFor(current.type),
            tags = reference.tags.value,
            currency = auth.currency,
            accountId = current.accountId ?: reference.defaultAccount()?.id,
        )
    }

    private fun categoriesFor(type: TransactionType): List<Category> = when (type) {
        TransactionType.Income -> reference.incomeCategories()
        TransactionType.Expense -> reference.expenseCategories()
        TransactionType.Transfer -> emptyList()
    }

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 450L
    }
}
