package com.budgettracker.data.remote

import com.budgettracker.data.model.AppRelease
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * The website's release manifest and the APK it points at.
 *
 * Deliberately separate from [BudgetApi]: these two calls go to wherever the
 * download page is hosted rather than to the API, and they are the only
 * requests in the app that must *not* carry the bearer token. Giving them
 * their own interface makes that boundary something you can see rather than
 * something you have to remember.
 *
 * Both take a full URL because the manifest's `download_url` is resolved at
 * runtime against the manifest's own location.
 */
interface ReleaseApi {

    @GET
    suspend fun manifest(@Url url: String): AppRelease

    /**
     * `@Streaming` keeps the APK out of memory — without it Retrofit buffers
     * the whole ~70 MB body before returning, and there is nothing to report
     * progress against.
     */
    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}
