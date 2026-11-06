package com.budgettracker.data.model

import com.budgettracker.core.money.Currency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val name: String,
    val email: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val currency: Currency = Currency.Default,
    val locale: String = "en",
    val timezone: String = "UTC",
    @SerialName("is_guest") val isGuest: Boolean = false,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("is_google_linked") val isGoogleLinked: Boolean = false,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @SerialName("app_lock_enabled") val appLockEnabled: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** First name, for greeting a person without shouting their full name. */
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }

    val initials: String
        get() = name.trim().split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
}

/** What a successful sign-in returns, whatever the method was. */
@Serializable
data class AuthResponse(
    val token: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_at") val expiresAt: String? = null,
    val user: User,
    @SerialName("needs_onboarding") val needsOnboarding: Boolean = false,
)

@Serializable
data class AppNotification(
    val id: String,
    val type: String,
    val title: String? = null,
    val body: String? = null,
    val read: Boolean = false,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NotificationPage(
    val data: List<AppNotification> = emptyList(),
    val meta: NotificationMeta? = null,
)

@Serializable
data class NotificationMeta(
    val unread: Int = 0,
)
