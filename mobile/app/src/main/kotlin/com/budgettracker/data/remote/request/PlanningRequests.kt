package com.budgettracker.data.remote.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* -------------------------------------------------------------------- budget */

@Serializable
data class BudgetLineRequest(
    @SerialName("category_id") val categoryId: Long,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("alert_threshold") val alertThreshold: Double? = null,
)

/**
 * `categories` replaces the month's limits wholesale, so a line left out is a
 * line removed. Omit the key entirely to change only the overall figure.
 */
@Serializable
data class UpdateBudgetRequest(
    val month: String? = null,
    @SerialName("overall_amount_minor") val overallAmountMinor: Long? = null,
    @SerialName("rollover_enabled") val rolloverEnabled: Boolean? = null,
    @SerialName("alert_threshold") val alertThreshold: Double? = null,
    val categories: List<BudgetLineRequest>? = null,
)

/* --------------------------------------------------------------------- bills */

@Serializable
data class BillRequest(
    val name: String? = null,
    val payee: String? = null,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val recurrence: String? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerialName("reminder_days_before") val reminderDaysBefore: Int? = null,
    @SerialName("auto_pay") val autoPay: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("account_id") val accountId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    val note: String? = null,
)

/**
 * `record_expense = false` marks a bill paid without writing an expense, for
 * when the payment was already entered by hand or imported.
 */
@Serializable
data class PayBillRequest(
    @SerialName("account_id") val accountId: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    @SerialName("paid_on") val paidOn: String? = null,
    val note: String? = null,
    @SerialName("record_expense") val recordExpense: Boolean? = null,
)

/* ------------------------------------------------------------ savings goals */

@Serializable
data class SavingsGoalRequest(
    val name: String? = null,
    @SerialName("target_amount_minor") val targetAmountMinor: Long? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("account_id") val accountId: Long? = null,
    val color: String? = null,
    val icon: String? = null,
    val note: String? = null,
    @SerialName("is_archived") val isArchived: Boolean? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

/**
 * `from_account_id` is what makes a contribution move real money; without it
 * the goal is credited on its own, for cash set aside outside the app.
 */
@Serializable
data class ContributionRequest(
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("contributed_on") val contributedOn: String,
    @SerialName("from_account_id") val fromAccountId: Long? = null,
    val note: String? = null,
)

/* --------------------------------------------------------- recurring rules */

@Serializable
data class RecurringRuleRequest(
    val name: String? = null,
    val type: String? = null,
    @SerialName("account_id") val accountId: Long? = null,
    @SerialName("transfer_account_id") val transferAccountId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    val frequency: String? = null,
    val interval: Int? = null,
    @SerialName("by_month_day") val byMonthDay: Int? = null,
    @SerialName("by_weekday") val byWeekday: Int? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("occurrence_limit") val occurrenceLimit: Int? = null,
    @SerialName("auto_post") val autoPost: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)
