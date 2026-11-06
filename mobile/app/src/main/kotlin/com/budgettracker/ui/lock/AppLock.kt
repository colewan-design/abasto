package com.budgettracker.ui.lock

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.budgettracker.ui.components.IconBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is currently locked.
 *
 * Kept outside the composition because it has to survive the activity being
 * recreated — a rotation is not an unlock. The lock is set when the app leaves
 * the foreground, so coming back always asks.
 */
@Singleton
class AppLockManager @Inject constructor() {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Called when the app goes to the background while the lock is enabled. */
    fun lock() {
        _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    /** A fresh sign-in starts unlocked; the credential was just presented. */
    fun reset() {
        _locked.value = false
    }
}

/** Whether this device can actually satisfy the lock. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

private val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Wraps the app in the unlock screen while it is locked.
 *
 * The prompt is offered immediately and can be retried, and the screen behind
 * it is not composed at all — a locked app should not be showing balances
 * under a dialog.
 */
@Composable
fun BiometricGate(
    enabled: Boolean,
    lock: AppLockManager,
    locked: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lock.lock() }
    }

    if (!enabled || !locked) {
        content()
        return
    }

    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    var failure by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    fun prompt() {
        if (activity == null || prompting) return

        prompting = true

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                failure = null
                lock.unlock()
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                prompting = false

                // A cancel is the person choosing to stay locked, not an error
                // worth shouting about.
                failure = when (code) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED,
                    -> null

                    else -> message.toString()
                }
            }

            override fun onAuthenticationFailed() {
                failure = "That did not match. Try again."
            }
        }

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Abasto")
                .setSubtitle("Your records are locked on this device")
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build(),
        )
    }

    LaunchedEffect(Unit) { prompt() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconBadge(
                icon = Icons.Outlined.Lock,
                tint = MaterialTheme.colorScheme.primary,
                size = 64.dp,
            )

            Text(
                text = "Abasto is locked",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = failure ?: "Unlock with your fingerprint, face or device PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            Button(
                onClick = { prompt() },
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text("Unlock")
            }
        }
    }
}
