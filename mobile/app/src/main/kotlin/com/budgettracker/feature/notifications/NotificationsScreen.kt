package com.budgettracker.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.AppNotification
import com.budgettracker.data.repository.NotificationRepository
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.DetailScaffold
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val notifications: List<AppNotification> = emptyList(),
    val unread: Int = 0,
)

/**
 * Budget warnings, bill reminders and anything else the server raised.
 *
 * Opening one marks it read — the alternative is a badge that never clears
 * because nobody found the button.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifications: NotificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.notifications.isEmpty(), error = null) }

            when (val outcome = notifications.list()) {
                is Outcome.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        notifications = outcome.value.data,
                        unread = outcome.value.meta?.unread ?: 0,
                    )
                }

                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    fun markRead(notification: AppNotification) {
        if (notification.read) return

        viewModelScope.launch {
            when (notifications.markRead(notification.id)) {
                is Outcome.Ok -> _state.update { current ->
                    current.copy(
                        notifications = current.notifications.map {
                            if (it.id == notification.id) it.copy(read = true) else it
                        },
                        unread = (current.unread - 1).coerceAtLeast(0),
                    )
                }

                is Outcome.Err -> Unit
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            when (notifications.markAllRead()) {
                is Outcome.Ok -> _state.update { current ->
                    current.copy(
                        notifications = current.notifications.map { it.copy(read = true) },
                        unread = 0,
                    )
                }

                is Outcome.Err -> Unit
            }
        }
    }

    fun dismiss(notification: AppNotification) {
        viewModelScope.launch {
            when (notifications.delete(notification.id)) {
                is Outcome.Ok -> _state.update { current ->
                    current.copy(
                        notifications = current.notifications.filterNot { it.id == notification.id },
                    )
                }

                is Outcome.Err -> Unit
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = "Notifications",
        onBack = onBack,
        actions = {
            if (state.unread > 0) {
                TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") }
            }
        },
    ) { padding ->
        when {
            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            state.notifications.isEmpty() && state.error != null -> CentredBox(padding = padding) {
                ErrorState(error = state.error!!, onRetry = viewModel::load)
            }

            state.notifications.isEmpty() -> CentredBox(padding = padding) {
                EmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "Nothing to tell you",
                    message = "Budget warnings and bill reminders will show up here.",
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.notifications, key = { it.id }) { notification ->
                    SectionCard(padding = 14.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.markRead(notification) },
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notification.title ?: "Update",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (notification.read) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )

                                notification.body?.let { body ->
                                    Text(
                                        text = body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }

                                Text(
                                    text = Dates.relativeOrRaw(notification.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            IconButton(onClick = { viewModel.dismiss(notification) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
