package com.aneto.instachat.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.aneto.instachat.R
import com.aneto.instachat.domain.model.Thread
import com.aneto.instachat.domain.model.User
import com.aneto.instachat.ui.preview.PreviewSamples
import com.aneto.instachat.ui.preview.PreviewTheme
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(
    onOpenThread: (String) -> Unit,
    onSessionLost: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.startPolling() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.stopPolling() }

    LaunchedEffect(state.sessionLost) {
        if (state.sessionLost) onSessionLost()
    }

    InboxContent(state = state, onOpenThread = onOpenThread)
}

@OptIn(dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class)
@Composable
fun InboxContent(
    state: InboxUiState,
    onOpenThread: (String) -> Unit,
    appBarTitle: String = "Instachat",
    appBarHeight: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Icon(
                        painter = painterResource(R.drawable.logo_instachat),
                        contentDescription = appBarTitle,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(28.dp)
                            .wrapContentWidth(),
                    )
                },
                expandedHeight = appBarHeight,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin()),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            when {
                state.loading && state.threads.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(padding)) { LoadingState() }

                state.threads.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(padding)) { EmptyState() }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    items(state.threads, key = { it.id }) { thread ->
                        ThreadListItem(
                            thread = thread,
                            onClick = { onOpenThread(thread.id) },
                        )
                    }
                    item { Spacer(Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun ThreadListItem(thread: Thread, onClick: () -> Unit, avatarSize: Int = 48) {
    val firstUser = thread.users.firstOrNull()
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        leadingContent = {
            if (thread.isGroup && thread.users.size >= 2) {
                GroupAvatar(users = thread.users, size = avatarSize)
            } else {
                Avatar(url = firstUser?.profilePicUrl, size = avatarSize, cacheKey = firstUser?.id)
            }
        },
        headlineContent = {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (thread.unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = thread.lastPreview.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                color = if (thread.unread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (thread.unread) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatRelative(thread.lastActivityAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (thread.unread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (thread.unread) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp),
                    )
                }
            }
        },
    )
}

@Composable
internal fun Avatar(url: String?, size: Int, modifier: Modifier = Modifier, cacheKey: String? = null) {
    val inPreview = LocalInspectionMode.current
    Surface(
        modifier = modifier.size(size.dp).clip(CircleShape),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (!inPreview && !url.isNullOrBlank()) {
            val key = cacheKey?.let { "avatar:$it" }
            val model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(url)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .build()
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

@Composable
internal fun GroupAvatar(users: List<User>, size: Int) {
    val inner = (size * 0.66f).toInt()
    val ring = 2
    val first = users.getOrNull(0)
    val second = users.getOrNull(1)
    Box(modifier = Modifier.size(size.dp)) {
        Avatar(
            url = first?.profilePicUrl,
            size = inner,
            cacheKey = first?.id,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size((inner + ring * 2).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                url = second?.profilePicUrl,
                size = inner,
                cacheKey = second?.id,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoadingState(label: String = stringResource(R.string.inbox_loading)) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.requiredSize(96.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyState(
    label: String = stringResource(R.string.inbox_empty),
    iconSize: Int = 44,
    surfaceSize: Int = 96,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(surfaceSize.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Rounded.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(iconSize.dp),
                )
            }
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatRelative(epochMicros: Long): String {
    if (epochMicros <= 0L) return ""
    val millis = epochMicros / 1000L
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000L -> "now"

        diff < 3_600_000L -> "${diff / 60_000L} min"

        diff < 86_400_000L -> "${diff / 3_600_000L} h"

        diff < 604_800_000L -> SimpleDateFormat("EEE", Locale.ENGLISH)
            .format(Date(millis))

        else -> SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(millis))
    }
}

@Preview(name = "Inbox · loaded", showBackground = true, heightDp = 780)
@Composable
private fun PreviewInboxLoaded() {
    PreviewTheme {
        InboxContent(state = PreviewSamples.inboxLoaded, onOpenThread = {})
    }
}

@Preview(name = "Inbox · loaded (dark)", showBackground = true, heightDp = 780)
@Composable
private fun PreviewInboxLoadedDark() {
    PreviewTheme(dark = true) {
        InboxContent(state = PreviewSamples.inboxLoaded, onOpenThread = {})
    }
}

@Preview(name = "Inbox · loading", showBackground = true, heightDp = 500)
@Composable
private fun PreviewInboxLoading() {
    PreviewTheme {
        InboxContent(state = PreviewSamples.inboxLoading, onOpenThread = {})
    }
}

@Preview(name = "Inbox · empty", showBackground = true, heightDp = 500)
@Composable
private fun PreviewInboxEmpty() {
    PreviewTheme {
        InboxContent(state = PreviewSamples.inboxEmpty, onOpenThread = {})
    }
}

@Preview(name = "ThreadListItem · read", showBackground = true)
@Composable
private fun PreviewThreadListItemRead() {
    PreviewTheme {
        ThreadListItem(thread = PreviewSamples.threadsSample[1], onClick = {})
    }
}

@Preview(name = "ThreadListItem · unread", showBackground = true)
@Composable
private fun PreviewThreadListItemUnread() {
    PreviewTheme {
        ThreadListItem(thread = PreviewSamples.threadsSample[0], onClick = {})
    }
}

@Preview(name = "ThreadListItem · group unread", showBackground = true)
@Composable
private fun PreviewThreadListItemGroup() {
    PreviewTheme {
        ThreadListItem(thread = PreviewSamples.threadsSample[2], onClick = {})
    }
}

@Preview(name = "Avatar · variantes", showBackground = true)
@Composable
private fun PreviewAvatars() {
    PreviewTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(url = null, size = 36)
            Avatar(url = null, size = 44)
            Avatar(url = null, size = 52)
            Avatar(url = null, size = 72)
        }
    }
}

@Preview(name = "GroupAvatar · variantes", showBackground = true)
@Composable
private fun PreviewGroupAvatars() {
    PreviewTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupAvatar(users = PreviewSamples.userGroup, size = 44)
            GroupAvatar(users = PreviewSamples.userGroup, size = 52)
            GroupAvatar(users = PreviewSamples.userGroup, size = 72)
        }
    }
}

@Preview(name = "LoadingState", showBackground = true, heightDp = 320)
@Composable
private fun PreviewLoadingState() {
    PreviewTheme { LoadingState() }
}

@Preview(name = "EmptyState", showBackground = true, heightDp = 360)
@Composable
private fun PreviewEmptyState() {
    PreviewTheme { EmptyState() }
}
