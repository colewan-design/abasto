package com.budgettracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.budgettracker.BuildConfig
import com.budgettracker.core.network.ApiJson
import com.budgettracker.data.remote.AuthInterceptor
import com.budgettracker.data.remote.BooleanQueryConverter
import com.budgettracker.data.remote.BudgetApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("session") },
        )

    /**
     * Receipt uploads are the slow call here — a photo over a phone connection
     * — so the write and read timeouts are generous enough to survive one
     * without the user having to retake the shot.
     */
    @Provides
    @Singleton
    fun okHttpClient(auth: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            // Headers and timings only. BODY would put bearer
                            // tokens and every amount into logcat.
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            // Registered first so it claims booleans before the JSON
            // converter is consulted for query values.
            .addConverterFactory(BooleanQueryConverter)
            .addConverterFactory(ApiJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun budgetApi(retrofit: Retrofit): BudgetApi = retrofit.create(BudgetApi::class.java)
}
