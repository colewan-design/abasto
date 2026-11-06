package com.budgettracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.BuildConfig
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.CurrencyOption
import com.budgettracker.data.model.User
import com.budgettracker.data.remote.request.UpdateProfileRequest
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.OnboardingRepository
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.data.repository.SessionState
import com.budgettracker.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val currencies: List<CurrencyOption> = emptyList(),
    val appLockEnabled: Boolean = false,
    val lockSupported: Boolean = true,
    val signedOut: Boolean = false,
) {
    val appVersion: String get() = BuildConfig.VERSION_NAME
    val apiBase: String get() = BuildConfig.API_BASE_URL
    val isDemo: Boolean get() = user?.isGuest == true
}

/**
 * Profile, the app lock, and the ways out of the app.
 *
 * Deleting an account and signing out both clear the local session; the
 * difference is whether the records on the server go with it, which the
 * confirmation makes explicit.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val onboarding: OnboardingRepository,
    private val reference: ReferenceRepository,
    private val lock: AppLockManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.state.collect { session ->
                val user = (session as? SessionState.SignedIn)?.user

                _state.update {
                    it.copy(user = user, appLockEnabled = user?.appLockEnabled ?: false)
                }
            }
        }

        viewModelScope.launch {
            // The currency list comes from the setup endpoint, which is also
            // the server's list of what it will accept.
            when (val outcome = onboarding.options()) {
                is Outcome.Ok -> _state.update { it.copy(currencies = outcome.value.currencies) }
                is Outcome.Err -> Unit
            }
        }

        viewModelScope.launch { auth.refreshProfile() }
    }

    fun setLockSupported(supported: Boolean) = _state.update { it.copy(lockSupported = supported) }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }

            when (val outcome = auth.setAppLockEnabled(enabled)) {
                is Outcome.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        appLockEnabled = enabled,
                        message = if (enabled) {
                            "The app will lock when you leave it."
                        } else {
                            "App lock turned off."
                        },
                    )
                }

                is Outcome.Err -> _state.update {
                    // The local setting already took effect, so say what is true.
                    it.copy(
                        busy = false,
                        appLockEnabled = enabled,
                        message = "Saved on this device, but not synced: ${outcome.error.message}",
                    )
                }
            }
        }
    }

    fun changeCurrency(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }

            when (val outcome = auth.updateProfile(UpdateProfileRequest(currencyCode = code))) {
                is Outcome.Ok -> _state.update {
                    it.copy(busy = false, message = "Now showing amounts in $code.")
                }

                is Outcome.Err -> _state.update {
                    it.copy(busy = false, message = outcome.error.message)
                }
            }
        }
    }

    fun changeName(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }

            when (val outcome = auth.updateProfile(UpdateProfileRequest(name = name.trim()))) {
                is Outcome.Ok -> _state.update { it.copy(busy = false, message = "Name updated.") }
                is Outcome.Err -> _state.update {
                    it.copy(busy = false, message = outcome.error.message)
                }
            }
        }
    }

    /** Puts the demo account back to the sample data it started with. */
    fun resetDemo() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }

            when (val outcome = auth.resetDemoData()) {
                is Outcome.Ok -> {
                    reference.refresh()
                    _state.update { it.copy(busy = false, message = "Sample data restored.") }
                }

                is Outcome.Err -> _state.update {
                    it.copy(busy = false, message = outcome.error.message)
                }
            }
        }
    }

    fun signOut(everywhere: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }

            auth.signOut(everywhere)
            reference.clear()
            lock.reset()

            _state.update { it.copy(busy = false, signedOut = true) }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }

            when (val outcome = auth.deleteAccount()) {
                is Outcome.Ok -> {
                    reference.clear()
                    lock.reset()
                    _state.update { it.copy(busy = false, signedOut = true) }
                }

                is Outcome.Err -> _state.update {
                    it.copy(busy = false, message = outcome.error.message)
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
