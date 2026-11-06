package com.budgettracker.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces that the ledger changed.
 *
 * One write ripples outwards: recording an expense moves an account balance,
 * eats into a budget, and belongs in the activity list. Those screens each own
 * their own state, so rather than have every one of them guess when to refetch
 * — or go stale until the user pulls to refresh — the repositories that write
 * say so once, here, and the screens listening reload themselves.
 *
 * It carries no payload on purpose: a screen knows how to reload itself, and a
 * diff would be another thing to keep true.
 */
@Singleton
class LedgerEvents @Inject constructor() {

    private val _changed = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val changed: SharedFlow<Unit> = _changed

    fun signal() {
        _changed.tryEmit(Unit)
    }
}
