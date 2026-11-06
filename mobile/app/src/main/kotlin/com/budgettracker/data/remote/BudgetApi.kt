package com.budgettracker.data.remote

import com.budgettracker.data.model.Account
import com.budgettracker.data.model.AccountDeletion
import com.budgettracker.data.model.AccountList
import com.budgettracker.data.model.AuthResponse
import com.budgettracker.data.model.Bill
import com.budgettracker.data.model.BillPaymentResponse
import com.budgettracker.data.model.BudgetSuggestion
import com.budgettracker.data.model.BudgetView
import com.budgettracker.data.model.CashFlowReport
import com.budgettracker.data.model.Category
import com.budgettracker.data.model.CategoryBreakdown
import com.budgettracker.data.model.CategoryGroup
import com.budgettracker.data.model.CategorySuggestion
import com.budgettracker.data.model.ContributionResponse
import com.budgettracker.data.model.Dashboard
import com.budgettracker.data.model.DataEnvelope
import com.budgettracker.data.model.ListEnvelope
import com.budgettracker.data.model.MerchantTotal
import com.budgettracker.data.model.MessageResponse
import com.budgettracker.data.model.MonthlyTrend
import com.budgettracker.data.model.NotificationPage
import com.budgettracker.data.model.OnboardingOptions
import com.budgettracker.data.model.OnboardingResult
import com.budgettracker.data.model.Receipt
import com.budgettracker.data.model.ReceiptConfirmation
import com.budgettracker.data.model.ReceiptDetail
import com.budgettracker.data.model.ReceiptPage
import com.budgettracker.data.model.RecurringRule
import com.budgettracker.data.model.SavingsGoal
import com.budgettracker.data.model.Tag
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionPage
import com.budgettracker.data.model.TransferResponse
import com.budgettracker.data.model.User
import com.budgettracker.data.remote.request.AccountRequest
import com.budgettracker.data.remote.request.BillRequest
import com.budgettracker.data.remote.request.CategoryGroupRequest
import com.budgettracker.data.remote.request.CategoryRequest
import com.budgettracker.data.remote.request.CompleteOnboardingRequest
import com.budgettracker.data.remote.request.ConfirmReceiptRequest
import com.budgettracker.data.remote.request.ContributionRequest
import com.budgettracker.data.remote.request.DeviceRequest
import com.budgettracker.data.remote.request.GoogleRequest
import com.budgettracker.data.remote.request.GuestRequest
import com.budgettracker.data.remote.request.LoginRequest
import com.budgettracker.data.remote.request.PayBillRequest
import com.budgettracker.data.remote.request.RecurringRuleRequest
import com.budgettracker.data.remote.request.RegisterRequest
import com.budgettracker.data.remote.request.SavingsGoalRequest
import com.budgettracker.data.remote.request.SeedCategoriesRequest
import com.budgettracker.data.remote.request.SuggestCategoryRequest
import com.budgettracker.data.remote.request.TagRequest
import com.budgettracker.data.remote.request.TransactionRequest
import com.budgettracker.data.remote.request.TransferRequest
import com.budgettracker.data.remote.request.UpdateBudgetRequest
import com.budgettracker.data.remote.request.UpdateProfileRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `/api/v1` in full. One interface for the whole contract keeps the endpoint
 * list in a single readable place; repositories pick the slice they need.
 *
 * Collection endpoints return the envelope rather than a bare list because the
 * `meta` block carries totals and balances the screens display.
 */
interface BudgetApi {

    /* ------------------------------------------------------------------ auth */

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/guest")
    suspend fun guest(@Body body: GuestRequest): AuthResponse

    @POST("auth/google")
    suspend fun google(@Body body: GoogleRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(): MessageResponse

    @POST("auth/logout-all")
    suspend fun logoutEverywhere(): MessageResponse

    /* --------------------------------------------------------------- profile */

    @GET("me")
    suspend fun profile(): DataEnvelope<User>

    @PATCH("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): DataEnvelope<User>

    @DELETE("me")
    suspend fun deleteProfile(): MessageResponse

    /* ------------------------------------------------------------ onboarding */

    @GET("onboarding/options")
    suspend fun onboardingOptions(): DataEnvelope<OnboardingOptions>

    @POST("onboarding/complete")
    suspend fun completeOnboarding(@Body body: CompleteOnboardingRequest): OnboardingResult

    @POST("onboarding/categories")
    suspend fun seedCategories(@Body body: SeedCategoriesRequest): MessageResponse

    @POST("demo/reset")
    suspend fun resetDemo(): MessageResponse

    /* -------------------------------------------------------------- accounts */

    @GET("accounts")
    suspend fun accounts(
        @Query("include_archived") includeArchived: Boolean? = null,
    ): AccountList

    @POST("accounts")
    suspend fun createAccount(@Body body: AccountRequest): DataEnvelope<Account>

    @GET("accounts/{id}")
    suspend fun account(@Path("id") id: Long): DataEnvelope<Account>

    @PATCH("accounts/{id}")
    suspend fun updateAccount(@Path("id") id: Long, @Body body: AccountRequest): DataEnvelope<Account>

    /**
     * An account with history is archived rather than deleted so the ledger
     * stays intact; the response says which happened.
     */
    @DELETE("accounts/{id}")
    suspend fun deleteAccount(
        @Path("id") id: Long,
        @Query("force") force: Boolean? = null,
    ): AccountDeletion

    @GET("accounts/{id}/transactions")
    suspend fun accountTransactions(
        @Path("id") id: Long,
        @Query("per_page") perPage: Int? = null,
        @Query("page") page: Int? = null,
    ): TransactionPage

    @POST("accounts/recalculate")
    suspend fun recalculateBalances(): MessageResponse

    /* ------------------------------------------------- categories, tags */

    @GET("categories")
    suspend fun categories(
        @Query("type") type: String? = null,
        @Query("include_archived") includeArchived: Boolean? = null,
    ): ListEnvelope<Category>

    @POST("categories")
    suspend fun createCategory(@Body body: CategoryRequest): DataEnvelope<Category>

    @PATCH("categories/{id}")
    suspend fun updateCategory(@Path("id") id: Long, @Body body: CategoryRequest): DataEnvelope<Category>

    @DELETE("categories/{id}")
    suspend fun deleteCategory(
        @Path("id") id: Long,
        @Query("reassign_to") reassignTo: Long? = null,
    ): MessageResponse

    @POST("categories/suggest")
    suspend fun suggestCategory(@Body body: SuggestCategoryRequest): DataEnvelope<CategorySuggestion>

    @GET("category-groups")
    suspend fun categoryGroups(
        @Query("type") type: String? = null,
    ): ListEnvelope<CategoryGroup>

    @POST("category-groups")
    suspend fun createCategoryGroup(@Body body: CategoryGroupRequest): DataEnvelope<CategoryGroup>

    @GET("tags")
    suspend fun tags(): ListEnvelope<Tag>

    @POST("tags")
    suspend fun createTag(@Body body: TagRequest): DataEnvelope<Tag>

    @DELETE("tags/{id}")
    suspend fun deleteTag(@Path("id") id: Long): MessageResponse

    /* ---------------------------------------------------------------- ledger */

    @GET("transactions")
    suspend fun transactions(
        @Query("q") query: String? = null,
        @Query("type") type: String? = null,
        @Query("account_id[]") accountIds: List<Long>? = null,
        @Query("category_id[]") categoryIds: List<Long>? = null,
        @Query("tag_id[]") tagIds: List<Long>? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("min_amount_minor") minAmountMinor: Long? = null,
        @Query("max_amount_minor") maxAmountMinor: Long? = null,
        @Query("has_receipt") hasReceipt: Boolean? = null,
        @Query("uncategorized") uncategorized: Boolean? = null,
        @Query("savings_goal_id") savingsGoalId: Long? = null,
        @Query("bill_id") billId: Long? = null,
        @Query("collapse_transfers") collapseTransfers: Boolean? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("page") page: Int? = null,
    ): TransactionPage

    @POST("transactions")
    suspend fun createTransaction(@Body body: TransactionRequest): DataEnvelope<Transaction>

    @GET("transactions/{id}")
    suspend fun transaction(@Path("id") id: Long): DataEnvelope<Transaction>

    @PATCH("transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: Long,
        @Body body: TransactionRequest,
    ): DataEnvelope<Transaction>

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: Long): MessageResponse

    @POST("transfers")
    suspend fun createTransfer(@Body body: TransferRequest): TransferResponse

    /* -------------------------------------------------------------- receipts */

    @GET("receipts")
    suspend fun receipts(
        @Query("status") status: String? = null,
        @Query("awaiting_review") awaitingReview: Boolean? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("page") page: Int? = null,
    ): ReceiptPage

    /**
     * The image goes up with whatever text ML Kit read on the device, so the
     * server can parse it without paying for a cloud OCR call.
     *
     * `process = true` extracts inline and returns the draft in this response,
     * which is what the capture flow wants — the alternative queues the work
     * and leaves the client polling.
     */
    @Multipart
    @POST("receipts")
    suspend fun uploadReceipt(
        @Part image: MultipartBody.Part,
        @Part("recognized_text") recognizedText: RequestBody? = null,
        @Part("captured_at") capturedAt: RequestBody? = null,
        @Part("process") process: RequestBody? = null,
    ): DataEnvelope<Receipt>

    /** Carries the suggested category and split for the review screen. */
    @GET("receipts/{id}")
    suspend fun receipt(@Path("id") id: Long): ReceiptDetail

    @POST("receipts/{id}/reprocess")
    suspend fun reprocessReceipt(@Path("id") id: Long): DataEnvelope<Receipt>

    @POST("receipts/{id}/confirm")
    suspend fun confirmReceipt(
        @Path("id") id: Long,
        @Body body: ConfirmReceiptRequest,
    ): ReceiptConfirmation

    @DELETE("receipts/{id}")
    suspend fun deleteReceipt(@Path("id") id: Long): MessageResponse

    /* ---------------------------------------------------------------- budget */

    @GET("budget")
    suspend fun budget(@Query("month") month: String? = null): BudgetView

    @PUT("budget")
    suspend fun updateBudget(@Body body: UpdateBudgetRequest): BudgetView

    @GET("budget/suggestions")
    suspend fun budgetSuggestions(
        @Query("month") month: String? = null,
    ): ListEnvelope<BudgetSuggestion>

    /* ----------------------------------------------------------------- bills */

    @GET("bills")
    suspend fun bills(
        @Query("include_inactive") includeInactive: Boolean? = null,
    ): ListEnvelope<Bill>

    @GET("bills/upcoming")
    suspend fun upcomingBills(@Query("days") days: Int? = null): ListEnvelope<Bill>

    @POST("bills")
    suspend fun createBill(@Body body: BillRequest): DataEnvelope<Bill>

    @GET("bills/{id}")
    suspend fun bill(@Path("id") id: Long): DataEnvelope<Bill>

    @PATCH("bills/{id}")
    suspend fun updateBill(@Path("id") id: Long, @Body body: BillRequest): DataEnvelope<Bill>

    @DELETE("bills/{id}")
    suspend fun deleteBill(@Path("id") id: Long): MessageResponse

    @POST("bills/{id}/pay")
    suspend fun payBill(@Path("id") id: Long, @Body body: PayBillRequest): BillPaymentResponse

    @POST("bills/{id}/skip")
    suspend fun skipBill(@Path("id") id: Long): BillPaymentResponse

    @DELETE("bills/{id}/payments/{paymentId}")
    suspend fun undoBillPayment(
        @Path("id") id: Long,
        @Path("paymentId") paymentId: Long,
    ): MessageResponse

    /* --------------------------------------------------------- savings goals */

    @GET("savings-goals")
    suspend fun savingsGoals(
        @Query("include_archived") includeArchived: Boolean? = null,
    ): ListEnvelope<SavingsGoal>

    @POST("savings-goals")
    suspend fun createSavingsGoal(@Body body: SavingsGoalRequest): DataEnvelope<SavingsGoal>

    @GET("savings-goals/{id}")
    suspend fun savingsGoal(@Path("id") id: Long): DataEnvelope<SavingsGoal>

    @PATCH("savings-goals/{id}")
    suspend fun updateSavingsGoal(
        @Path("id") id: Long,
        @Body body: SavingsGoalRequest,
    ): DataEnvelope<SavingsGoal>

    @DELETE("savings-goals/{id}")
    suspend fun deleteSavingsGoal(@Path("id") id: Long): MessageResponse

    @POST("savings-goals/{id}/contributions")
    suspend fun contribute(
        @Path("id") id: Long,
        @Body body: ContributionRequest,
    ): ContributionResponse

    @DELETE("savings-goals/{id}/contributions/{contributionId}")
    suspend fun removeContribution(
        @Path("id") id: Long,
        @Path("contributionId") contributionId: Long,
    ): MessageResponse

    /* ------------------------------------------------------ recurring rules */

    @GET("recurring-rules")
    suspend fun recurringRules(
        @Query("include_inactive") includeInactive: Boolean? = null,
    ): ListEnvelope<RecurringRule>

    @POST("recurring-rules")
    suspend fun createRecurringRule(@Body body: RecurringRuleRequest): DataEnvelope<RecurringRule>

    @PATCH("recurring-rules/{id}")
    suspend fun updateRecurringRule(
        @Path("id") id: Long,
        @Body body: RecurringRuleRequest,
    ): DataEnvelope<RecurringRule>

    @DELETE("recurring-rules/{id}")
    suspend fun deleteRecurringRule(@Path("id") id: Long): MessageResponse

    @POST("recurring-rules/{id}/run")
    suspend fun runRecurringRule(@Path("id") id: Long): MessageResponse

    /* --------------------------------------------------------------- reports */

    @GET("reports/dashboard")
    suspend fun dashboard(@Query("month") month: String? = null): DataEnvelope<Dashboard>

    @GET("reports/cash-flow")
    suspend fun cashFlow(
        @Query("month") month: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): DataEnvelope<CashFlowReport>

    @GET("reports/categories")
    suspend fun categoryReport(
        @Query("month") month: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("type") type: String? = null,
    ): ListEnvelope<CategoryBreakdown>

    @GET("reports/trends")
    suspend fun trends(@Query("months") months: Int? = null): ListEnvelope<MonthlyTrend>

    @GET("reports/merchants")
    suspend fun merchants(
        @Query("month") month: String? = null,
        @Query("limit") limit: Int? = null,
    ): ListEnvelope<MerchantTotal>

    /* ------------------------------------------- notifications and devices */

    @GET("notifications")
    suspend fun notifications(
        @Query("unread") unread: Boolean? = null,
        @Query("per_page") perPage: Int? = null,
    ): NotificationPage

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): MessageResponse

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): MessageResponse

    @DELETE("notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: String): MessageResponse

    @POST("devices")
    suspend fun registerDevice(@Body body: DeviceRequest): MessageResponse

    @DELETE("devices")
    suspend fun unregisterDevice(@Query("push_token") pushToken: String): MessageResponse
}
