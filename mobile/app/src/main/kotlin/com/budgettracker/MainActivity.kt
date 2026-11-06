package com.budgettracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.ui.BudgetTrackerApp
import com.budgettracker.ui.theme.BudgetTrackerTheme
import com.budgettracker.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity. Everything else is Compose.
 *
 * It extends `AppCompatActivity` rather than `ComponentActivity` because the
 * biometric prompt behind the app lock needs a `FragmentActivity` to attach to.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Held until the stored session has been read, so the app never flashes
        // the welcome screen at someone who is already signed in.
        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            // Scoped to the activity rather than to a screen: the theme wraps
            // the whole graph, so the light/dark choice has to outlive every
            // destination inside it.
            val theme: ThemeViewModel = hiltViewModel()
            val mode by theme.mode.collectAsStateWithLifecycle()

            BudgetTrackerTheme(mode = mode, onModeChange = theme::set) {
                BudgetTrackerApp(onReady = { ready = true })
            }
        }
    }
}
