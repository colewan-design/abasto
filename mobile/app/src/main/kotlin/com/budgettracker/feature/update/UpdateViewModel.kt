package com.budgettracker.feature.update

import androidx.lifecycle.ViewModel
import com.budgettracker.core.update.ApkInstaller
import com.budgettracker.data.repository.UpdateRepository
import com.budgettracker.data.repository.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject

/**
 * A thin view onto [UpdateRepository].
 *
 * Deliberately holds no state of its own. The prompt over the main screen and
 * the card in settings each get their own instance, and both have to show the
 * same download — the single source of truth is the repository, which is a
 * singleton and outlives both.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val installer: ApkInstaller,
) : ViewModel() {

    val status: StateFlow<UpdateStatus> = updates.status

    val installedVersionName: String = updates.installedVersionName
    val installedVersionCode: Int = updates.installedVersionCode

    fun checkOnLaunch() = updates.checkOnLaunch()

    fun check() = updates.check(manual = true)

    fun download() = updates.download()

    fun skip() = updates.skip()

    fun dismiss() = updates.dismiss()

    fun canInstall(): Boolean = installer.canInstall()

    fun install(file: File) = installer.install(file)

    fun openInstallPermissionSettings() = installer.openInstallPermissionSettings()
}
