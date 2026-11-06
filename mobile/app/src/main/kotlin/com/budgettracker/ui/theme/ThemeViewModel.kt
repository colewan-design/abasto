package com.budgettracker.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the theme preference for the whole app.
 *
 * It is read above every screen — the theme wraps the navigation graph, not
 * the other way round — so it is scoped to the activity rather than to a
 * destination, and the first frame uses [ThemeMode.System] while the stored
 * value is still on its way off disk.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val store: ThemeStore,
) : ViewModel() {

    val mode: StateFlow<ThemeMode> = store.mode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.System,
    )

    fun set(mode: ThemeMode) {
        viewModelScope.launch { store.set(mode) }
    }
}
