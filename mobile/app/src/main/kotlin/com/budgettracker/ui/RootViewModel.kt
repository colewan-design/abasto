package com.budgettracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.data.repository.SessionState
import com.budgettracker.data.repository.TransactionRepository
import com.budgettracker.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns what the whole app branches on: whether there is a session, whether it
 * still needs setting up, and whether the app is locked.
 *
 * It also deletes a transaction, because the detail sheet is hosted by the
 * shell rather than by any one screen — the row can be opened from the home
 * screen or the activity list, and only one of those has a list to remove it
 * from.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val transactions: TransactionRepository,
    private val reference: ReferenceRepository,
    private val lock: AppLockManager,
) : ViewModel() {

    val session: StateFlow<SessionState> = auth.state
    val locked: StateFlow<Boolean> = lock.locked

    /** The gate needs the manager itself to unlock, not just its state. */
    val lockManager: AppLockManager get() = lock

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            // Wait for a session before asking the server anything; the stored
            // user is shown in the meantime.
            auth.state.filterIsInstance<SessionState.SignedIn>().first()

            auth.refreshProfile()
            reference.ensureLoaded()
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            _message.value = when (val outcome = transactions.delete(id)) {
                is Outcome.Ok -> "Deleted."
                is Outcome.Err -> outcome.error.message
            }
        }
    }

    fun messageShown() = _message.update { null }
}
