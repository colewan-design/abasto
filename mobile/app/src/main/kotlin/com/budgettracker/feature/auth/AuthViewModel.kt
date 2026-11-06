package com.budgettracker.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val googleAvailable: Boolean = false,
) {
    val canSignIn: Boolean get() = email.isNotBlank() && password.length >= 4 && !busy

    val canRegister: Boolean
        get() = name.isNotBlank() &&
            email.isNotBlank() &&
            password.length >= 8 &&
            confirmation == password &&
            !busy
}

/**
 * Drives every way into the app: Google, the guest demo, and email with a
 * password.
 *
 * Nothing here navigates. A successful sign-in writes the session, and the
 * root composable — which is already watching it — swaps the graph. That keeps
 * "am I signed in" in exactly one place.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val google: GoogleSignInClient,
    private val lock: AppLockManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(googleAvailable = google.isConfigured))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, error = null, fieldErrors = it.fieldErrors - "email")
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, error = null, fieldErrors = it.fieldErrors - "password")
    }

    fun onConfirmationChange(value: String) = _state.update { it.copy(confirmation = value) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun signIn() = attempt {
        auth.signIn(_state.value.email, _state.value.password)
    }

    fun register() = attempt {
        val current = _state.value

        auth.register(
            name = current.name,
            email = current.email,
            password = current.password,
            confirmation = current.confirmation,
        )
    }

    fun continueAsGuest() = attempt { auth.continueAsGuest() }

    /**
     * The Google sheet needs an activity context to show itself, so the screen
     * hands one in rather than the view model holding onto one.
     */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }

            when (val result = google.requestIdToken(context)) {
                is GoogleResult.Token -> {
                    when (val outcome = auth.signInWithGoogle(result.idToken)) {
                        is Outcome.Ok -> lock.reset()
                        is Outcome.Err -> showError(outcome.error)
                    }

                    _state.update { it.copy(busy = false) }
                }

                GoogleResult.Cancelled -> _state.update { it.copy(busy = false) }

                GoogleResult.NotConfigured -> _state.update {
                    it.copy(
                        busy = false,
                        googleAvailable = false,
                        error = "Google sign-in is not set up in this build.",
                    )
                }

                is GoogleResult.Failed -> _state.update {
                    it.copy(busy = false, error = result.message)
                }
            }
        }
    }

    private fun attempt(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, fieldErrors = emptyMap()) }

            when (val outcome = block()) {
                is Outcome.Ok -> {
                    lock.reset()
                    _state.update { it.copy(busy = false) }
                }

                is Outcome.Err -> {
                    showError(outcome.error)
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    /**
     * A 422 puts its messages under the fields that caused them; anything else
     * is a banner, because it is not about something the user typed.
     */
    private fun showError(error: ApiException) {
        _state.update { current ->
            current.copy(
                error = if (error.isValidation) null else error.message,
                fieldErrors = error.errors.mapValues { (_, messages) -> messages.first() },
            )
        }
    }
}
