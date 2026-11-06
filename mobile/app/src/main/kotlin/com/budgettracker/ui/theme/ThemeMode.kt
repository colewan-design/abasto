package com.budgettracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which palette the app draws in.
 *
 * [System] is the default and the only value a fresh install has — the
 * toggle on the budget screen is what moves it off, and once someone has
 * chosen, the choice outlives the system changing its mind.
 */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun parse(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: System
    }
}

/**
 * The stored preference. It lives in the same DataStore as the session but
 * is deliberately not cleared with it: signing out should not throw away
 * how someone likes the app to look.
 */
@Singleton
class ThemeStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val key = stringPreferencesKey("theme_mode")

    val mode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.parse(it[key]) }

    suspend fun set(mode: ThemeMode) {
        dataStore.edit { it[key] = mode.name }
    }
}

/**
 * What a composable needs to show the toggle: whether it is currently dark,
 * and how to flip it.
 *
 * Flipping writes an explicit Light or Dark — there is no third press that
 * returns to following the system, because a control with two icons that has
 * three states is a control nobody can predict.
 */
@Immutable
data class ThemeController(
    val mode: ThemeMode,
    val isDark: Boolean,
    val setMode: (ThemeMode) -> Unit,
) {
    fun toggle() = setMode(if (isDark) ThemeMode.Light else ThemeMode.Dark)
}

val LocalThemeController = staticCompositionLocalOf {
    ThemeController(mode = ThemeMode.System, isDark = false, setMode = {})
}
