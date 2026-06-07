package com.aneto.instachat.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aneto.instachat.core.auth.SessionEvent
import com.aneto.instachat.core.auth.SessionStore
import com.aneto.instachat.data.direct.DirectRepository
import com.aneto.instachat.domain.model.Thread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val loading: Boolean = true,
    val threads: List<Thread> = emptyList(),
    val error: String? = null,
    val sessionLost: Boolean = false,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: DirectRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private val locallySeenAt = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            sessionStore.events.collect { event ->
                if (event is SessionEvent.Invalidated) {
                    _state.value = _state.value.copy(sessionLost = true)
                    pollJob?.cancel()
                }
            }
        }
        viewModelScope.launch {
            repository.seenEvents.collect { threadId ->
                val current = _state.value.threads.firstOrNull { it.id == threadId }
                if (current != null) {
                    locallySeenAt[threadId] = current.lastActivityAt
                }
                _state.value = _state.value.copy(
                    threads = _state.value.threads.map {
                        if (it.id == threadId && it.unread) it.copy(unread = false) else it
                    },
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { fetch() }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                fetch()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun fetch() {
        runCatching { repository.loadInbox() }
            .onSuccess { threads ->
                locallySeenAt.entries.removeAll { (id, seenAt) ->
                    val fresh = threads.firstOrNull { it.id == id } ?: return@removeAll true
                    fresh.lastActivityAt > seenAt
                }
                val reconciled = threads.map { t ->
                    if (locallySeenAt.containsKey(t.id) && t.unread) t.copy(unread = false) else t
                }
                _state.value = InboxUiState(loading = false, threads = reconciled)
            }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Couldn't load chats",
                )
            }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
