package com.budgettracker.data.remote

import com.budgettracker.data.session.SessionStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broadcasts that the server has rejected our token. Something has to notice
 * from outside the Retrofit call that made it — an expired token can surface
 * on any request — so the session is cleared once, centrally, rather than by
 * every view model that happens to see a 401.
 */
@Singleton
class SessionExpiry @Inject constructor() {

    private val _expired = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val expired: SharedFlow<Unit> = _expired

    fun signal() {
        _expired.tryEmit(Unit)
    }
}

/**
 * Attaches the bearer token and the headers the API expects on every call.
 *
 * `Accept: application/json` matters: without it Laravel answers a validation
 * failure with a redirect instead of the 422 body the client parses.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val session: SessionStore,
    private val expiry: SessionExpiry,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.tokenBlocking()

        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

        val response = chain.proceed(request)

        if (response.code == 401 && token != null) {
            session.forgetTokenInMemory()
            expiry.signal()
        }

        return response
    }
}
