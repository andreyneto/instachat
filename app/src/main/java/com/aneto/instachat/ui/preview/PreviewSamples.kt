package com.aneto.instachat.ui.preview

import com.aneto.instachat.domain.model.CarouselEntry
import com.aneto.instachat.domain.model.MediaItem
import com.aneto.instachat.domain.model.Message
import com.aneto.instachat.domain.model.Reaction
import com.aneto.instachat.domain.model.Thread
import com.aneto.instachat.domain.model.User
import com.aneto.instachat.ui.inbox.InboxUiState
import com.aneto.instachat.ui.thread.ThreadUiState

/**
 * Mock data for @Preview and local UI showcases. All media is loaded from free,
 * royalty-free placeholder services (avatars: pravatar.cc, images: picsum.photos,
 * video: Big Buck Bunny). Image loading is disabled inside @Preview, so URLs only
 * resolve when this data is rendered on a real device.
 */
object PreviewSamples {

    const val VIEWER_ID = "viewer-1"

    private fun avatar(seed: Int) = "https://i.pravatar.cc/150?img=$seed"
    private fun photo(seed: String, w: Int = 1080, h: Int = 1080) = "https://picsum.photos/seed/$seed/$w/$h"

    /** Epoch micros for "[ms] ago", so relative timestamps stay fresh on each render. */
    private fun ago(ms: Long) = (System.currentTimeMillis() - ms) * 1000L

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    val userLuna = User("u-luna", "luna.rivers", "Luna Rivers", avatar(5))
    val userTheo = User("u-theo", "theo.brooks", "Theo Brooks", avatar(12))
    val userAna = User("u-ana", "ana.flores", "Ana Flores", avatar(9))
    val userMarcus = User("u-marcus", "marcus.lee", "Marcus Lee", avatar(33))
    val userPriya = User("u-priya", "priya.kapoor", "Priya Kapoor", avatar(44))

    val userGroup = listOf(userLuna, userTheo, userAna)

    fun thread(
        id: String = "t-1",
        title: String = userLuna.fullName.orEmpty(),
        users: List<User> = listOf(userLuna),
        lastPreview: String = "Hey! how are you?",
        lastActivityAt: Long = ago(2 * MINUTE),
        unread: Boolean = false,
        isGroup: Boolean = false,
    ) = Thread(
        id = id,
        title = title,
        users = users,
        lastPreview = lastPreview,
        lastActivityAt = lastActivityAt,
        unread = unread,
        isGroup = isGroup,
    )

    val threadsSample = listOf(
        thread(
            id = "t-1",
            title = "Luna Rivers",
            users = listOf(userLuna),
            lastPreview = "found a cozy café near my place — want to go Saturday? 🌿",
            lastActivityAt = ago(40_000L),
            unread = true,
        ),
        thread(
            id = "t-2",
            title = "Theo Brooks",
            users = listOf(userTheo),
            lastPreview = "🎬 Video",
            lastActivityAt = ago(18 * MINUTE),
            unread = true,
        ),
        thread(
            id = "t-3",
            title = "book club",
            users = userGroup,
            lastPreview = "Marcus: did anyone finish chapter 4?",
            lastActivityAt = ago(2 * HOUR),
            unread = false,
            isGroup = true,
        ),
        thread(
            id = "t-4",
            title = "Ana Flores",
            users = listOf(userAna),
            lastPreview = "📷 Photo",
            lastActivityAt = ago(5 * HOUR),
            unread = false,
        ),
        thread(
            id = "t-5",
            title = "Marcus Lee",
            users = listOf(userMarcus),
            lastPreview = "🎤 Voice message",
            lastActivityAt = ago(2 * DAY),
            unread = false,
        ),
        thread(
            id = "t-6",
            title = "Priya Kapoor",
            users = listOf(userPriya),
            lastPreview = "",
            lastActivityAt = ago(9 * DAY),
            unread = false,
        ),
    )

    val inboxLoaded = InboxUiState(loading = false, threads = threadsSample)
    val inboxLoading = InboxUiState(loading = true, threads = emptyList())
    val inboxEmpty = InboxUiState(loading = false, threads = emptyList())

    fun message(
        id: String = "m-1",
        senderId: String = VIEWER_ID,
        content: MediaItem = MediaItem.Text("Sample message"),
        reactions: List<Reaction> = emptyList(),
        ageMillis: Long = MINUTE,
    ): Message = Message(
        id = id,
        senderId = senderId,
        timestampMicros = ago(ageMillis),
        content = content,
        reactions = reactions,
    )

    val carouselEntries = listOf(
        CarouselEntry(url = photo("carousel-a", 1080, 1350), posterUrl = null, isVideo = false),
        CarouselEntry(url = photo("carousel-b", 1080, 1350), posterUrl = null, isVideo = true),
        CarouselEntry(url = photo("carousel-c", 1080, 1350), posterUrl = null, isVideo = false),
    )

    val messagesSample = listOf(
        message(
            id = "m-6",
            senderId = VIEWER_ID,
            content = MediaItem.Text("perfect, let's do Saturday 🙌"),
            ageMillis = 30_000L,
        ),
        message(
            id = "m-5",
            senderId = userLuna.id,
            content = MediaItem.Text("found a cozy café near my place — want to check it out this weekend?"),
            reactions = listOf(Reaction(VIEWER_ID, "❤")),
            ageMillis = 3 * MINUTE,
        ),
        message(
            id = "m-4",
            senderId = userLuna.id,
            content = MediaItem.Video(
                url = "https://www.w3schools.com/html/mov_bbb.mp4",
                posterUrl = photo("video-poster", 720, 1280),
            ),
            ageMillis = 8 * MINUTE,
        ),
        message(
            id = "m-3",
            senderId = userLuna.id,
            content = MediaItem.Photo(photo("luna-photo", 1080, 1350), 1080, 1350),
            ageMillis = 12 * MINUTE,
        ),
        message(
            id = "m-2",
            senderId = VIEWER_ID,
            content = MediaItem.Text("haha that's amazing 😂"),
            ageMillis = 20 * MINUTE,
        ),
        message(
            id = "m-1",
            senderId = userLuna.id,
            content = MediaItem.Text("this cat playing piano is the best thing on the internet today 🐈🎹"),
            ageMillis = 25 * MINUTE,
        ),
    )

    val threadLoaded = ThreadUiState(
        loading = false,
        title = userLuna.fullName.orEmpty(),
        subtitle = "@${userLuna.username}",
        avatarUrl = userLuna.profilePicUrl,
        avatarUserId = userLuna.id,
        viewerId = VIEWER_ID,
        messages = messagesSample,
        hasOlder = true,
        sending = false,
    )

    val threadLoadedGroup = ThreadUiState(
        loading = false,
        title = "book club",
        subtitle = "",
        avatarUrl = null,
        avatarUserId = userLuna.id,
        isGroup = true,
        users = userGroup,
        viewerId = VIEWER_ID,
        messages = messagesSample,
        hasOlder = false,
        sending = false,
    )

    val threadLoading = ThreadUiState(
        loading = true,
        viewerId = VIEWER_ID,
    )

    val threadEmpty = ThreadUiState(
        loading = false,
        title = "New chat",
        subtitle = "@theo.brooks",
        viewerId = VIEWER_ID,
    )
}
