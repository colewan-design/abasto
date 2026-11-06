package com.budgettracker.data.model

import java.time.LocalDate

/**
 * The query behind the activity list. Kept as one value so a screen can hand
 * the whole thing to the repository, compare it for equality to decide whether
 * to refetch, and show a count of what is applied.
 *
 * `type` takes the API's two extra values beside the transaction types:
 * `all` includes transfers, `cash_flow` leaves them out.
 */
data class TransactionFilters(
    val query: String = "",
    val type: String = TYPE_CASH_FLOW,
    val accountIds: List<Long> = emptyList(),
    val categoryIds: List<Long> = emptyList(),
    val tagIds: List<Long> = emptyList(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val hasReceipt: Boolean? = null,
    val uncategorized: Boolean? = null,
    val sort: String = "occurred_on",
    val direction: String = "desc",
) {
    /** How many narrowing choices are in force, for the filter button badge. */
    val appliedCount: Int
        get() = listOf(
            accountIds.isNotEmpty(),
            categoryIds.isNotEmpty(),
            tagIds.isNotEmpty(),
            from != null || to != null,
            minAmountMinor != null || maxAmountMinor != null,
            hasReceipt == true,
            uncategorized == true,
            type != TYPE_CASH_FLOW,
        ).count { it }

    val isNarrowed: Boolean get() = appliedCount > 0 || query.isNotBlank()

    fun cleared(): TransactionFilters = TransactionFilters(query = query)

    companion object {
        const val TYPE_ALL = "all"
        const val TYPE_CASH_FLOW = "cash_flow"

        val typeOptions: List<Pair<String, String>> = listOf(
            TYPE_CASH_FLOW to "Income & expenses",
            TYPE_ALL to "Everything",
            "expense" to "Expenses",
            "income" to "Income",
            "transfer" to "Transfers",
        )
    }
}
