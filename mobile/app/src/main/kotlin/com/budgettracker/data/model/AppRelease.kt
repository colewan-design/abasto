package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * One published Android build, as described by `downloads/android.json` on the
 * website.
 *
 * The app is distributed as a direct APK rather than through Play, so nothing
 * else is going to tell an install that it has fallen behind. The manifest sits
 * next to the APK it describes, which means publishing a release is a single
 * directory copy and the two can never point at different versions.
 *
 * [downloadUrl] is normally relative (`budget-tracker-1.1.0.apk`) and is
 * resolved against the manifest's own URL, so the same file works on localhost,
 * a LAN address and a real domain without being rewritten.
 */
@Serializable
data class AppRelease(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,

    /**
     * Installs older than this cannot be left alone — the prompt they get has
     * no way out. Reserve it for builds that genuinely stopped working, such as
     * one that talks to an API route that no longer exists.
     */
    @SerialName("minimum_version_code") val minimumVersionCode: Int = 0,

    @SerialName("download_url") val downloadUrl: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,

    /**
     * Lowercase hex digest of the APK. Checked after downloading, before the
     * installer is handed the file: this build is sideloaded over plain HTTP in
     * development, and an APK is the last thing that should be installed on the
     * strength of "the download finished".
     */
    val sha256: String? = null,

    @SerialName("released_at") val releasedAt: String? = null,
    val notes: List<String> = emptyList(),
) {
    /**
     * `64.1 MB`, or null when the manifest did not record a size.
     *
     * Fixed to a decimal point rather than the device's separator: this sits
     * next to a version number, and the two reading differently on a comma
     * locale is worse than the one being unlocalised.
     */
    val readableSize: String?
        get() = sizeBytes
            .takeIf { it > 0 }
            ?.let { String.format(Locale.US, "%.1f MB", it / 1_048_576.0) }
}

/** What an install should do about a published release. */
sealed interface UpdateVerdict {

    /** Nothing newer is published. */
    data object UpToDate : UpdateVerdict

    /** Newer, but the person asked not to be told about this one again. */
    data object Skipped : UpdateVerdict

    data class Available(val release: AppRelease, val required: Boolean) : UpdateVerdict
}

/**
 * Compares a published release against what is installed.
 *
 * Kept as a pure function because it is the whole decision — everything else in
 * the updater is plumbing — and because the interesting cases (a skip that must
 * be overridden, a manifest that has gone backwards) are easier to pin down in
 * a test than to reproduce on a device.
 */
fun decideUpdate(
    release: AppRelease,
    installedVersionCode: Int,
    skippedVersionCode: Int = 0,
): UpdateVerdict = when {
    release.versionCode <= installedVersionCode -> UpdateVerdict.UpToDate

    // A required update outranks a skip: the person said no to being nagged,
    // not to an install that no longer works.
    installedVersionCode < release.minimumVersionCode ->
        UpdateVerdict.Available(release, required = true)

    release.versionCode == skippedVersionCode -> UpdateVerdict.Skipped

    else -> UpdateVerdict.Available(release, required = false)
}
