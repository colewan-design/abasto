package com.budgettracker.data.repository

import com.budgettracker.core.money.Currency
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.data.model.AuthResponse
import com.budgettracker.data.model.User
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.SessionExpiry
import com.budgettracker.data.remote.request.GoogleRequest
import com.budgettracker.data.remote.request.GuestRequest
import com.budgettracker.data.remote.request.LoginRequest
import com.budgettracker.data.remote.request.RegisterRequest
import com.budgettracker.data.remote.request.UpdateProfileRequest
import com.budgettracker.data.session.SessionStore
import com.budgettracker.di.ApplicationScope
import com.budgettracker.di.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the whole app branches on at startup. */
sealed interface SessionState {

    /** Reading the stored session; the splash stays up. */
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val user: User,
        val appLockEnabled: Boolean,
    ) : SessionState {
        val needsOnboarding: Boolean get() = !user.onboardingCompleted
        val currency: Currency get() = user.currency
    }
}

/**
 * Owns the session: who is signed in, and every way in or out.
 *
 * Signing in stores the token before returning, so by the time a caller sees
 * success the next request is already authenticated.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: BudgetApi,
    private val store: SessionStore,
    private val device: DeviceInfo,
    expiry: SessionExpiry,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    val state: StateFlow<SessionState> = store.session
        .map { stored ->
            when (stored) {
                null -> SessionState.SignedOut
                else -> SessionState.SignedIn(stored.user, stored.appLockEnabled)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    init {
        // A token the server has rejected is worthless; drop it and let the
        // navigation graph fall back to the welcome screen on its own.
        scope.launch {
            expiry.expired.collect { store.clear() }
        }
    }

    val user: User? get() = (state.value as? SessionState.SignedIn)?.user

    val currency: Currency get() = user?.currency ?: Currency.Default

    suspend fun signIn(email: String, password: String): Outcome<AuthResponse> =
        apiCall { api.login(LoginRequest(email.trim(), password, device.name)) }
            .also { it.store() }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        confirmation: String,
        currencyCode: String? = null,
    ): Outcome<AuthResponse> =
        apiCall {
            api.register(
                RegisterRequest(
                    name = name.trim(),
                    email = email.trim(),
                    password = password,
                    passwordConfirmation = confirmation,
                    currencyCode = currencyCode,
                    deviceName = device.name,
                ),
            )
        }.also { it.store() }

    /** Starts a throwaway account preloaded with sample data. */
    suspend fun continueAsGuest(): Outcome<AuthResponse> =
        apiCall { api.guest(GuestRequest(device.name)) }.also { it.store() }

    /**
     * Google sign-in, which doubles as the way a guest converts to a real
     * account — the demo data is discarded unless asked for.
     */
    suspend fun signInWithGoogle(idToken: String, discardDemo: Boolean = true): Outcome<AuthResponse> =
        apiCall {
            api.google(GoogleRequest(idToken = idToken, deviceName = device.name, discardDemo = discardDemo))
        }.also { it.store() }

    /**
     * Replaces the cached user after something other than a profile call
     * changed it — finishing guided setup, for one, which is what flips the
     * app out of the onboarding graph.
     */
    suspend fun cacheUser(user: User) = store.saveUser(user)

    suspend fun refreshProfile(): Outcome<User> =
        apiCall { api.profile().data }.also { outcome ->
            if (outcome is Outcome.Ok) store.saveUser(outcome.value)
        }

    suspend fun updateProfile(request: UpdateProfileRequest): Outcome<User> =
        apiCall { api.updateProfile(request).data }.also { outcome ->
            if (outcome is Outcome.Ok) store.saveUser(outcome.value)
        }

    suspend fun setAppLockEnabled(enabled: Boolean): Outcome<User> {
        // Saved locally first: the lock has to hold on this device even if the
        // request fails, otherwise the setting appears to do nothing.
        store.setAppLockEnabled(enabled)

        return updateProfile(UpdateProfileRequest(appLockEnabled = enabled))
    }

    /** Signs out locally even when the request fails; the token expires anyway. */
    suspend fun signOut(everywhere: Boolean = false): Outcome<Unit> {
        val outcome = apiCall { if (everywhere) api.logoutEverywhere() else api.logout() }

        store.clear()

        return outcome.let { if (it is Outcome.Err) it else Outcome.Ok(Unit) }
    }

    suspend fun resetDemoData(): Outcome<Unit> =
        apiCall { api.resetDemo() }.let { outcome ->
            when (outcome) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Err -> outcome
            }
        }

    suspend fun deleteAccount(): Outcome<Unit> {
        val outcome = apiCall { api.deleteProfile() }

        if (outcome is Outcome.Ok) store.clear()

        return when (outcome) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Err -> outcome
        }
    }

    private suspend fun Outcome<AuthResponse>.store() {
        if (this is Outcome.Ok) store.save(value)
    }
}
