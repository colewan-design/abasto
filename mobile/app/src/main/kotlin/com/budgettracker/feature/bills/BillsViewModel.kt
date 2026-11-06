package com.budgettracker.feature.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Bill
import com.budgettracker.data.model.Category
import com.budgettracker.data.remote.request.BillRequest
import com.budgettracker.data.remote.request.PayBillRequest
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.BillRepository
import com.budgettracker.data.repository.ReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BillForm(
    val id: Long? = null,
    val name: String = "",
    val payee: String = "",
    val amount: String = "",
    val dueDate: LocalDate = Dates.today(),
    val recurrence: String = "monthly",
    val reminderDays: Int = 3,
    val autoPay: Boolean = false,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank() && amount.isNotBlank() && !saving
}

/** Marking a bill paid, which optionally writes the expense too. */
data class PayForm(
    val bill: Bill,
    val amount: String,
    val accountId: Long?,
    val paidOn: LocalDate = Dates.today(),
    val recordExpense: Boolean = true,
    val saving: Boolean = false,
)

data class BillsUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val message: String? = null,
    val bills: List<Bill> = emptyList(),
    val showInactive: Boolean = false,
    val currency: Currency = Currency.Default,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val form: BillForm? = null,
    val paying: PayForm? = null,
) {
    val due: List<Bill> get() = bills.filter { it.isActive && it.daysUntilDue <= DUE_SOON_DAYS }
    val later: List<Bill> get() = bills.filter { it.isActive && it.daysUntilDue > DUE_SOON_DAYS }
    val inactive: List<Bill> get() = bills.filterNot { it.isActive }
    val overdueCount: Int get() = bills.count { it.isActive && it.isOverdue }

    private companion object {
        const val DUE_SOON_DAYS = 14
    }
}

/**
 * Bills and their due dates.
 *
 * Paying a bill normally records the expense as well, which is the point —
 * but it can be turned off for a payment that was already entered by hand, so
 * the same amount is never counted twice.
 */
@HiltViewModel
class BillsViewModel @Inject constructor(
    private val bills: BillRepository,
    private val reference: ReferenceRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BillsUiState(currency = auth.currency))
    val state: StateFlow<BillsUiState> = _state.asStateFlow()

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
            _state.update { it.copy(loading = it.bills.isEmpty(), error = null) }

            when (val outcome = bills.list(includeInactive = true)) {
                is Outcome.Ok -> _state.update { it.copy(loading = false, bills = outcome.value) }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    fun toggleInactive() = _state.update { it.copy(showInactive = !it.showInactive) }

    /* --------------------------------------------------------------- form */

    fun addBill() = _state.update { current ->
        current.copy(
            form = BillForm(
                accountId = reference.defaultAccount()?.id,
                dueDate = Dates.today().plusDays(7),
            ),
        )
    }

    fun editBill(bill: Bill) = _state.update { current ->
        current.copy(
            form = BillForm(
                id = bill.id,
                name = bill.name,
                payee = bill.payee.orEmpty(),
                amount = Money.majorInput(bill.amountMinor, current.currency.exponent),
                dueDate = Dates.parse(bill.dueDate) ?: Dates.today(),
                recurrence = bill.recurrence,
                reminderDays = bill.reminderDaysBefore,
                autoPay = bill.autoPay,
                accountId = bill.accountId,
                categoryId = bill.categoryId,
                note = bill.note.orEmpty(),
            ),
        )
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    fun editForm(transform: (BillForm) -> BillForm) = _state.update { current ->
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

            val request = BillRequest(
                name = form.name.trim(),
                payee = form.payee.trim().takeIf { it.isNotBlank() },
                amountMinor = amountMinor,
                dueDate = Dates.wire(form.dueDate),
                recurrence = form.recurrence,
                reminderDaysBefore = form.reminderDays,
                autoPay = form.autoPay,
                accountId = form.accountId,
                categoryId = form.categoryId,
                note = form.note.trim().takeIf { it.isNotBlank() },
            )

            val outcome = when (val id = form.id) {
                null -> bills.create(request)
                else -> bills.update(id, request)
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

    /* ------------------------------------------------------------- paying */

    fun startPaying(bill: Bill) = _state.update { current ->
        current.copy(
            paying = PayForm(
                bill = bill,
                amount = Money.majorInput(bill.amountMinor, current.currency.exponent),
                accountId = bill.accountId ?: reference.defaultAccount()?.id,
            ),
        )
    }

    fun editPayment(transform: (PayForm) -> PayForm) = _state.update { current ->
        current.copy(paying = current.paying?.let(transform))
    }

    fun cancelPaying() = _state.update { it.copy(paying = null) }

    fun confirmPayment() {
        val current = _state.value
        val form = current.paying ?: return

        viewModelScope.launch {
            editPayment { it.copy(saving = true) }

            val outcome = bills.pay(
                id = form.bill.id,
                request = PayBillRequest(
                    accountId = form.accountId,
                    amountMinor = Money.parse(form.amount, current.currency.exponent),
                    paidOn = Dates.wire(form.paidOn),
                    recordExpense = form.recordExpense,
                ),
            )

            when (outcome) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(
                            paying = null,
                            message = outcome.value.message ?: "${form.bill.name} marked paid.",
                        )
                    }

                    load()
                }

                is Outcome.Err -> {
                    editPayment { it.copy(saving = false) }
                    _state.update { it.copy(message = outcome.error.message) }
                }
            }
        }
    }

    fun skip(bill: Bill) {
        viewModelScope.launch {
            when (val outcome = bills.skip(bill.id)) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(message = outcome.value.message ?: "${bill.name} skipped.")
                    }

                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun delete(bill: Bill) {
        viewModelScope.launch {
            when (val outcome = bills.delete(bill.id)) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = "${bill.name} removed.") }
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
