package com.budgettracker.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgettracker.data.model.Transaction
import com.budgettracker.data.model.TransactionType
import com.budgettracker.feature.accounts.AccountsScreen
import com.budgettracker.feature.bills.BillsScreen
import com.budgettracker.feature.budget.BudgetScreen
import com.budgettracker.feature.home.HomeScreen
import com.budgettracker.feature.more.MoreScreen
import com.budgettracker.feature.notifications.NotificationsScreen
import com.budgettracker.feature.receipts.ReceiptReviewScreen
import com.budgettracker.feature.receipts.ReceiptsScreen
import com.budgettracker.feature.recurring.RecurringScreen
import com.budgettracker.feature.reports.ReportsScreen
import com.budgettracker.feature.savings.SavingsScreen
import com.budgettracker.feature.settings.SettingsScreen
import com.budgettracker.feature.transactions.ActivityScreen
import com.budgettracker.feature.transactions.TransactionComposerSheet
import com.budgettracker.feature.transactions.TransactionDetailSheet
import com.budgettracker.feature.update.UpdatePrompt
import com.budgettracker.ui.RootViewModel
import com.budgettracker.ui.navigation.Routes
import com.budgettracker.ui.navigation.Tab

/**
 * The signed-in app: five tabs, the screens they lead to, and the two sheets
 * that can open over any of them.
 *
 * The composer and the detail sheet live here rather than inside a screen
 * because both are reachable from more than one — a row tapped on the home
 * screen opens the same sheet as a row tapped in the activity list, and the
 * home screen's four round buttons each open it on a different kind of entry.
 */
@Composable
fun MainShell(root: RootViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tab = Tab.forRoute(currentRoute)

    val snackbar = remember { SnackbarHostState() }
    val message by root.message.collectAsStateWithLifecycle()

    var detail by remember { mutableStateOf<Transaction?>(null) }
    var composing by remember { mutableStateOf<ComposerRequest?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            root.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(visible = tab != null) {
                AppBottomBar(current = tab, onSelect = navController::switchTab)
            }
        },
        floatingActionButton = {
            // The home screen has its own row of actions under the balance,
            // so the button is offered only where recording something is the
            // obvious next move and nothing else says so.
            if (tab == Tab.Activity) {
                FloatingActionButton(
                    onClick = {
                        composing = ComposerRequest(editing = null, type = TransactionType.Expense)
                    },
                    // Filled with the accent rather than its container tint:
                    // this is the one button on the screen, and it sits over
                    // a scrolling list that it has to stay legible against.
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Record something")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenTransaction = { transaction -> detail = transaction },
                        onRecord = { type ->
                            composing = ComposerRequest(editing = null, type = type)
                        },
                        onSeeAllActivity = { navController.switchTab(Tab.Activity) },
                        onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                        onOpenBudget = { navController.switchTab(Tab.Budget) },
                        onOpenBills = { navController.navigate(Routes.BILLS) },
                        onOpenSavings = { navController.navigate(Routes.SAVINGS) },
                        onOpenInsights = { navController.switchTab(Tab.Insights) },
                        onOpenMore = { navController.switchTab(Tab.More) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    )
                }

                composable(Routes.REPORTS) {
                    ReportsScreen()
                }

                composable(Routes.ACTIVITY) {
                    ActivityScreen(onOpenTransaction = { transaction -> detail = transaction })
                }

                composable(Routes.BUDGET) {
                    BudgetScreen()
                }

                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                        onOpenReceipts = { navController.navigate(Routes.RECEIPTS) },
                        onOpenBills = { navController.navigate(Routes.BILLS) },
                        onOpenSavings = { navController.navigate(Routes.SAVINGS) },
                        onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                        onOpenReports = { navController.switchTab(Tab.Insights) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(Routes.ACCOUNTS) {
                    AccountsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.RECEIPTS) {
                    ReceiptsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenReview = { id -> navController.navigate(Routes.receiptReview(id)) },
                    )
                }

                composable(
                    route = Routes.RECEIPT_REVIEW,
                    arguments = listOf(
                        navArgument(Routes.ARG_RECEIPT_ID) { type = NavType.StringType },
                    ),
                ) {
                    ReceiptReviewScreen(
                        onBack = { navController.popBackStack() },
                        onRecorded = {
                            // The expense now exists; the receipts list behind
                            // this screen reloads on the ledger signal.
                            navController.popBackStack()
                        },
                    )
                }

                composable(Routes.BILLS) {
                    BillsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SAVINGS) {
                    SavingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.RECURRING) {
                    RecurringScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        // Signing out clears the session, and the root graph
                        // swaps to the welcome screen on its own.
                        onSignedOut = {},
                    )
                }
            }
        }
    }

    detail?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            onDismiss = { detail = null },
            onEdit = { editing ->
                detail = null
                composing = ComposerRequest(editing = editing, type = editing.type)
            },
            onDelete = { removing ->
                detail = null
                root.deleteTransaction(removing.id)
            },
            onOpenReceipt = { receiptId ->
                detail = null
                navController.navigate(Routes.receiptReview(receiptId))
            },
        )
    }

    composing?.let { request ->
        TransactionComposerSheet(
            editing = request.editing,
            initialType = request.type,
            onDismiss = { composing = null },
            onSaved = { composing = null },
        )
    }

    // Hosted here rather than on a screen so the launch check runs once and
    // the prompt survives moving between tabs. Inside the shell, which means
    // inside the app lock — an update is not worth showing over a locked app.
    UpdatePrompt()
}

/** What the composer was opened for: a new entry, or an existing one. */
private data class ComposerRequest(
    val editing: Transaction?,
    val type: TransactionType,
)

/**
 * Moves between tabs the way a bottom bar should: one entry per tab on the
 * back stack, and tapping the current tab returns to its root rather than
 * stacking another copy.
 */
private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
