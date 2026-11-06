package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CashFlowFormatted(
    val income: String = "",
    val expense: String = "",
    val saved: String = "",
    val net: String = "",
)

@Serializable
data class CashFlow(
    @SerialName("income_minor") val incomeMinor: Long = 0,
    @SerialName("expense_minor") val expenseMinor: Long = 0,
    @SerialName("saved_minor") val savedMinor: Long = 0,
    @SerialName("net_minor") val netMinor: Long = 0,
    val formatted: CashFlowFormatted = CashFlowFormatted(),
)

@Serializable
data class ReportPeriod(
    val from: String = "",
    val to: String = "",
    val label: String = "",
)

@Serializable
data class CategoryBreakdown(
    @SerialName("category_id") val categoryId: Long? = null,
    val name: String = "",
    val color: String? = null,
    val icon: String? = null,
    val group: String? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val formatted: String = "",
    val transactions: Int = 0,
    val share: Double = 0.0,
)

@Serializable
data class MonthlyTrend(
    val period: String = "",
    val label: String = "",
    @SerialName("income_minor") val incomeMinor: Long = 0,
    @SerialName("expense_minor") val expenseMinor: Long = 0,
    @SerialName("saved_minor") val savedMinor: Long = 0,
    @SerialName("net_minor") val netMinor: Long = 0,
    val currency: String = "PHP",
)

@Serializable
data class MerchantTotal(
    val merchant: String = "",
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val formatted: String = "",
    val visits: Int = 0,
    @SerialName("average_minor") val averageMinor: Long = 0,
)

@Serializable
data class DailyFlow(
    val date: String = "",
    @SerialName("income_minor") val incomeMinor: Long = 0,
    @SerialName("expense_minor") val expenseMinor: Long = 0,
)

@Serializable
data class CashFlowReport(
    val period: ReportPeriod = ReportPeriod(),
    val totals: CashFlow = CashFlow(),
    val daily: List<DailyFlow> = emptyList(),
)

/** The budget block the dashboard embeds: progress without the budget record. */
@Serializable
data class DashboardBudget(
    val overall: BudgetOverall? = null,
    val categories: List<BudgetLine> = emptyList(),
    @SerialName("unbudgeted_minor") val unbudgetedMinor: Long = 0,
    @SerialName("uncategorized_minor") val uncategorizedMinor: Long = 0,
)

@Serializable
data class UpcomingBill(
    val id: Long,
    val name: String = "",
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val formatted: String = "",
    @SerialName("due_date") val dueDate: String = "",
    @SerialName("days_until_due") val daysUntilDue: Int = 0,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    val category: String? = null,
    val color: String? = null,
    val icon: String? = null,
)

@Serializable
data class GoalProgress(
    val id: Long,
    val name: String = "",
    @SerialName("target_minor") val targetMinor: Long = 0,
    @SerialName("saved_minor") val savedMinor: Long = 0,
    @SerialName("remaining_minor") val remainingMinor: Long = 0,
    val progress: Double = 0.0,
    @SerialName("target_date") val targetDate: String? = null,
    val formatted: SavingsFormatted = SavingsFormatted(),
)

/** Everything the home screen needs, in one request. */
@Serializable
data class Dashboard(
    val period: ReportPeriod = ReportPeriod(),
    val currency: String = "PHP",
    @SerialName("cash_flow") val cashFlow: CashFlow = CashFlow(),
    val balances: Balances = Balances(),
    val budget: DashboardBudget? = null,
    @SerialName("upcoming_bills") val upcomingBills: List<UpcomingBill> = emptyList(),
    @SerialName("savings_goals") val savingsGoals: List<GoalProgress> = emptyList(),
    @SerialName("recent_transactions") val recentTransactions: List<Transaction> = emptyList(),
    @SerialName("top_categories") val topCategories: List<CategoryBreakdown> = emptyList(),
)
