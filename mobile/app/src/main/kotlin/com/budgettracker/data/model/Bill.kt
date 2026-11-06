package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BillPaymentStatus {
    @SerialName("unpaid") Unpaid,
    @SerialName("paid") Paid,
    @SerialName("skipped") Skipped,
}

@Serializable
data class BillPayment(
    val id: Long,
    @SerialName("bill_id") val billId: Long = 0,
    @SerialName("period_due_date") val periodDueDate: String = "",
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("formatted_amount") val formattedAmount: String = "",
    @SerialName("paid_on") val paidOn: String? = null,
    val status: BillPaymentStatus = BillPaymentStatus.Unpaid,
    @SerialName("transaction_id") val transactionId: Long? = null,
)

@Serializable
data class Bill(
    val id: Long,
    val name: String,
    val payee: String? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("formatted_amount") val formattedAmount: String = "",
    @SerialName("due_date") val dueDate: String = "",
    @SerialName("days_until_due") val daysUntilDue: Int = 0,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    val recurrence: String = "monthly",
    @SerialName("recurrence_label") val recurrenceLabel: String = "",
    @SerialName("recurrence_interval") val recurrenceInterval: Int = 1,
    @SerialName("reminder_days_before") val reminderDaysBefore: Int = 3,
    @SerialName("auto_pay") val autoPay: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    val note: String? = null,
    @SerialName("last_paid_on") val lastPaidOn: String? = null,
    @SerialName("account_id") val accountId: Long? = null,
    val account: Account? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    val category: Category? = null,
    val payments: List<BillPayment> = emptyList(),
) {
    val isPaidThisCycle: Boolean
        get() = payments.any { it.periodDueDate == dueDate && it.status == BillPaymentStatus.Paid }
}

/** What `bills/{id}/pay` and `bills/{id}/skip` answer with. */
@Serializable
data class BillPaymentResponse(
    val message: String? = null,
    val payment: BillPayment? = null,
    val bill: Bill? = null,
)

/** The recurrence options the bill form offers, matching `BillRecurrence`. */
object BillRecurrences {
    val options: List<Pair<String, String>> = listOf(
        "monthly" to "Monthly",
        "weekly" to "Weekly",
        "quarterly" to "Quarterly",
        "semiannual" to "Every 6 months",
        "yearly" to "Yearly",
        "none" to "One time",
    )

    fun label(value: String): String =
        options.firstOrNull { it.first == value }?.second ?: value.replaceFirstChar { it.uppercase() }
}

/** Frequencies a recurring rule can run on, matching `RecurrenceFrequency`. */
object RecurrenceFrequencies {
    val options: List<Pair<String, String>> = listOf(
        "monthly" to "Monthly",
        "weekly" to "Weekly",
        "biweekly" to "Every 2 weeks",
        "daily" to "Daily",
        "quarterly" to "Quarterly",
        "yearly" to "Yearly",
    )

    fun label(value: String): String =
        options.firstOrNull { it.first == value }?.second ?: value.replaceFirstChar { it.uppercase() }
}
