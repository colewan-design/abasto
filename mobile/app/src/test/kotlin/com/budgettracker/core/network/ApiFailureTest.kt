package com.budgettracker.core.network

import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.LoginRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Every screen renders failures from [ApiException], so the mapping from an
 * HTTP response to that type is what decides whether a person sees the
 * server's own message or a generic shrug.
 */
class ApiFailureTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BudgetApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }

        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(
                ApiJson.instance.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(BudgetApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a successful response comes back as a value`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "token": "plain-text-token",
                      "token_type": "Bearer",
                      "user": {
                        "id": 1,
                        "name": "Ada Lovelace",
                        "email": "ada@example.com",
                        "currency": { "code": "PHP", "symbol": "₱", "exponent": 2 },
                        "onboarding_completed": true
                      },
                      "needs_onboarding": false
                    }
                    """.trimIndent(),
                ),
        )

        val outcome = apiCall { api.login(LoginRequest("ada@example.com", "secret")) }

        assertTrue(outcome is Outcome.Ok)

        val auth = (outcome as Outcome.Ok).value

        assertEquals("plain-text-token", auth.token)
        assertEquals("Ada Lovelace", auth.user.name)
        assertEquals("Ada", auth.user.firstName)
        assertEquals(2, auth.user.currency.exponent)
    }

    @Test
    fun `a validation failure keeps the per-field messages`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "message": "The given data was invalid.",
                      "errors": {
                        "email": ["Those credentials do not match our records."]
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val outcome = apiCall { api.login(LoginRequest("ada@example.com", "wrong")) }

        val error = (outcome as Outcome.Err).error

        assertTrue(error.isValidation)
        assertEquals("The given data was invalid.", error.message)
        assertEquals("Those credentials do not match our records.", error.field("email"))
        assertNull(error.field("password"))
    }

    @Test
    fun `an unauthorised response is recognisable so the session can be cleared`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))

        val error = (apiCall { api.profile() } as Outcome.Err).error

        assertTrue(error.isUnauthorized)
        assertEquals("Unauthenticated.", error.message)
    }

    @Test
    fun `a server fault falls back to a readable message, not a stack trace`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>Server Error</html>"))

        val error = (apiCall { api.profile() } as Outcome.Err).error

        assertTrue(error.isServerFault)
        assertFalse(error.isOffline)
        assertEquals("The server had a problem. Try again shortly.", error.message)
    }

    @Test
    fun `a dropped connection reads as offline, which is worth retrying`() = runTest {
        server.shutdown()

        val error = (apiCall { api.profile() } as Outcome.Err).error

        assertTrue(error.isOffline)
        assertEquals(0, error.status)
        assertEquals(ApiException.OFFLINE_MESSAGE, error.message)
    }

    @Test
    fun `unknown fields in a response do not break the client`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "data": {
                        "id": 7,
                        "name": "Ada",
                        "email": "ada@example.com",
                        "something_the_app_has_not_heard_of": true
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val outcome = apiCall { api.profile() }

        assertEquals(7L, (outcome as Outcome.Ok).value.data.id)
    }
}
