package com.aneto.instachat.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aneto.instachat.core.auth.SessionEvent
import com.aneto.instachat.core.auth.SessionStore
import com.aneto.instachat.data.direct.DirectRepository
import com.aneto.instachat.domain.model.MediaItem
import com.aneto.instachat.domain.model.Message
import com.aneto.instachat.domain.model.Reaction
import com.aneto.instachat.domain.model.User
import com.aneto.instachat.ui.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreadUiState(
    val loading: Boolean = true,
    val title: String = "",
    val subtitle: String = "",
    val avatarUrl: String? = null,
    val avatarUserId: String? = null,
    val isGroup: Boolean = false,
    val users: List<User> = emptyList(),
    val viewerId: String = "",
    val messages: List<Message> = emptyList(),
    val oldestCursor: String? = null,
    val hasOlder: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val sessionLost: Boolean = false,
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DirectRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val threadId: String =
        savedStateHandle.get<String>(AppRoute.Thread.ARG_THREAD_ID).orEmpty()

    private val _state = MutableStateFlow(
        ThreadUiState(viewerId = sessionStore.userId().orEmpty()),
    )
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.events.collect { event ->
                if (event is SessionEvent.Invalidated) {
                    _state.update { it.copy(sessionLost = true) }
                    pollJob?.cancel()
                }
            }
        }
        load(initial = true)
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                load(initial = false)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun loadOlder() {
        val cursor = _state.value.oldestCursor ?: return
        if (!_state.value.hasOlder) return
        viewModelScope.launch {
            runCatching { repository.loadThread(threadId, cursor = cursor) }
                .onSuccess { page ->
                    _state.update { current ->
                        val existing = current.messages.map { it.id }.toSet()
                        val merged = current.messages + page.messages.filter { it.id !in existing }
                        current.copy(
                            messages = merged,
                            oldestCursor = page.oldestCursor,
                            hasOlder = page.hasOlder,
                        )
                    }
                }
        }
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Optimistic: show the bubble immediately; the DOM send round-trip takes a few
        // seconds and load() reconciles it once IG echoes the real message back.
        val localId = LOCAL_PREFIX + java.util.UUID.randomUUID()
        val optimistic = Message(
            id = localId,
            senderId = _state.value.viewerId,
            timestampMicros = System.currentTimeMillis() * 1000L,
            content = MediaItem.Text(clean),
            reactions = emptyList(),
        )
        _state.update { it.copy(messages = listOf(optimistic) + it.messages) }

        viewModelScope.launch {
            runCatching { repository.sendText(threadId, clean) }
                .onSuccess { load(initial = false) }
                .onFailure { error ->
                    android.util.Log.w("ThreadViewModel", "send failed", error)
                    _state.update { st ->
                        st.copy(messages = st.messages.filterNot { it.id == localId })
                    }
                }
        }
    }

    fun react(messageId: String, emoji: String) {
        val target = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val anchorText = (target.content as? MediaItem.Text)?.text
        val viewerId = _state.value.viewerId
        val previous = target.reactions

        // Optimistic: show the viewer's reaction immediately; the DOM round-trip
        // (navigate -> settle -> click -> reload) takes a few seconds in the background.
        _state.update { current ->
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id != messageId) {
                        msg
                    } else {
                        msg.copy(
                            reactions = msg.reactions.filterNot { it.senderId == viewerId } +
                                Reaction(senderId = viewerId, emoji = emoji),
                        )
                    }
                },
            )
        }

        viewModelScope.launch {
            runCatching { repository.react(threadId, messageId, emoji, anchorText) }
                .onSuccess { load(initial = false) }
                .onFailure { error ->
                    android.util.Log.w("ThreadViewModel", "react failed", error)
                    // Revert the optimistic change.
                    _state.update { current ->
                        current.copy(
                            messages = current.messages.map { msg ->
                                if (msg.id != messageId) msg else msg.copy(reactions = previous)
                            },
                        )
                    }
                }
        }
    }

    fun markLastSeen() {
        val lastId = _state.value.messages.firstOrNull()?.id ?: return
        viewModelScope.launch { repository.markSeen(threadId, lastId) }
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            runCatching { repository.loadThread(threadId) }
                .onSuccess { page ->
                    _state.update { current ->
                        val fresh = page.messages.associateBy { it.id }
                        // Keep optimistic (local) sends until IG echoes the same text back,
                        // plus any message this snapshot momentarily dropped (IG's REST read
                        // is eventually-consistent right after a send). Merge + sort by time
                        // so a message's position never jumps between snapshots.
                        val viewerTexts = page.messages
                            .filter { it.senderId == current.viewerId }
                            .mapNotNull { (it.content as? MediaItem.Text)?.text?.trim() }
                            .toSet()
                        val retained = current.messages.filter { m ->
                            when {
                                m.id in fresh -> false

                                m.id.startsWith(LOCAL_PREFIX) -> {
                                    val t = (m.content as? MediaItem.Text)?.text?.trim()
                                    t == null || t !in viewerTexts
                                }

                                else -> true
                            }
                        }
                        val merged = (page.messages + retained)
                            .sortedByDescending { it.timestampMicros }
                        val others = page.thread.users.filter { it.id != current.viewerId }
                        val counterpart = others.firstOrNull() ?: page.thread.users.firstOrNull()
                        val avatarUsers = if (page.thread.isGroup) {
                            others.ifEmpty {
                                page.thread.users
                            }
                        } else {
                            page.thread.users
                        }
                        current.copy(
                            loading = false,
                            title = page.thread.title,
                            subtitle = counterpart?.username.orEmpty().let { if (it.isNotBlank()) "@$it" else "" },
                            avatarUrl = counterpart?.profilePicUrl,
                            avatarUserId = counterpart?.id,
                            isGroup = page.thread.isGroup,
                            users = avatarUsers,
                            messages = merged,
                            oldestCursor =
                            page.oldestCursor.takeIf { current.oldestCursor == null || initial }
                                ?: current.oldestCursor,
                            hasOlder = if (initial) page.hasOlder else current.hasOlder,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message)
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
        private const val LOCAL_PREFIX = "local:"
    }
}
