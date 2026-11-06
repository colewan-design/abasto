package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CategoryType {
    @SerialName("income") Income,
    @SerialName("expense") Expense,
    ;

    val wire: String get() = if (this == Income) "income" else "expense"
}

@Serializable
data class CategoryGroupRef(
    val id: Long,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
)

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val type: CategoryType = CategoryType.Expense,
    val color: String? = null,
    val icon: String? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("category_group_id") val categoryGroupId: Long? = null,
    val group: CategoryGroupRef? = null,
    @SerialName("is_system") val isSystem: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
    val keywords: List<String> = emptyList(),
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("transactions_count") val transactionsCount: Int? = null,
)

@Serializable
data class CategoryGroup(
    val id: Long,
    val name: String,
    val type: CategoryType = CategoryType.Expense,
    val color: String? = null,
    val icon: String? = null,
    @SerialName("is_system") val isSystem: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val categories: List<Category> = emptyList(),
    @SerialName("categories_count") val categoriesCount: Int? = null,
)

@Serializable
data class Tag(
    val id: Long,
    val name: String,
    val color: String? = null,
    @SerialName("transactions_count") val transactionsCount: Int? = null,
)

/**
 * What the server thinks a merchant belongs under. `proposal` is filled when
 * nothing matched and the server would rather create a category than guess —
 * the client offers it as "Add «Groceries»" instead of silently applying it.
 */
@Serializable
data class CategorySuggestion(
    val category: Category? = null,
    val confidence: Double = 0.0,
    val source: String = "",
    val proposal: CategoryProposal? = null,
)

@Serializable
data class CategoryProposal(
    val name: String,
    val group: String? = null,
    val icon: String? = null,
    val color: String? = null,
)
