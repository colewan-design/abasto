package com.budgettracker.data.repository

import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.AccountDeletion
import com.budgettracker.data.model.AccountList
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.AccountRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accounts and the balances derived from them. The list endpoint returns both
 * in one response, which is why callers get [AccountList] rather than a plain
 * list — the net-worth header would otherwise need a second request.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val api: BudgetApi,
    private val events: LedgerEvents,
) {

    suspend fun list(includeArchived: Boolean = false): Outcome<AccountList> =
        apiCall { api.accounts(includeArchived = includeArchived.takeIf { it }) }

    suspend fun find(id: Long): Outcome<Account> =
        apiCall { api.account(id).data }

    suspend fun create(request: AccountRequest): Outcome<Account> =
        apiCall { api.createAccount(request).data }
            .also { if (it is Outcome.Ok) events.signal() }

    suspend fun update(id: Long, request: AccountRequest): Outcome<Account> =
        apiCall { api.updateAccount(id, request).data }
            .also { if (it is Outcome.Ok) events.signal() }

    suspend fun archive(id: Long): Outcome<Account> =
        update(id, AccountRequest(isArchived = true))

    suspend fun restore(id: Long): Outcome<Account> =
        update(id, AccountRequest(isArchived = false))

    suspend fun delete(id: Long): Outcome<AccountDeletion> =
        apiCall { api.deleteAccount(id) }
            .also { if (it is Outcome.Ok) events.signal() }

    /** Rebuilds balances from the ledger, for when they look wrong. */
    suspend fun recalculate(): Outcome<String> =
        apiCall { api.recalculateBalances().message ?: "Balances rebuilt." }
            .also { if (it is Outcome.Ok) events.signal() }
}
