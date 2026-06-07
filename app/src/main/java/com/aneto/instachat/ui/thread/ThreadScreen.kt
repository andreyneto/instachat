package com.aneto.instachat.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aneto.instachat.R
import com.aneto.instachat.domain.model.CarouselEntry
import com.aneto.instachat.domain.model.MediaOrigin
import com.aneto.instachat.domain.model.Message
import com.aneto.instachat.ui.inbox.Avatar
import com.aneto.instachat.ui.inbox.GroupAvatar
import com.aneto.instachat.ui.preview.PreviewSamples
import com.aneto.instachat.ui.preview.PreviewTheme
import com.aneto.instachat.ui.thread.components.Composer
import com.aneto.instachat.ui.thread.components.MessageBubble
import com.aneto.instachat.ui.thread.components.ReactionSheet
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ThreadScreen(
    threadId: String,
    onBack: () -> Unit,
    onOpenMedia: (List<CarouselEntry>, Int, MediaOrigin?) -> Unit,
    onSessionLost: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reactingTo by remember { mutableStateOf<Message?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.startPolling() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.stopPolling() }

    LaunchedEffect(state.sessionLost) {
        if (state.sessionLost) onSessionLost()
    }

    LaunchedEffect(state.messages.firstOrNull()?.id) {
        state.messages.firstOrNull()?.let { viewModel.markLastSeen() }
    }

    ThreadContent(
        state = state,
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        onSend = { viewModel.send(it) },
        onLongPressMessage = { reactingTo = it },
        onReachOlderEdge = { viewModel.loadOlder() },
    )

    reactingTo?.let { target ->
        ReactionSheet(
            onDismiss = { reactingTo = null },
            onPick = { emoji ->
                viewModel.react(target.id, emoji)
                reactingTo = null
            },
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class,
)
@Composable
fun ThreadContent(
    state: ThreadUiState,
    onBack: () -> Unit,
    onOpenMedia: (List<CarouselEntry>, Int, MediaOrigin?) -> Unit,
    onSend: (String) -> Unit,
    onLongPressMessage: (Message) -> Unit,
    onReachOlderEdge: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    listPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
) {
    // Scroll to the bottom (item 0 under reverseLayout) when the viewer sends a message.
    // O último id tratado sobrevive à navegação (ex.: media viewer) para não
    // re-rolar ao voltar — o que também quebraria a transição hero de volta.
    val newestId = state.messages.firstOrNull()?.id
    var lastHandledNewestId by rememberSaveable { mutableStateOf(newestId) }
    LaunchedEffect(newestId) {
        val newest = state.messages.firstOrNull()
        if (newestId != lastHandledNewestId && newest != null && newest.senderId == state.viewerId) {
            listState.animateScrollToItem(0)
        }
        lastHandledNewestId = newestId
    }

    LaunchedEffect(listState, state.hasOlder, state.messages.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val total = state.messages.size
                if (lastVisible != null && state.hasOlder && lastVisible >= total - 3) {
                    onReachOlderEdge()
                }
            }
    }

    val hazeState = rememberHazeState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ThreadTopTitle(state) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin()),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val density = LocalDensity.current
        var composerHeight by remember { mutableStateOf(0.dp) }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.loading && state.messages.isEmpty()) {
                LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(96.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding() + listPadding.calculateTopPadding(),
                        bottom = listPadding.calculateBottomPadding() + composerHeight,
                    ),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val prev = state.messages.indexOf(message).let { i ->
                            state.messages.getOrNull(i + 1)
                        }
                        val next = state.messages.indexOf(message).let { i ->
                            state.messages.getOrNull(i - 1)
                        }
                        val isMine = message.senderId == state.viewerId
                        val prevFromSame = prev?.senderId == message.senderId
                        val nextFromSame = next?.senderId == message.senderId
                        val sender = if (state.isGroup && !isMine) {
                            state.users.firstOrNull { it.id == message.senderId }
                        } else {
                            null
                        }
                        val senderName = sender?.fullName?.takeIf { it.isNotBlank() }
                            ?: sender?.username?.takeIf { it.isNotBlank() }
                        MessageBubble(
                            message = message,
                            isMine = isMine,
                            isFirstInGroup = !prevFromSame,
                            isLastInGroup = !nextFromSame,
                            onLongPress = { onLongPressMessage(message) },
                            onOpenMedia = onOpenMedia,
                            senderName = senderName,
                            senderAvatarUrl = sender?.profilePicUrl,
                            senderAvatarUserId = sender?.id,
                            showSenderAvatarSlot = state.isGroup && !isMine,
                        )
                    }
                    if (state.hasOlder) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }

            Composer(
                enabled = !state.sending,
                onSend = onSend,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        composerHeight = with(density) { size.height.toDp() }
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ThreadTopTitle(state: ThreadUiState, avatarSize: Int = 36) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isGroup && state.users.size >= 2) {
            GroupAvatar(users = state.users, size = avatarSize)
        } else {
            Avatar(url = state.avatarUrl, size = avatarSize, cacheKey = state.avatarUserId)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = state.title.ifBlank { stringResource(R.string.thread_title_fallback) },
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.subtitle.isNotBlank()) {
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(name = "Thread · loaded", showBackground = true, heightDp = 780)
@Composable
private fun PreviewThreadLoaded() {
    PreviewTheme {
        ThreadContent(
            state = PreviewSamples.threadLoaded,
            onBack = {},
            onOpenMedia = { _, _, _ -> },
            onSend = {},
            onLongPressMessage = {},
            onReachOlderEdge = {},
        )
    }
}

@Preview(name = "Thread · loaded (dark)", showBackground = true, heightDp = 780)
@Composable
private fun PreviewThreadLoadedDark() {
    PreviewTheme(dark = true) {
        ThreadContent(
            state = PreviewSamples.threadLoaded,
            onBack = {},
            onOpenMedia = { _, _, _ -> },
            onSend = {},
            onLongPressMessage = {},
            onReachOlderEdge = {},
        )
    }
}

@Preview(name = "Thread · loading", showBackground = true, heightDp = 600)
@Composable
private fun PreviewThreadLoading() {
    PreviewTheme {
        ThreadContent(
            state = PreviewSamples.threadLoading,
            onBack = {},
            onOpenMedia = { _, _, _ -> },
            onSend = {},
            onLongPressMessage = {},
            onReachOlderEdge = {},
        )
    }
}

@Preview(name = "Thread · vazia", showBackground = true, heightDp = 600)
@Composable
private fun PreviewThreadEmpty() {
    PreviewTheme {
        ThreadContent(
            state = PreviewSamples.threadEmpty,
            onBack = {},
            onOpenMedia = { _, _, _ -> },
            onSend = {},
            onLongPressMessage = {},
            onReachOlderEdge = {},
        )
    }
}

@Preview(name = "Thread · sending", showBackground = true, heightDp = 780)
@Composable
private fun PreviewThreadSending() {
    PreviewTheme {
        ThreadContent(
            state = PreviewSamples.threadLoaded.copy(sending = true),
            onBack = {},
            onOpenMedia = { _, _, _ -> },
            onSend = {},
            onLongPressMessage = {},
            onReachOlderEdge = {},
        )
    }
}

@Preview(name = "ThreadTopTitle", showBackground = true)
@Composable
private fun PreviewThreadTopTitle() {
    PreviewTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            ThreadTopTitle(state = PreviewSamples.threadLoaded)
        }
    }
}

@Preview(name = "ThreadTopTitle · group", showBackground = true)
@Composable
private fun PreviewThreadTopTitleGroup() {
    PreviewTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            ThreadTopTitle(state = PreviewSamples.threadLoadedGroup)
        }
    }
}

@Preview(name = "Thread · group", showBackground = true, heightDp = 780)
@Composable
private fun PreviewThreadLoadedGroup() {
    PreviewTheme {
        ThreadContent(
            state = PreviewSamples.threadLoadedGroup,
            onBack = {},
            onOpenMedia = { _, _, _ -> },
            onSend = {},
            onLongPressMessage = {},
            onReachOlderEdge = {},
        )
    }
}
