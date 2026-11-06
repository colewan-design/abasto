package com.budgettracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.budgettracker.data.repository.SessionState
import com.budgettracker.feature.auth.SignInScreen
import com.budgettracker.feature.auth.SignUpScreen
import com.budgettracker.feature.auth.WelcomeScreen
import com.budgettracker.feature.onboarding.OnboardingScreen
import com.budgettracker.ui.lock.BiometricGate
import com.budgettracker.ui.navigation.Routes
import com.budgettracker.ui.shell.MainShell

/**
 * The root of the app.
 *
 * There is one decision here and it is made in one place: a stored session
 * that still needs setting up gets the wizard, a session that is ready gets
 * the app behind the lock, and no session gets the welcome screen. Nothing
 * else in the app navigates between those three — signing in or out moves the
 * session, and the graph follows.
 */
@Composable
fun BudgetTrackerApp(
    onReady: () -> Unit = {},
    viewModel: RootViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()

    // Releases the splash screen once the stored session has been read, so the
    // welcome screen never flashes at someone who is already signed in.
    LaunchedEffect(session) {
        if (session !is SessionState.Loading) onReady()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val current = session) {
            SessionState.Loading -> Box(modifier = Modifier.fillMaxSize())

            SessionState.SignedOut -> AuthGraph()

            is SessionState.SignedIn -> when {
                current.needsOnboarding -> OnboardingScreen(onFinished = {})

                else -> BiometricGate(
                    enabled = current.appLockEnabled,
                    lock = viewModel.lockManager,
                    locked = locked,
                ) {
                    MainShell(root = viewModel)
                }
            }
        }
    }
}

@Composable
private fun AuthGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onSignIn = { navController.navigate(Routes.SIGN_IN) },
                onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
            )
        }

        composable(Routes.SIGN_IN) {
            SignInScreen(
                onBack = { navController.popBackStack() },
                onCreateAccount = {
                    navController.navigate(Routes.SIGN_UP) {
                        popUpTo(Routes.WELCOME)
                    }
                },
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onBack = { navController.popBackStack() },
                onSignIn = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.WELCOME)
                    }
                },
            )
        }
    }
}
