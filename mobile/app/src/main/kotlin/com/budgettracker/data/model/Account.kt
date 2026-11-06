package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    @SerialName("cash") Cash,
    @SerialName("bank") Bank,
    @SerialName("ewallet") Ewallet,
    @SerialName("credit_card") CreditCard,
    @SerialName("savings") Savings,
    ;

    val wire: String
        get() = when (this) {
            Cash -> "cash"
            Bank -> "bank"
            Ewallet -> "ewallet"
            CreditCard -> "credit_card"
            Savings -> "savings"
        }

    /** Matches `AccountType::label()` on the server, for forms built offline. */
    val label: String
        get() = when (this) {
            Cash -> "Cash"
            Bank -> "Bank account"
            Ewallet -> "E-wallet"
            CreditCard -> "Credit card"
            Savings -> "Savings"
        }
}

@Serializable
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType = AccountType.Cash,
    @SerialName("type_label") val typeLabel: String = "",
    @SerialName("is_liability") val isLiability: Boolean = false,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long = 0,
    @SerialName("current_balance_minor") val currentBalanceMinor: Long = 0,
    @SerialName("credit_limit_minor") val creditLimitMinor: Long? = null,
    @SerialName("available_credit_minor") val availableCreditMinor: Long? = null,
    val institution: String? = null,
    val color: String? = null,
    val icon: String? = null,
    @SerialName("include_in_total") val includeInTotal: Boolean = true,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("transactions_count") val transactionsCount: Int? = null,
    val formatted: AccountFormatted = AccountFormatted(),
)

@Serializable
data class AccountFormatted(
    val balance: String = "",
    @SerialName("opening_balance") val openingBalance: String = "",
    @SerialName("available_credit") val availableCredit: String? = null,
)

/** The slim account a transaction carries, enough to label a row. */
@Serializable
data class AccountSummary(
    val id: Long,
    val name: String,
    val type: AccountType = AccountType.Cash,
    @SerialName("type_label") val typeLabel: String = "",
    @SerialName("is_liability") val isLiability: Boolean = false,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    val color: String? = null,
    val icon: String? = null,
)

/** The `meta` block on the accounts list: net worth and per-account balances. */
@Serializable
data class Balances(
    @SerialName("available_minor") val availableMinor: Long = 0,
    @SerialName("owed_minor") val owedMinor: Long = 0,
    @SerialName("net_worth_minor") val netWorthMinor: Long = 0,
    val formatted: BalancesFormatted = BalancesFormatted(),
    val accounts: List<AccountBalance> = emptyList(),
)

@Serializable
data class BalancesFormatted(
    val available: String = "",
    val owed: String = "",
    @SerialName("net_worth") val netWorth: String = "",
)

@Serializable
data class AccountBalance(
    val id: Long,
    val name: String,
    val type: AccountType = AccountType.Cash,
    @SerialName("balance_minor") val balanceMinor: Long = 0,
    val formatted: String = "",
    @SerialName("is_liability") val isLiability: Boolean = false,
)

@Serializable
data class AccountList(
    val data: List<Account> = emptyList(),
    val meta: Balances = Balances(),
)

/**
 * Deleting an account that has history archives it instead. `archived` says
 * which of the two happened so the confirmation can tell the truth.
 */
@Serializable
data class AccountDeletion(
    val message: String? = null,
    val archived: Boolean = false,
    val account: Account? = null,
)
