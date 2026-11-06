package com.budgettracker.data.remote.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------ accounts */

@Serializable
data class AccountRequest(
    val name: String? = null,
    val type: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long? = null,
    @SerialName("credit_limit_minor") val creditLimitMinor: Long? = null,
    val institution: String? = null,
    @SerialName("number_last4") val numberLast4: String? = null,
    val color: String? = null,
    val icon: String? = null,
    @SerialName("include_in_total") val includeInTotal: Boolean? = null,
    @SerialName("is_archived") val isArchived: Boolean? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

/* ---------------------------------------------------------------- categories */

@Serializable
data class CategoryRequest(
    val name: String? = null,
    val type: String? = null,
    @SerialName("category_group_id") val categoryGroupId: Long? = null,
    @SerialName("new_group_name") val newGroupName: String? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    val color: String? = null,
    val icon: String? = null,
    val keywords: List<String>? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    // The category endpoints spell this one `archived`, unlike accounts.
    val archived: Boolean? = null,
)

@Serializable
data class CategoryGroupRequest(
    val name: String? = null,
    val type: String? = null,
    val color: String? = null,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

@Serializable
data class SuggestCategoryRequest(
    val merchant: String? = null,
    val description: String? = null,
    val type: String? = null,
)

@Serializable
data class TagRequest(
    val name: String,
    val color: String? = null,
)

/* -------------------------------------------------------------------- ledger */

@Serializable
data class SplitRequest(
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long,
    val note: String? = null,
)

/**
 * `type` only accepts income or expense — a transfer is two rows and goes
 * through [TransferRequest] instead, which is what keeps reports from counting
 * moved money as either.
 */
@Serializable
data class TransactionRequest(
    val type: String? = null,
    @SerialName("account_id") val accountId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    @SerialName("occurred_on") val occurredOn: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    @SerialName("receipt_id") val receiptId: Long? = null,
    @SerialName("bill_id") val billId: Long? = null,
    @SerialName("savings_goal_id") val savingsGoalId: Long? = null,
    val tags: List<Long>? = null,
    val splits: List<SplitRequest>? = null,
)

@Serializable
data class TransferRequest(
    @SerialName("from_account_id") val fromAccountId: Long,
    @SerialName("to_account_id") val toAccountId: Long,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("occurred_on") val occurredOn: String,
    val note: String? = null,
    @SerialName("savings_goal_id") val savingsGoalId: Long? = null,
)

/* ------------------------------------------------------------------ receipts */

@Serializable
data class ReceiptItemAssignment(
    val id: Long,
    @SerialName("category_id") val categoryId: Long? = null,
)

@Serializable
data class ConfirmReceiptRequest(
    @SerialName("account_id") val accountId: Long,
    @SerialName("category_id") val categoryId: Long? = null,
    val merchant: String? = null,
    @SerialName("receipt_date") val receiptDate: String? = null,
    @SerialName("total_minor") val totalMinor: Long? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val note: String? = null,
    @SerialName("acknowledge_duplicate") val acknowledgeDuplicate: Boolean? = null,
    val tags: List<Long>? = null,
    val items: List<ReceiptItemAssignment>? = null,
    val splits: List<SplitRequest>? = null,
)
