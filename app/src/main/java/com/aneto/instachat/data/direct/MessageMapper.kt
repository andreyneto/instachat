package com.aneto.instachat.data.direct

import com.aneto.instachat.data.direct.dto.CarouselEntryDto
import com.aneto.instachat.data.direct.dto.ItemDto
import com.aneto.instachat.data.direct.dto.MediaShareDto
import com.aneto.instachat.data.direct.dto.ThreadDto
import com.aneto.instachat.data.direct.dto.UserDto
import com.aneto.instachat.domain.model.CarouselEntry
import com.aneto.instachat.domain.model.MediaItem
import com.aneto.instachat.domain.model.Message
import com.aneto.instachat.domain.model.Reaction
import com.aneto.instachat.domain.model.Thread
import com.aneto.instachat.domain.model.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object MessageMapper {

    private val prettyJson = Json { prettyPrint = true }

    private fun JsonObject.pretty(): String = prettyJson.encodeToString(JsonObject.serializer(), this)

    fun toUser(dto: UserDto): User = User(
        id = dto.idString(),
        username = dto.username.orEmpty(),
        fullName = dto.fullName,
        profilePicUrl = dto.profilePicUrl,
    )

    fun toThread(dto: ThreadDto, viewerId: String?): Thread {
        val users = dto.users.map(::toUser)
        val title = dto.threadTitle?.takeIf { it.isNotBlank() }
            ?: users.joinToString(", ") { it.fullName ?: it.username }
                .ifBlank { "Chat" }
        val lastItem = dto.items.firstOrNull()
        val preview = lastItem?.let(::previewText).orEmpty()
        val lastTs = maxOf(lastItem?.timestamp ?: 0L, dto.lastActivityAt ?: 0L)
        val viewerSeenTs = viewerId
            ?.let { parseTimestampMicros(dto.lastSeenAt[it]?.timestamp) }
            ?: 0L
        val unreadBySeenAt = lastTs > 0L && viewerSeenTs > 0L && lastTs > viewerSeenTs
        val unread = unreadBySeenAt || dto.readState != 0
        return Thread(
            id = dto.threadId,
            title = title,
            users = users,
            lastPreview = preview,
            lastActivityAt = dto.lastActivityAt ?: 0L,
            unread = unread,
            isGroup = dto.isGroup,
        )
    }

    fun toMessage(dto: ItemDto, raw: JsonObject? = null): Message {
        val reactions = dto.reactions?.emojis.orEmpty().map {
            Reaction(senderId = it.senderId.toString(), emoji = it.emoji)
        } + dto.reactions?.likes.orEmpty().map {
            Reaction(senderId = it.senderId.toString(), emoji = "❤")
        }
        return Message(
            id = dto.itemId,
            senderId = dto.userId?.toString().orEmpty(),
            timestampMicros = dto.timestamp ?: 0L,
            content = toMediaItem(dto, raw),
            reactions = reactions,
        )
    }

    private fun toMediaItem(dto: ItemDto, raw: JsonObject? = null): MediaItem = when (dto.itemType) {
        "text", "link" -> MediaItem.Text(dto.text.orEmpty())

        "action_log" -> MediaItem.SystemEvent(
            text = dto.actionLog?.description
                ?: dto.text
                ?: "System event",
        )

        "placeholder" -> MediaItem.SystemEvent(
            text = dto.text ?: "Message unavailable",
            rawJson = raw?.pretty(),
        )

        "media" -> when (dto.media?.mediaType) {
            1 -> MediaItem.Photo(
                url = dto.media.imageVersions2?.candidates?.firstOrNull()?.url.orEmpty(),
                width = dto.media.originalWidth,
                height = dto.media.originalHeight,
            )

            2 -> MediaItem.Video(
                url = dto.media.videoVersions.firstOrNull()?.url.orEmpty(),
                posterUrl = dto.media.imageVersions2?.candidates?.firstOrNull()?.url,
            )

            8 -> MediaItem.Carousel(dto.media.carouselMedia.map(::toCarouselEntry))

            else -> MediaItem.Unsupported("media_${dto.media?.mediaType}", dto.itemType)
        }

        "voice_media" -> {
            val audio = dto.voiceMedia?.media?.audio
            MediaItem.VoiceNote(url = audio?.audioSrc.orEmpty(), durationMs = audio?.duration)
        }

        "media_share" -> (dto.mediaShare ?: dto.directMediaShare?.media)?.let(::toSharedPost)
            ?: dto.xmaMediaShare.firstOrNull()?.let(::xmaToSharedPost)
            ?: MediaItem.SystemEvent("Post unavailable", raw?.pretty())

        "clip" -> dto.clip?.clip?.let { toSharedPost(it, isReel = true) }
            ?: MediaItem.SystemEvent("Reel unavailable", raw?.pretty())

        "felix_share" -> dto.felixShare?.video?.let { toSharedPost(it, isReel = true) }
            ?: MediaItem.SystemEvent("Video unavailable", raw?.pretty())

        "xma_media_share", "xma_reshare" -> {
            val xma = dto.xmaMediaShare.firstOrNull() ?: dto.xmaReshare.firstOrNull()
            xma?.let(::xmaToSharedPost)
                ?: MediaItem.SystemEvent("Post unavailable", raw?.pretty())
        }

        "xma_story_share" -> {
            val xma = dto.xmaStoryShare.firstOrNull()
            if (xma != null) {
                MediaItem.StoryShare(
                    thumbUrl = xma.previewUrlInfo?.url,
                    replyText = dto.text ?: xma.headerSubtitleText,
                    kind = xma.headerTitleText,
                    videoUrl = xma.playableUrl,
                    authorUsername = xma.headerTitleText?.removePrefix("@"),
                    authorAvatarUrl = xma.headerIconUrlInfo?.url,
                )
            } else {
                MediaItem.SystemEvent("Story unavailable", raw?.pretty())
            }
        }

        "xma_link" -> {
            val xma = dto.xmaLink.firstOrNull()
            val linkText = listOfNotNull(xma?.headerTitleText, xma?.headerSubtitleText)
                .joinToString(" — ")
                .ifBlank { dto.text.orEmpty() }
            MediaItem.Text(linkText)
        }

        "story_share" -> MediaItem.StoryShare(
            thumbUrl = dto.storyShare?.media?.imageVersions2?.candidates?.firstOrNull()?.url,
            replyText = dto.storyShare?.text ?: dto.text,
            kind = dto.storyShare?.storyShareType,
            videoUrl = dto.storyShare?.media?.videoVersions?.firstOrNull()?.url,
            authorUsername = dto.storyShare?.media?.user?.username,
            authorAvatarUrl = dto.storyShare?.media?.user?.profilePicUrl,
        )

        "reel_share" -> MediaItem.StoryShare(
            thumbUrl = dto.reelShare?.media?.imageVersions2?.candidates?.firstOrNull()?.url,
            replyText = dto.reelShare?.text ?: dto.text,
            kind = dto.reelShare?.type,
            videoUrl = dto.reelShare?.media?.videoVersions?.firstOrNull()?.url,
            authorUsername = dto.reelShare?.media?.user?.username,
            authorAvatarUrl = dto.reelShare?.media?.user?.profilePicUrl,
        )

        "raven_media", "visual_media" -> {
            val raven = dto.ravenMedia ?: dto.visualMedia
            val isVideo = raven?.mediaType == 2
            MediaItem.Ephemeral(
                thumbUrl = raven?.imageVersions2?.candidates?.firstOrNull()?.url,
                url = if (isVideo) {
                    raven?.videoVersions?.firstOrNull()?.url
                } else {
                    raven?.imageVersions2?.candidates?.firstOrNull()?.url
                },
                isVideo = isVideo,
            )
        }

        else -> MediaItem.Unsupported(
            kind = dto.itemType,
            rawPreview = dto.text ?: dto.preview.orEmpty(),
        )
    }

    private fun toSharedPost(dto: MediaShareDto, isReel: Boolean = false): MediaItem.SharedPost {
        var startIndex = 0
        val entries = when {
            dto.carouselMedia.isNotEmpty() -> {
                val kept = dto.carouselMedia
                    .map { child -> child to toCarouselEntry(child) }
                    .filter { (_, entry) -> entry.url.isNotBlank() }
                startIndex = kept.indexOfFirst { (child, _) ->
                    child.id != null && child.id == dto.carouselShareChildMediaId
                }.coerceAtLeast(0)
                kept.map { (_, entry) -> entry }
            }

            dto.videoVersions.isNotEmpty() -> listOf(
                CarouselEntry(
                    url = dto.videoVersions.first().url,
                    posterUrl = dto.imageVersions2?.candidates?.firstOrNull()?.url,
                    isVideo = true,
                ),
            )

            dto.imageVersions2?.candidates?.isNotEmpty() == true -> listOf(
                CarouselEntry(
                    url = dto.imageVersions2.candidates.first().url,
                    posterUrl = null,
                    isVideo = false,
                ),
            )

            else -> emptyList()
        }
        val sharedEntry = entries.getOrNull(startIndex)
        val firstCandidate = dto.imageVersions2?.candidates?.firstOrNull()
        return MediaItem.SharedPost(
            thumbUrl = sharedEntry?.posterUrl ?: firstCandidate?.url,
            caption = dto.caption?.text,
            authorUsername = dto.user?.username,
            authorAvatarUrl = dto.user?.profilePicUrl,
            thumbAspectRatio = ratioOf(dto.originalWidth, dto.originalHeight)
                ?: ratioOf(firstCandidate?.width, firstCandidate?.height),
            entries = entries,
            startIndex = startIndex,
            isReel = isReel || dto.productType == "clips",
        )
    }

    private fun ratioOf(width: Int?, height: Int?): Float? =
        if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }

    private fun xmaToSharedPost(xma: com.aneto.instachat.data.direct.dto.XmaDto): MediaItem.SharedPost {
        val thumb = xma.previewUrlInfo?.url
        val entry = when {
            !xma.playableUrl.isNullOrBlank() -> CarouselEntry(
                url = xma.playableUrl,
                posterUrl = thumb,
                isVideo = true,
            )

            !thumb.isNullOrBlank() -> CarouselEntry(
                url = thumb,
                posterUrl = null,
                isVideo = false,
            )

            else -> null
        }
        return MediaItem.SharedPost(
            thumbUrl = thumb,
            caption = xma.headerSubtitleText,
            authorUsername = xma.headerTitleText?.removePrefix("@"),
            authorAvatarUrl = xma.headerIconUrlInfo?.url,
            entries = listOfNotNull(entry),
        )
    }

    private fun toCarouselEntry(dto: CarouselEntryDto): CarouselEntry {
        val isVideo = dto.mediaType == 2
        val photoUrl = dto.imageVersions2?.candidates?.firstOrNull()?.url
        val videoUrl = dto.videoVersions.firstOrNull()?.url
        return CarouselEntry(
            url = if (isVideo) videoUrl.orEmpty() else photoUrl.orEmpty(),
            posterUrl = photoUrl,
            isVideo = isVideo,
        )
    }

    private fun parseTimestampMicros(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return if (raw.contains('.')) {
            raw.toDoubleOrNull()?.times(1_000_000.0)?.toLong() ?: 0L
        } else {
            raw.toLongOrNull() ?: 0L
        }
    }

    fun previewText(item: ItemDto): String = when (val mi = toMediaItem(item)) {
        is MediaItem.Text -> mi.text
        is MediaItem.SystemEvent -> mi.text
        is MediaItem.Photo -> "📷 Photo"
        is MediaItem.Video -> "🎬 Video"
        is MediaItem.VoiceNote -> "🎤 Voice message"
        is MediaItem.Carousel -> "🖼 ${mi.entries.size} items"
        is MediaItem.SharedPost -> "📱 ${mi.caption ?: "Shared post"}"
        is MediaItem.StoryShare -> "📖 ${mi.replyText ?: "Story"}"
        is MediaItem.Ephemeral -> "👁 Disappearing media"
        is MediaItem.Unsupported -> "[${mi.kind}]"
    }
}
