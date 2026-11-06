package com.budgettracker.data.model

import com.budgettracker.core.network.ApiJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The updater's one decision: given what the website published and what is
 * installed, does this person get a prompt, and can they refuse it?
 *
 * Worth pinning down here rather than on a device because the cases that go
 * wrong are the awkward ones — a skip that has to be overridden, a manifest
 * that has gone backwards — and none of them are reachable by tapping around.
 */
class UpdateVerdictTest {

    private fun release(
        versionCode: Int,
        minimumVersionCode: Int = 0,
    ) = AppRelease(
        versionCode = versionCode,
        versionName = "1.$versionCode.0",
        minimumVersionCode = minimumVersionCode,
        downloadUrl = "budget-tracker-1.$versionCode.0.apk",
    )

    @Test
    fun `the same build is up to date`() {
        assertEquals(
            UpdateVerdict.UpToDate,
            decideUpdate(release(versionCode = 4), installedVersionCode = 4),
        )
    }

    @Test
    fun `a newer build is offered`() {
        val verdict = decideUpdate(release(versionCode = 5), installedVersionCode = 4)

        assertTrue(verdict is UpdateVerdict.Available)
        assertFalse((verdict as UpdateVerdict.Available).required)
    }

    @Test
    fun `a manifest that has gone backwards is not an update`() {
        // A rolled-back release, or a stale manifest cached by a proxy.
        // Offering it would walk someone's install backwards.
        assertEquals(
            UpdateVerdict.UpToDate,
            decideUpdate(release(versionCode = 3), installedVersionCode = 4),
        )
    }

    @Test
    fun `a skipped version is not offered again`() {
        assertEquals(
            UpdateVerdict.Skipped,
            decideUpdate(release(versionCode = 5), installedVersionCode = 4, skippedVersionCode = 5),
        )
    }

    @Test
    fun `skipping one version does not hide the next`() {
        val verdict = decideUpdate(
            release(versionCode = 6),
            installedVersionCode = 4,
            skippedVersionCode = 5,
        )

        assertTrue(verdict is UpdateVerdict.Available)
    }

    @Test
    fun `an install below the minimum must update, even having skipped it`() {
        val verdict = decideUpdate(
            release(versionCode = 5, minimumVersionCode = 5),
            installedVersionCode = 4,
            skippedVersionCode = 5,
        )

        assertTrue(verdict is UpdateVerdict.Available)
        assertTrue((verdict as UpdateVerdict.Available).required)
    }

    @Test
    fun `an install at the minimum is not forced`() {
        val verdict = decideUpdate(
            release(versionCode = 6, minimumVersionCode = 4),
            installedVersionCode = 4,
        )

        assertFalse((verdict as UpdateVerdict.Available).required)
    }

    @Test
    fun `a manifest parses with only the fields a release has to carry`() {
        val release = ApiJson.instance.decodeFromString<AppRelease>(
            """
            {
              "version_code": 2,
              "version_name": "1.1.0",
              "download_url": "budget-tracker-1.1.0.apk"
            }
            """.trimIndent(),
        )

        assertEquals(2, release.versionCode)
        assertEquals(0, release.minimumVersionCode)
        assertNull(release.sha256)
        assertNull(release.readableSize)
        assertTrue(release.notes.isEmpty())
    }

    @Test
    fun `a published size is shown in megabytes`() {
        val release = AppRelease(
            versionCode = 2,
            versionName = "1.1.0",
            downloadUrl = "budget-tracker-1.1.0.apk",
            sizeBytes = 67_219_005,
        )

        assertEquals("64.1 MB", release.readableSize)
    }
}
