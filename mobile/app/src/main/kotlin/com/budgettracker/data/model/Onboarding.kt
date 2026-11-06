package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrencyOption(
    val code: String,
    val name: String = "",
    val symbol: String = "",
    val exponent: Int = 2,
)

@Serializable
data class AccountTypeOption(
    val value: AccountType,
    val label: String = "",
    val icon: String? = null,
    @SerialName("is_liability") val isLiability: Boolean = false,
)

@Serializable
data class CatalogCategory(
    val name: String,
    val icon: String? = null,
    val recommended: Boolean = false,
)

@Serializable
data class CatalogGroup(
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val categories: List<CatalogCategory> = emptyList(),
)

@Serializable
data class CatalogSection(
    val type: CategoryType = CategoryType.Expense,
    val groups: List<CatalogGroup> = emptyList(),
)

/**
 * Everything guided setup needs up front: the currencies the API accepts, the
 * account types it knows, and the starter category catalog. Fetched once so
 * the wizard can run without a round trip per step.
 */
@Serializable
data class OnboardingOptions(
    val currencies: List<CurrencyOption> = emptyList(),
    @SerialName("account_types") val accountTypes: List<AccountTypeOption> = emptyList(),
    val catalog: List<CatalogSection> = emptyList(),
    @SerialName("recommended_categories") val recommendedCategories: List<String> = emptyList(),
    @SerialName("detected_currency") val detectedCurrency: String = "PHP",
    val completed: Boolean = false,
)

@Serializable
data class OnboardingResult(
    val message: String? = null,
    val user: User,
    val accounts: List<Account> = emptyList(),
    val budget: Budget? = null,
)
