package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BudgetStatus {
    @SerialName("ok") Ok,
    @SerialName("approaching") Approaching,
    @SerialName("exceeded") Exceeded,
    @SerialName("unset") Unset,
}

@Serializable
data class BudgetMoney(
    val limit: String? = null,
    val spent: String = "",
    val remaining: String? = null,
)

/** One category's limit and how much of it is gone. */
@Serializable
data class BudgetLine(
    val id: Long = 0,
    @SerialName("category_id") val categoryId: Long = 0,
    val category: Category? = null,
    @SerialName("limit_minor") val limitMinor: Long = 0,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("rollover_minor") val rolloverMinor: Long = 0,
    @SerialName("spent_minor") val spentMinor: Long = 0,
    @SerialName("remaining_minor") val remainingMinor: Long = 0,
    @SerialName("percent_used") val percentUsed: Double? = null,
    val status: BudgetStatus = BudgetStatus.Ok,
    val threshold: Double = 0.8,
    val formatted: BudgetMoney = BudgetMoney(),
) {
    val isOverspent: Boolean get() = remainingMinor < 0L
}

@Serializable
data class BudgetOverall(
    @SerialName("limit_minor") val limitMinor: Long? = null,
    @SerialName("spent_minor") val spentMinor: Long = 0,
    @SerialName("remaining_minor") val remainingMinor: Long? = null,
    @SerialName("percent_used") val percentUsed: Double? = null,
    val status: BudgetStatus = BudgetStatus.Unset,
    val formatted: BudgetMoney = BudgetMoney(),
)

@Serializable
data class BudgetCategoryLimit(
    val id: Long = 0,
    @SerialName("category_id") val categoryId: Long = 0,
    val category: Category? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("rollover_minor") val rolloverMinor: Long = 0,
    @SerialName("limit_minor") val limitMinor: Long = 0,
    @SerialName("alert_threshold") val alertThreshold: Double = 0.8,
)

@Serializable
data class Budget(
    val id: Long,
    val period: String = "",
    @SerialName("period_start") val periodStart: String = "",
    @SerialName("period_end") val periodEnd: String = "",
    val label: String = "",
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("overall_amount_minor") val overallAmountMinor: Long? = null,
    @SerialName("rollover_enabled") val rolloverEnabled: Boolean = false,
    @SerialName("alert_threshold") val alertThreshold: Double = 0.8,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val categories: List<BudgetCategoryLimit> = emptyList(),
)

@Serializable
data class BudgetSuggestion(
    @SerialName("category_id") val categoryId: Long = 0,
    val category: String = "",
    @SerialName("suggested_minor") val suggestedMinor: Long = 0,
    val formatted: String = "",
)

@Serializable
data class BudgetMeta(
    val month: String = "",
    val label: String = "",
    val overall: BudgetOverall? = null,
    val categories: List<BudgetLine> = emptyList(),
    @SerialName("unbudgeted_minor") val unbudgetedMinor: Long = 0,
    @SerialName("uncategorized_minor") val uncategorizedMinor: Long = 0,
    val suggestions: List<BudgetSuggestion> = emptyList(),
)

/**
 * A month with no budget set comes back with `data = null` and suggestions in
 * the meta, so the screen can offer to build one from past spending instead of
 * showing an error.
 */
@Serializable
data class BudgetView(
    val data: Budget? = null,
    val meta: BudgetMeta = BudgetMeta(),
) {
    val isSet: Boolean get() = data != null
}
