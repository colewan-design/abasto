package com.budgettracker.data.repository

import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.core.network.discard
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.CashFlowReport
import com.budgettracker.data.model.CategoryBreakdown
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.Dashboard
import com.budgettracker.data.model.MerchantTotal
import com.budgettracker.data.model.MonthlyTrend
import com.budgettracker.data.model.NotificationPage
import com.budgettracker.data.model.OnboardingOptions
import com.budgettracker.data.model.OnboardingResult
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.CompleteOnboardingRequest
import com.budgettracker.data.remote.request.SeedCategoriesRequest
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val api: BudgetApi,
) {

    /** One request for the whole home screen. */
    suspend fun dashboard(month: YearMonth): Outcome<Dashboard> =
        apiCall { api.dashboard(Dates.wireMonth(month)).data }

    suspend fun cashFlow(month: YearMonth): Outcome<CashFlowReport> =
        apiCall { api.cashFlow(month = Dates.wireMonth(month)).data }

    suspend fun byCategory(
        month: YearMonth,
        type: CategoryType = CategoryType.Expense,
    ): Outcome<List<CategoryBreakdown>> =
        apiCall { api.categoryReport(month = Dates.wireMonth(month), type = type.wire).data }

    suspend fun trends(months: Int = 6): Outcome<List<MonthlyTrend>> =
        apiCall { api.trends(months).data }

    suspend fun merchants(month: YearMonth, limit: Int = 10): Outcome<List<MerchantTotal>> =
        apiCall { api.merchants(month = Dates.wireMonth(month), limit = limit).data }
}

@Singleton
class NotificationRepository @Inject constructor(
    private val api: BudgetApi,
) {

    suspend fun list(unreadOnly: Boolean = false): Outcome<NotificationPage> =
        apiCall { api.notifications(unread = unreadOnly.takeIf { it }, perPage = 50) }

    suspend fun markRead(id: String): Outcome<Unit> =
        apiCall { api.markNotificationRead(id) }.discard()

    suspend fun markAllRead(): Outcome<Unit> =
        apiCall { api.markAllNotificationsRead() }.discard()

    suspend fun delete(id: String): Outcome<Unit> =
        apiCall { api.deleteNotification(id) }.discard()

    suspend fun unreadCount(): Outcome<Int> =
        apiCall { api.notifications(unread = true, perPage = 1).meta?.unread ?: 0 }
}

@Singleton
class OnboardingRepository @Inject constructor(
    private val api: BudgetApi,
) {

    suspend fun options(): Outcome<OnboardingOptions> =
        apiCall { api.onboardingOptions().data }

    /**
     * Writes the whole of guided setup in one request — currency, accounts
     * with their starting balances, chosen categories and an optional budget
     * — so a half-finished wizard leaves nothing behind.
     */
    suspend fun complete(request: CompleteOnboardingRequest): Outcome<OnboardingResult> =
        apiCall { api.completeOnboarding(request) }

    /** Adds the starter categories for an account that skipped setup. */
    suspend fun seedCategories(names: List<String>? = null): Outcome<Unit> =
        apiCall { api.seedCategories(SeedCategoriesRequest(names)) }.discard()
}
