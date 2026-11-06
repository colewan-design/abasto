package com.budgettracker.data.repository

import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.core.network.discard
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionFilters
import com.budgettracker.data.model.TransactionPage
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.TransactionRequest
import com.budgettracker.data.remote.request.TransferRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ledger. Reads are paged; writes return the saved row so a list can
 * update in place without a refetch.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val api: BudgetApi,
    private val events: LedgerEvents,
) {

    suspend fun list(
        filters: TransactionFilters,
        page: Int = 1,
        perPage: Int = PAGE_SIZE,
    ): Outcome<TransactionPage> = apiCall {
        api.transactions(
            query = filters.query.trim().takeIf { it.isNotBlank() },
            type = filters.type,
            accountIds = filters.accountIds.takeIf { it.isNotEmpty() },
            categoryIds = filters.categoryIds.takeIf { it.isNotEmpty() },
            tagIds = filters.tagIds.takeIf { it.isNotEmpty() },
            from = filters.from?.let(Dates::wire),
            to = filters.to?.let(Dates::wire),
            minAmountMinor = filters.minAmountMinor,
            maxAmountMinor = filters.maxAmountMinor,
            hasReceipt = filters.hasReceipt,
            uncategorized = filters.uncategorized,
            savingsGoalId = null,
            billId = null,
            // A transfer is two rows; the list shows the outflow leg only.
            collapseTransfers = true,
            sort = filters.sort,
            direction = filters.direction,
            perPage = perPage,
            page = page,
        )
    }

    suspend fun forAccount(accountId: Long, page: Int = 1): Outcome<TransactionPage> =
        apiCall { api.accountTransactions(accountId, perPage = PAGE_SIZE, page = page) }

    suspend fun find(id: Long): Outcome<Transaction> =
        apiCall { api.transaction(id).data }

    suspend fun create(request: TransactionRequest): Outcome<Transaction> =
        apiCall { api.createTransaction(request).data }.announce()

    suspend fun update(id: Long, request: TransactionRequest): Outcome<Transaction> =
        apiCall { api.updateTransaction(id, request).data }.announce()

    suspend fun delete(id: Long): Outcome<Unit> =
        apiCall { api.deleteTransaction(id) }.discard().announce()

    /** Both legs are written server-side; the outflow comes back as the row. */
    suspend fun transfer(request: TransferRequest): Outcome<Transaction> =
        apiCall { api.createTransfer(request).data }.announce()

    /** Tells the rest of the app a balance or a total has moved. */
    private fun <T> Outcome<T>.announce(): Outcome<T> = also {
        if (it is Outcome.Ok) events.signal()
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
