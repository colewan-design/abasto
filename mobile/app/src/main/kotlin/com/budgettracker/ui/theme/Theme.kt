package com.budgettracker.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Money colours ride alongside Material's scheme because the scheme has no
 * slot for "this figure is money coming in". Read them through
 * `MaterialTheme.money` so a composable never reaches for a raw palette value.
 */
val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

/**
 * The design's own surfaces: the dark hero card, the accent, and the hues a
 * category card is painted in. Read through `MaterialTheme.accents`.
 */
val LocalAccentColors = staticCompositionLocalOf { LightAccentColors }

val MaterialTheme.money: MoneyColors
    @Composable
    get() = LocalMoneyColors.current

val MaterialTheme.accents: AccentColors
    @Composable
    get() = LocalAccentColors.current

@Composable
fun BudgetTrackerTheme(
    mode: ThemeMode = ThemeMode.System,
    onModeChange: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val followingSystem = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        ThemeMode.System -> followingSystem
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val scheme = if (darkTheme) DarkScheme else LightScheme
    val moneyColors = if (darkTheme) DarkMoneyColors else LightMoneyColors
    val accentColors = if (darkTheme) DarkAccentColors else LightAccentColors
    val view = LocalView.current

    val controller = remember(mode, darkTheme, onModeChange) {
        ThemeController(mode = mode, isDark = darkTheme, setMode = onModeChange)
    }

    if (!view.isInEditMode) {
        val window = LocalActivity.current?.window

        SideEffect {
            if (window != null) {
                // The bars are already transparent — the activity goes edge to
                // edge — so the only thing left is which way round their icons
                // are drawn, which follows the theme rather than the system's.
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalMoneyColors provides moneyColors,
        LocalAccentColors provides accentColors,
        LocalThemeController provides controller,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BudgetTypography,
            shapes = BudgetShapes,
            content = content,
        )
    }
}
