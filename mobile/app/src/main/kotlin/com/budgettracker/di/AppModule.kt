package com.budgettracker.di

import com.budgettracker.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for state no screen owns. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun deviceInfo(): DeviceInfo = DeviceInfo()
}

/**
 * How this install identifies itself. The device name is what shows up in the
 * account's token list, so signing out of one phone from another is possible.
 */
class DeviceInfo {

    val name: String =
        listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
            .take(60)
            .ifBlank { "Android" }

    val appVersion: String = BuildConfig.VERSION_NAME

    val googleClientId: String? = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }
}
