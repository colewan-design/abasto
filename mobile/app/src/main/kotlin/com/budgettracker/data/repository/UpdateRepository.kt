package com.budgettracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.budgettracker.BuildConfig
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.data.model.AppRelease
import com.budgettracker.data.model.UpdateVerdict
import com.budgettracker.data.model.decideUpdate
import com.budgettracker.data.remote.ReleaseApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A release that is newer than this install, and whether it can be put off. */
data class PendingUpdate(
    val release: AppRelease,
    val required: Boolean,
)

/** Where the updater is, from the check through to a file ready to install. */
sealed interface UpdateStatus {

    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    data object UpToDate : UpdateStatus

    data class Available(val update: PendingUpdate) : UpdateStatus

    data class Downloading(
        val update: PendingUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateStatus {
        /** Null while the server has not said how big the file is. */
        val fraction: Float?
            get() = totalBytes.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    data class ReadyToInstall(val update: PendingUpdate, val file: File) : UpdateStatus

    data class Failed(val update: PendingUpdate?, val message: String) : UpdateStatus

    /**
     * The release this state is about, where there is one.
     *
     * Named apart from the `update` each state carries so the two are never
     * confused: this one answers "is anything in flight at all".
     */
    val pending: PendingUpdate?
        get() = when (this) {
            is Available -> update
            is Downloading -> update
            is ReadyToInstall -> update
            is Failed -> update
            Idle, Checking, UpToDate -> null
        }
}

/**
 * Keeps this install in step with what the website is handing out.
 *
 * The app ships as a direct APK, so there is no store to notice a new build
 * and no update to install in the background — the whole cycle is: read the
 * manifest, compare version codes, download when the person says so, hand the
 * file to the system installer.
 *
 * A singleton because the prompt on the main screen and the card in settings
 * are two views of one process. Starting a download from settings and then
 * navigating away has to keep going, and must not start a second one.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val releases: ReleaseApi,
    private val dataStore: DataStore<Preferences>,
) {

    private val keySkipped = intPreferencesKey("update_skipped_version_code")

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Serialises check and download so two entry points cannot overlap. */
    private val gate = Mutex()

    /**
     * The updater's work outlives any one screen. A download started from
     * settings has to survive navigating away from it, so it cannot run in a
     * view model's scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val installedVersionCode: Int = BuildConfig.VERSION_CODE
    val installedVersionName: String = BuildConfig.VERSION_NAME

    /**
     * The check made once per launch.
     *
     * Does nothing if anything has happened already, so returning to the main
     * screen — or opening settings — does not re-check on every recomposition.
     */
    fun checkOnLaunch() {
        if (_status.value != UpdateStatus.Idle) return

        check(manual = false)
    }

    fun check(manual: Boolean) {
        scope.launch { runCheck(manual) }
    }

    fun download() {
        scope.launch { runDownload() }
    }

    /** Stops this version being offered again until it is checked for by hand. */
    fun skip() {
        scope.launch { runSkip() }
    }

    /** Puts the prompt away for this run. The next launch asks again. */
    fun dismiss() {
        if (_status.value.pending?.required == true) return

        _status.value = UpdateStatus.Idle
    }

    /**
     * Reads the manifest and decides what this install should do about it.
     *
     * A failed automatic check is silent: being unable to reach the website is
     * not something to interrupt someone over at launch. A failed manual check
     * says so, because they asked.
     */
    private suspend fun runCheck(manual: Boolean) {
        gate.withLock {
            // A download in flight is further along than a check; leave it be.
            if (_status.value is UpdateStatus.Downloading) return@withLock

            _status.value = UpdateStatus.Checking

            when (val outcome = apiCall { releases.manifest(BuildConfig.UPDATE_MANIFEST_URL) }) {
                is Outcome.Ok -> _status.value = verdictToStatus(outcome.value)

                is Outcome.Err -> _status.value = if (manual) {
                    UpdateStatus.Failed(update = null, message = outcome.error.message)
                } else {
                    UpdateStatus.Idle
                }
            }
        }
    }

    /**
     * Fetches the APK into the cache and verifies it.
     *
     * The digest check is the point of the exercise as much as the download is:
     * what comes back is about to be handed to the package installer, and in
     * development it arrives over plain HTTP. A file that does not match the
     * manifest is deleted rather than offered.
     */
    private suspend fun runDownload() {
        gate.withLock {
            val pending = _status.value.pending ?: return@withLock
            if (_status.value is UpdateStatus.Downloading) return@withLock

            val url = resolve(pending.release.downloadUrl)
                ?: run {
                    _status.value = UpdateStatus.Failed(pending, "That download link is not valid.")
                    return@withLock
                }

            _status.value = UpdateStatus.Downloading(
                update = pending,
                downloadedBytes = 0,
                totalBytes = pending.release.sizeBytes,
            )

            _status.value = withContext(Dispatchers.IO) {
                runCatching { fetch(pending, url) }
                    .getOrElse { failure ->
                        UpdateStatus.Failed(
                            update = pending,
                            message = failure.message?.takeIf { it.isNotBlank() }
                                ?: "The download did not finish. Try again.",
                        )
                    }
            }
        }
    }

    private suspend fun runSkip() {
        val pending = _status.value.pending ?: return
        if (pending.required) return

        dataStore.edit { it[keySkipped] = pending.release.versionCode }

        _status.value = UpdateStatus.UpToDate
    }

    private suspend fun verdictToStatus(release: AppRelease): UpdateStatus {
        val skipped = dataStore.data.map { it[keySkipped] ?: 0 }.first()

        return when (val verdict = decideUpdate(release, installedVersionCode, skipped)) {
            UpdateVerdict.UpToDate, UpdateVerdict.Skipped -> UpdateStatus.UpToDate
            is UpdateVerdict.Available -> UpdateStatus.Available(
                PendingUpdate(verdict.release, verdict.required),
            )
        }
    }

    private suspend fun fetch(pending: PendingUpdate, url: String): UpdateStatus {
        val body = releases.download(url)
        val total = body.contentLength().takeIf { it > 0 } ?: pending.release.sizeBytes

        // One APK at a time: these are tens of megabytes and the cache is not
        // somewhere to leave a trail of superseded builds.
        val directory = File(context.cacheDir, UPDATE_DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }

        val target = File(directory, "budget-tracker-${pending.release.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        var lastReported = 0L

        body.byteStream().use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break

                    sink.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    written += read

                    // Reporting every chunk would push tens of thousands of
                    // states at the UI for one file.
                    if (written - lastReported >= PROGRESS_STEP_BYTES) {
                        lastReported = written
                        _status.value = UpdateStatus.Downloading(pending, written, total)
                    }
                }
            }
        }

        val expected = pending.release.sha256?.lowercase()
        val actual = digest.digest().joinToString("") { "%02x".format(it) }

        if (expected != null && expected != actual) {
            target.delete()

            return UpdateStatus.Failed(
                update = pending,
                message = "That download did not arrive intact. Try again.",
            )
        }

        return UpdateStatus.ReadyToInstall(pending, target)
    }

    /**
     * Turns the manifest's `download_url` into something fetchable.
     *
     * Relative paths are resolved against the manifest's own location, so a
     * published release does not have to know the hostname it will be served
     * from.
     */
    private fun resolve(downloadUrl: String): String? =
        BuildConfig.UPDATE_MANIFEST_URL.toHttpUrlOrNull()?.resolve(downloadUrl)?.toString()

    private companion object {
        const val UPDATE_DIRECTORY = "updates"
        const val PROGRESS_STEP_BYTES = 256L * 1024
    }
}
