package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SavingsFormatted(
    val target: String = "",
    val saved: String = "",
    val remaining: String = "",
    @SerialName("suggested_monthly") val suggestedMonthly: String? = null,
)

@Serializable
data class SavingsContribution(
    val id: Long,
    @SerialName("savings_goal_id") val savingsGoalId: Long = 0,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("formatted_amount") val formattedAmount: String = "",
    @SerialName("contributed_on") val contributedOn: String = "",
    val note: String? = null,
    @SerialName("transaction_id") val transactionId: Long? = null,
)

@Serializable
data class SavingsGoal(
    val id: Long,
    val name: String,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("target_amount_minor") val targetAmountMinor: Long = 0,
    @SerialName("current_amount_minor") val currentAmountMinor: Long = 0,
    @SerialName("remaining_minor") val remainingMinor: Long = 0,
    val progress: Double = 0.0,
    @SerialName("is_achieved") val isAchieved: Boolean = false,
    @SerialName("achieved_at") val achievedAt: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("suggested_monthly_minor") val suggestedMonthlyMinor: Long? = null,
    val formatted: SavingsFormatted = SavingsFormatted(),
    val color: String? = null,
    val icon: String? = null,
    val note: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("account_id") val accountId: Long? = null,
    val account: Account? = null,
    val contributions: List<SavingsContribution> = emptyList(),
    @SerialName("contributions_count") val contributionsCount: Int? = null,
)

@Serializable
data class ContributionResponse(
    val message: String? = null,
    val contribution: SavingsContribution? = null,
    val goal: SavingsGoal? = null,
)

@Serializable
data class RecurringRule(
    val id: Long,
    val name: String,
    val type: TransactionType = TransactionType.Expense,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("formatted_amount") val formattedAmount: String = "",
    val merchant: String? = null,
    val note: String? = null,
    val frequency: String = "monthly",
    @SerialName("frequency_label") val frequencyLabel: String = "",
    val interval: Int = 1,
    @SerialName("by_month_day") val byMonthDay: Int? = null,
    @SerialName("by_weekday") val byWeekday: Int? = null,
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("occurrence_limit") val occurrenceLimit: Int? = null,
    @SerialName("occurrences_created") val occurrencesCreated: Int = 0,
    @SerialName("next_run_on") val nextRunOn: String? = null,
    @SerialName("last_run_on") val lastRunOn: String? = null,
    @SerialName("upcoming_dates") val upcomingDates: List<String> = emptyList(),
    @SerialName("auto_post") val autoPost: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("account_id") val accountId: Long = 0,
    val account: Account? = null,
    @SerialName("transfer_account_id") val transferAccountId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    val category: Category? = null,
)
