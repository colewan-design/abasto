package com.budgettracker.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.budgettracker.core.network.ApiJson
import com.budgettracker.data.model.AuthResponse
import com.budgettracker.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in session, kept across launches.
 *
 * The user record is cached alongside the token so the app can open straight
 * into the dashboard — with a name, a currency and the right tabs — before the
 * first network response arrives. It is refreshed from `GET /me` on resume.
 */
@Singleton
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val keyToken = stringPreferencesKey("auth_token")
    private val keyUser = stringPreferencesKey("auth_user")
    private val keyLockEnabled = booleanPreferencesKey("app_lock_enabled")

    /**
     * Read by the OkHttp interceptor on a background thread. Cached so an
     * authenticated request does not wait on disk, and kept in step with every
     * write below.
     */
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var hydrated = false

    val session: Flow<StoredSession?> = dataStore.data.map { preferences ->
        val token = preferences[keyToken] ?: return@map null
        val user = preferences[keyUser]
            ?.let { runCatching { ApiJson.instance.decodeFromString<User>(it) }.getOrNull() }
            ?: return@map null

        StoredSession(token = token, user = user, appLockEnabled = preferences[keyLockEnabled] ?: false)
    }

    suspend fun save(auth: AuthResponse) {
        cachedToken = auth.token

        dataStore.edit { preferences ->
            preferences[keyToken] = auth.token
            preferences[keyUser] = ApiJson.instance.encodeToString(auth.user)
            preferences[keyLockEnabled] = auth.user.appLockEnabled
        }
    }

    suspend fun saveUser(user: User) {
        dataStore.edit { preferences ->
            // Only overwrite the cached user when a session is actually open,
            // so a late response cannot resurrect a signed-out account.
            if (preferences[keyToken] != null) {
                preferences[keyUser] = ApiJson.instance.encodeToString(user)
                preferences[keyLockEnabled] = user.appLockEnabled
            }
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[keyLockEnabled] = enabled }
    }

    suspend fun clear() {
        cachedToken = null

        dataStore.edit { preferences ->
            preferences.remove(keyToken)
            preferences.remove(keyUser)
        }
    }

    suspend fun token(): String? = dataStore.data.first()[keyToken]

    /** For the interceptor, which has no coroutine of its own to run in. */
    fun tokenBlocking(): String? {
        if (!hydrated) {
            cachedToken = runBlocking { token() }
            hydrated = true
        }

        return cachedToken
    }

    /** Called when the server rejects the token, so nothing retries with it. */
    fun forgetTokenInMemory() {
        cachedToken = null
    }
}

data class StoredSession(
    val token: String,
    val user: User,
    val appLockEnabled: Boolean,
)
