package com.budgettracker.di

import com.budgettracker.BuildConfig
import com.budgettracker.core.network.ApiJson
import com.budgettracker.data.remote.ReleaseApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the client and Retrofit instance used for update checks and APK downloads. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Updates

/**
 * The updater's own networking.
 *
 * It does not reuse [NetworkModule]'s client for one reason that matters: that
 * client carries [com.budgettracker.data.remote.AuthInterceptor], which
 * attaches the session's bearer token to every request it sees. The release
 * manifest and the APK are served by the website, not by the API, so sharing
 * the client would hand the account's token to a different host on every
 * launch.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    @Updates
    fun updateClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // An APK is tens of megabytes and the read timeout applies between
            // chunks, not to the transfer as a whole — but a stalled download
            // on a weak connection should still give up rather than hang.
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    @Updates
    fun updateRetrofit(@Updates client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // Every call passes an absolute `@Url`, so this is only ever the
            // base a relative download path is resolved against. Retrofit
            // still insists on one being set.
            .baseUrl(manifestBase())
            .client(client)
            .addConverterFactory(ApiJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun releaseApi(@Updates retrofit: Retrofit): ReleaseApi = retrofit.create(ReleaseApi::class.java)

    /**
     * The directory the manifest lives in. A misconfigured `UPDATE_MANIFEST_URL`
     * must not take the whole object graph down at startup, so an unparsable
     * value falls back to a URL that simply never resolves — the update check
     * then fails the way an offline check does, and the rest of the app runs.
     */
    private fun manifestBase(): HttpUrl =
        BuildConfig.UPDATE_MANIFEST_URL.toHttpUrlOrNull()?.resolve("./")
            ?: "http://localhost/".toHttpUrlOrNull()!!
}
