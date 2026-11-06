package com.budgettracker.data.remote

import com.budgettracker.core.network.ApiJson
import com.budgettracker.data.model.TransactionFilters
import com.budgettracker.data.repository.LedgerEvents
import com.budgettracker.data.repository.TransactionRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Laravel's `boolean` rule accepts `1` and `0` but rejects the strings
 * `"true"` and `"false"`, which is what Retrofit sends by default. Getting
 * this wrong turns every flagged query into a 422 that only shows up against
 * a real server, so it is pinned here.
 */
class BooleanQueryConverterTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }

        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(BooleanQueryConverter)
            .addConverterFactory(
                ApiJson.instance.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(BudgetApi::class.java)

        repository = TransactionRepository(api, LedgerEvents())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `boolean query flags are sent as 1 and 0, not true and false`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[],"meta":{}}"""))

        repository.list(
            TransactionFilters(hasReceipt = true, uncategorized = false),
        )

        val query = server.takeRequest().requestUrl!!

        assertTrue(
            "collapse_transfers should be 1, was ${query.queryParameter("collapse_transfers")}",
            query.queryParameter("collapse_transfers") == "1",
        )
        assertTrue(
            "has_receipt should be 1, was ${query.queryParameter("has_receipt")}",
            query.queryParameter("has_receipt") == "1",
        )
        assertTrue(
            "uncategorized should be 0, was ${query.queryParameter("uncategorized")}",
            query.queryParameter("uncategorized") == "0",
        )
    }

    @Test
    fun `a flag that is not set is left out of the query entirely`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[],"meta":{}}"""))

        repository.list(TransactionFilters())

        val query = server.takeRequest().requestUrl!!

        assertTrue(query.queryParameter("has_receipt") == null)
        assertTrue(query.queryParameter("uncategorized") == null)
    }

    @Test
    fun `list filters travel as the array syntax Laravel expects`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[],"meta":{}}"""))

        repository.list(TransactionFilters(accountIds = listOf(3L, 7L)))

        val query = server.takeRequest().requestUrl!!

        assertTrue(
            "expected repeated account_id[] params, got ${query.query}",
            query.queryParameterValues("account_id[]") == listOf("3", "7"),
        )
    }
}
