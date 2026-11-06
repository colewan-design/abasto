package com.budgettracker.feature.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category
import com.budgettracker.data.model.Receipt
import com.budgettracker.data.model.ReceiptMeta
import com.budgettracker.data.remote.request.ConfirmReceiptRequest
import com.budgettracker.data.remote.request.ReceiptItemAssignment
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.ReceiptRepository
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReceiptReviewUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val reprocessing: Boolean = false,
    val error: ApiException? = null,
    val message: String? = null,
    val receipt: Receipt? = null,
    val meta: ReceiptMeta = ReceiptMeta(),
    val merchant: String = "",
    val date: LocalDate = Dates.today(),
    val total: String = "",
    val note: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val splitByItem: Boolean = true,
    val itemCategories: Map<Long, Long?> = emptyMap(),
    val acknowledgeDuplicate: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currency: Currency = Currency.Default,
    val confirmedTransactionId: Long? = null,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val category: Category? get() = categories.firstOrNull { it.id == categoryId }

    val totalMinor: Long? get() = Money.parse(total, currency.exponent)?.takeIf { it > 0L }

    /** Flagged by the parser as not confident enough to accept unchecked. */
    fun flagged(field: String): Boolean = receipt?.flagged(field) == true

    val duplicate get() = receipt?.duplicate

    val blockedByDuplicate: Boolean
        get() = duplicate?.isDuplicate == true && !acknowledgeDuplicate

    val canConfirm: Boolean
        get() = !saving && accountId != null && totalMinor != null && !blockedByDuplicate

    /** How many distinct categories the lines are assigned to. */
    val distinctItemCategories: Int
        get() = itemCategories.values.filterNotNull().distinct().size
}

/**
 * Checking a scanned receipt before it becomes a transaction.
 *
 * Nothing is recorded until confirm: the draft is the server's best reading,
 * and every field it was unsure about is flagged for a human to look at. When
 * the lines are confirmed with their categories, the server allocates the
 * total across them — including tax and discounts — so the split always sums
 * to what was actually paid.
 *
 * Correcting a category here is also what teaches the suggester, which is why
 * the per-line assignment is worth the screen space.
 */
@HiltViewModel
class ReceiptReviewViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    private val reference: ReferenceRepository,
    private val auth: AuthRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val receiptId: Long = savedState.get<String>(Routes.ARG_RECEIPT_ID)?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ReceiptReviewUiState(currency = auth.currency))
    val state: StateFlow<ReceiptReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reference.ensureLoaded()

            _state.update {
                it.copy(
                    accounts = reference.accounts.value.filterNot { account -> account.isArchived },
                    categories = reference.expenseCategories(),
                )
            }
        }

        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            when (val outcome = receipts.detail(receiptId)) {
                is Outcome.Ok -> fill(outcome.value.data, outcome.value.meta)
                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    private fun fill(receipt: Receipt, meta: ReceiptMeta) = _state.update { current ->
        val exponent = current.currency.exponent

        current.copy(
            loading = false,
            receipt = receipt,
            meta = meta,
            merchant = receipt.merchant.orEmpty(),
            date = Dates.parse(receipt.receiptDate) ?: Dates.today(),
            total = receipt.totalMinor?.let { Money.majorInput(it, exponent) }.orEmpty(),
            accountId = current.accountId ?: reference.defaultAccount()?.id,
            // The server's merchant-level suggestion, offered as the fallback
            // category for the receipt as a whole.
            categoryId = current.categoryId ?: meta.suggestedCategory?.category?.id,
            itemCategories = receipt.items.associate { item ->
                item.id to (item.categoryId ?: item.suggestedCategoryId)
            },
            splitByItem = receipt.items.isNotEmpty(),
        )
    }

    fun onMerchantChange(value: String) = _state.update { it.copy(merchant = value) }

    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onTotalChange(value: String) = _state.update {
        it.copy(total = value, fieldErrors = it.fieldErrors - "total_minor")
    }

    fun onNoteChange(value: String) = _state.update { it.copy(note = value) }

    fun onAccountChange(id: Long) = _state.update { it.copy(accountId = id) }

    fun onCategoryChange(id: Long?) = _state.update { it.copy(categoryId = id) }

    fun onItemCategoryChange(itemId: Long, categoryId: Long?) = _state.update { current ->
        current.copy(itemCategories = current.itemCategories + (itemId to categoryId))
    }

    fun toggleSplitByItem() = _state.update { it.copy(splitByItem = !it.splitByItem) }

    fun acknowledgeDuplicate() = _state.update { it.copy(acknowledgeDuplicate = true) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Re-runs extraction, for a receipt that failed or read badly. */
    fun reprocess() {
        viewModelScope.launch {
            _state.update { it.copy(reprocessing = true) }

            when (val outcome = receipts.reprocess(receiptId)) {
                is Outcome.Ok -> {
                    _state.update { it.copy(reprocessing = false, message = "Read again.") }
                    load()
                }

                is Outcome.Err -> _state.update {
                    it.copy(reprocessing = false, message = outcome.error.message)
                }
            }
        }
    }

    fun confirm() {
        val current = _state.value
        val accountId = current.accountId ?: return
        val totalMinor = current.totalMinor ?: return

        viewModelScope.launch {
            _state.update { it.copy(saving = true, fieldErrors = emptyMap()) }

            val request = ConfirmReceiptRequest(
                accountId = accountId,
                categoryId = current.categoryId,
                merchant = current.merchant.trim().takeIf { it.isNotBlank() },
                receiptDate = Dates.wire(current.date),
                totalMinor = totalMinor,
                note = current.note.trim().takeIf { it.isNotBlank() },
                acknowledgeDuplicate = current.acknowledgeDuplicate.takeIf { it },
                items = current.itemCategories
                    .map { (id, categoryId) -> ReceiptItemAssignment(id = id, categoryId = categoryId) }
                    .takeIf { it.isNotEmpty() },
                // Left unset, the server allocates the total across the lines
                // by their categories; an empty list says "one category".
                splits = if (current.splitByItem) null else emptyList(),
            )

            when (val outcome = receipts.confirm(receiptId, request)) {
                is Outcome.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        confirmedTransactionId = outcome.value.transaction.id,
                        message = outcome.value.message ?: "Recorded.",
                    )
                }

                is Outcome.Err -> _state.update { current2 ->
                    current2.copy(
                        saving = false,
                        message = if (outcome.error.isValidation) {
                            "Check the highlighted fields."
                        } else {
                            outcome.error.message
                        },
                        fieldErrors = outcome.error.errors.mapValues { (_, m) -> m.first() },
                    )
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (val outcome = receipts.delete(receiptId)) {
                is Outcome.Ok -> onDeleted()
                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }
}
