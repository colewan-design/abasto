package com.budgettracker.data.repository

import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.core.network.discard
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Bill
import com.budgettracker.data.model.BillPaymentResponse
import com.budgettracker.data.model.BudgetSuggestion
import com.budgettracker.data.model.BudgetView
import com.budgettracker.data.model.ContributionResponse
import com.budgettracker.data.model.RecurringRule
import com.budgettracker.data.model.SavingsGoal
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.BillRequest
import com.budgettracker.data.remote.request.ContributionRequest
import com.budgettracker.data.remote.request.PayBillRequest
import com.budgettracker.data.remote.request.RecurringRuleRequest
import com.budgettracker.data.remote.request.SavingsGoalRequest
import com.budgettracker.data.remote.request.UpdateBudgetRequest
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monthly limits. A month with nothing set still answers — with suggestions
 * built from past spending — so the screen can offer to create a budget.
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val api: BudgetApi,
) {

    suspend fun forMonth(month: YearMonth): Outcome<BudgetView> =
        apiCall { api.budget(Dates.wireMonth(month)) }

    suspend fun save(month: YearMonth, request: UpdateBudgetRequest): Outcome<BudgetView> =
        apiCall { api.updateBudget(request.copy(month = Dates.wireMonth(month))) }

    suspend fun suggestions(month: YearMonth): Outcome<List<BudgetSuggestion>> =
        apiCall { api.budgetSuggestions(Dates.wireMonth(month)).data }
}

@Singleton
class BillRepository @Inject constructor(
    private val api: BudgetApi,
    private val events: LedgerEvents,
) {

    suspend fun list(includeInactive: Boolean = false): Outcome<List<Bill>> =
        apiCall { api.bills(includeInactive.takeIf { it }).data }

    suspend fun upcoming(days: Int = 30): Outcome<List<Bill>> =
        apiCall { api.upcomingBills(days).data }

    suspend fun create(request: BillRequest): Outcome<Bill> =
        apiCall { api.createBill(request).data }

    suspend fun update(id: Long, request: BillRequest): Outcome<Bill> =
        apiCall { api.updateBill(id, request).data }

    suspend fun delete(id: Long): Outcome<Unit> =
        apiCall { api.deleteBill(id) }.discard()

    /**
     * Marking a bill paid records the expense too, unless the caller says the
     * payment was already entered by hand.
     */
    suspend fun pay(id: Long, request: PayBillRequest): Outcome<BillPaymentResponse> =
        apiCall { api.payBill(id, request) }
            .also { if (it is Outcome.Ok) events.signal() }

    suspend fun skip(id: Long): Outcome<BillPaymentResponse> =
        apiCall { api.skipBill(id) }

    suspend fun undoPayment(id: Long, paymentId: Long): Outcome<Unit> =
        apiCall { api.undoBillPayment(id, paymentId) }
            .discard()
            .also { if (it is Outcome.Ok) events.signal() }
}

@Singleton
class SavingsRepository @Inject constructor(
    private val api: BudgetApi,
    private val events: LedgerEvents,
) {

    suspend fun list(includeArchived: Boolean = false): Outcome<List<SavingsGoal>> =
        apiCall { api.savingsGoals(includeArchived.takeIf { it }).data }

    suspend fun find(id: Long): Outcome<SavingsGoal> =
        apiCall { api.savingsGoal(id).data }

    suspend fun create(request: SavingsGoalRequest): Outcome<SavingsGoal> =
        apiCall { api.createSavingsGoal(request).data }

    suspend fun update(id: Long, request: SavingsGoalRequest): Outcome<SavingsGoal> =
        apiCall { api.updateSavingsGoal(id, request).data }

    suspend fun delete(id: Long): Outcome<Unit> =
        apiCall { api.deleteSavingsGoal(id) }.discard()

    suspend fun contribute(id: Long, request: ContributionRequest): Outcome<ContributionResponse> =
        apiCall { api.contribute(id, request) }
            .also { if (it is Outcome.Ok) events.signal() }

    suspend fun removeContribution(id: Long, contributionId: Long): Outcome<Unit> =
        apiCall { api.removeContribution(id, contributionId) }
            .discard()
            .also { if (it is Outcome.Ok) events.signal() }
}

@Singleton
class RecurringRepository @Inject constructor(
    private val api: BudgetApi,
    private val events: LedgerEvents,
) {

    suspend fun list(includeInactive: Boolean = true): Outcome<List<RecurringRule>> =
        apiCall { api.recurringRules(includeInactive.takeIf { it }).data }

    suspend fun create(request: RecurringRuleRequest): Outcome<RecurringRule> =
        apiCall { api.createRecurringRule(request).data }

    suspend fun update(id: Long, request: RecurringRuleRequest): Outcome<RecurringRule> =
        apiCall { api.updateRecurringRule(id, request).data }

    suspend fun delete(id: Long): Outcome<Unit> =
        apiCall { api.deleteRecurringRule(id) }.discard()

    /** Posts the rule's due occurrences now instead of waiting for the night. */
    suspend fun run(id: Long): Outcome<String> =
        apiCall { api.runRecurringRule(id).message ?: "Rule run." }
            .also { if (it is Outcome.Ok) events.signal() }
}
