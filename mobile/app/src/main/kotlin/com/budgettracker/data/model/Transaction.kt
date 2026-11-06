package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    @SerialName("income") Income,
    @SerialName("expense") Expense,
    @SerialName("transfer") Transfer,
    ;

    val wire: String
        get() = when (this) {
            Income -> "income"
            Expense -> "expense"
            Transfer -> "transfer"
        }

    val label: String
        get() = when (this) {
            Income -> "Income"
            Expense -> "Expense"
            Transfer -> "Transfer"
        }
}

@Serializable
enum class Direction {
    @SerialName("inflow") Inflow,
    @SerialName("outflow") Outflow,
}

@Serializable
data class TransactionSplit(
    val id: Long = 0,
    @SerialName("category_id") val categoryId: Long? = null,
    val category: Category? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val formatted: String = "",
    val note: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class Transaction(
    val id: Long,
    val type: TransactionType = TransactionType.Expense,
    val direction: Direction = Direction.Outflow,
    @SerialName("is_transfer") val isTransfer: Boolean = false,
    @SerialName("is_savings_allocation") val isSavingsAllocation: Boolean = false,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("signed_amount_minor") val signedAmountMinor: Long = 0,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("formatted_amount") val formattedAmount: String = "",
    @SerialName("occurred_on") val occurredOn: String = "",
    @SerialName("occurred_at") val occurredAt: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    @SerialName("account_id") val accountId: Long = 0,
    val account: AccountSummary? = null,
    @SerialName("transfer_account_id") val transferAccountId: Long? = null,
    @SerialName("transfer_account") val transferAccount: AccountSummary? = null,
    @SerialName("transfer_group") val transferGroup: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    val category: Category? = null,
    val tags: List<Tag> = emptyList(),
    @SerialName("has_splits") val hasSplits: Boolean = false,
    val splits: List<TransactionSplit> = emptyList(),
    @SerialName("receipt_id") val receiptId: Long? = null,
    @SerialName("bill_id") val billId: Long? = null,
    @SerialName("savings_goal_id") val savingsGoalId: Long? = null,
    @SerialName("recurring_rule_id") val recurringRuleId: Long? = null,
    @SerialName("created_via") val createdVia: String = "manual",
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** What a row shows as its heading when no merchant was recorded. */
    val title: String
        get() = merchant?.takeIf { it.isNotBlank() }
            ?: category?.name
            ?: when {
                isTransfer -> "Transfer"
                type == TransactionType.Income -> "Income"
                else -> "Expense"
            }

    val isInflow: Boolean get() = direction == Direction.Inflow
}

@Serializable
data class TransactionPage(
    val data: List<Transaction> = emptyList(),
    val meta: TransactionPageMeta = TransactionPageMeta(),
)

@Serializable
data class TransactionPageMeta(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
    val total: Int = 0,
    val totals: TransactionTotals = TransactionTotals(),
) {
    val hasMore: Boolean get() = currentPage < lastPage
}

/** Totals for the whole filtered set, not just the page on screen. */
@Serializable
data class TransactionTotals(
    @SerialName("income_minor") val incomeMinor: Long = 0,
    @SerialName("expense_minor") val expenseMinor: Long = 0,
    @SerialName("net_minor") val netMinor: Long = 0,
    val formatted: TransactionTotalsFormatted = TransactionTotalsFormatted(),
)

@Serializable
data class TransactionTotalsFormatted(
    val income: String = "",
    val expense: String = "",
    val net: String = "",
)

/** The transfer endpoint answers with both legs; the outflow is the subject. */
@Serializable
data class TransferResponse(
    val data: Transaction,
    val legs: TransferLegs? = null,
)

@Serializable
data class TransferLegs(
    val outflow: Transaction? = null,
    val inflow: Transaction? = null,
)
