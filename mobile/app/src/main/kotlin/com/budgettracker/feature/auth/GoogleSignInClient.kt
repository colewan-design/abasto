package com.budgettracker.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.budgettracker.di.DeviceInfo
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject
import javax.inject.Singleton

/** What came back from the Google sheet. */
sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data object Cancelled : GoogleResult
    data object NotConfigured : GoogleResult
    data class Failed(val message: String) : GoogleResult
}

/**
 * Google sign-in through Credential Manager.
 *
 * The web client ID is a build setting, and a build without one simply hides
 * the Google button — the guest demo and a password account are still complete
 * ways in, so a missing credential is a configuration state rather than an
 * error to show someone.
 *
 * The ID token is verified server-side; nothing here trusts it.
 */
@Singleton
class GoogleSignInClient @Inject constructor(
    private val device: DeviceInfo,
) {

    val isConfigured: Boolean get() = device.googleClientId != null

    suspend fun requestIdToken(context: Context): GoogleResult {
        val clientId = device.googleClientId ?: return GoogleResult.NotConfigured

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()

        return try {
            val credential = CredentialManager.create(context)
                .getCredential(context, request)
                .credential

            when {
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                    GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)

                else -> GoogleResult.Failed("That sign-in method is not supported.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleResult.Failed("No Google account is available on this device.")
        } catch (failure: GetCredentialException) {
            GoogleResult.Failed(failure.message ?: "Google sign-in failed.")
        }
    }
}
