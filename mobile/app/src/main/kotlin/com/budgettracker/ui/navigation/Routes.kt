package com.budgettracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination, in one place. Routes are plain strings because the graph
 * is small and flat; the few that take an argument build their path through a
 * function so a caller cannot misspell the template.
 */
object Routes {

    // Signed out
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign-in"
    const val SIGN_UP = "sign-up"

    // Guided setup
    const val ONBOARDING = "onboarding"

    // Tabs
    const val HOME = "home"
    const val REPORTS = "reports"
    const val ACTIVITY = "activity"
    const val BUDGET = "budget"
    const val MORE = "more"

    // Pushed screens
    const val ACCOUNTS = "accounts"
    const val RECEIPTS = "receipts"
    const val BILLS = "bills"
    const val SAVINGS = "savings"
    const val RECURRING = "recurring"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"

    private const val RECEIPT_REVIEW_BASE = "receipt-review"
    const val RECEIPT_REVIEW = "$RECEIPT_REVIEW_BASE/{receiptId}"
    const val ARG_RECEIPT_ID = "receiptId"

    fun receiptReview(receiptId: Long): String = "$RECEIPT_REVIEW_BASE/$receiptId"
}

/**
 * The five tabs in the bottom bar.
 *
 * Insights is one of them rather than something reached through a menu: it
 * is the other half of the home screen — home says what is happening now,
 * insights says what has been happening — and burying it made it a screen
 * nobody opened.
 */
enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(Routes.HOME, "Home", Icons.Outlined.Home),
    Insights(Routes.REPORTS, "Insights", Icons.AutoMirrored.Outlined.ShowChart),
    Activity(Routes.ACTIVITY, "Activity", Icons.Outlined.History),
    Budget(Routes.BUDGET, "Budget", Icons.Outlined.AccountBalanceWallet),
    More(Routes.MORE, "More", Icons.Outlined.PersonOutline),
    ;

    companion object {
        fun forRoute(route: String?): Tab? = entries.firstOrNull { it.route == route }
    }
}
