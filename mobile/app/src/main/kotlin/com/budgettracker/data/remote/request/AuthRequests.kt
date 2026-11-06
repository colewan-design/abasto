package com.budgettracker.data.remote.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request bodies, written as their own types rather than maps so a field the
 * API renamed breaks the build instead of a screen.
 *
 * Nulls are omitted on the way out (`explicitNulls = false`), which matches
 * Laravel's distinction between a field that was left out and one that was
 * explicitly cleared.
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class GuestRequest(
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class GoogleRequest(
    @SerialName("id_token") val idToken: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("discard_demo") val discardDemo: Boolean? = null,
)

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    @SerialName("app_lock_enabled") val appLockEnabled: Boolean? = null,
    val password: String? = null,
    @SerialName("password_confirmation") val passwordConfirmation: String? = null,
    @SerialName("current_password") val currentPassword: String? = null,
)

@Serializable
data class DeviceRequest(
    val platform: String = "android",
    @SerialName("push_token") val pushToken: String,
    val name: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

/* ---------------------------------------------------------------- onboarding */

@Serializable
data class OnboardingAccount(
    val name: String,
    val type: String,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long = 0,
    @SerialName("credit_limit_minor") val creditLimitMinor: Long? = null,
    val institution: String? = null,
    val color: String? = null,
    val icon: String? = null,
)

@Serializable
data class OnboardingBudgetLine(
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long,
)

@Serializable
data class OnboardingBudget(
    @SerialName("overall_amount_minor") val overallAmountMinor: Long? = null,
    val categories: List<OnboardingBudgetLine>? = null,
)

@Serializable
data class CompleteOnboardingRequest(
    @SerialName("currency_code") val currencyCode: String,
    val locale: String? = null,
    val timezone: String? = null,
    val accounts: List<OnboardingAccount>,
    val categories: List<String>? = null,
    val budget: OnboardingBudget? = null,
)

@Serializable
data class SeedCategoriesRequest(
    val categories: List<String>? = null,
)
