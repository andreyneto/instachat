package com.aneto.instachat.data.direct.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SimpleStatusResponse(val status: String? = null)

@Serializable
data class DirectInboxResponse(
    val inbox: InboxDto? = null,
    @SerialName("viewer")
    val viewer: UserDto? = null,
    val status: String? = null,
)

@Serializable
data class InboxDto(
    val threads: List<ThreadDto> = emptyList(),
    @SerialName("has_older")
    val hasOlder: Boolean = false,
    @SerialName("oldest_cursor")
    val oldestCursor: String? = null,
    @SerialName("unseen_count")
    val unseenCount: Int = 0,
)

@Serializable
data class ThreadDto(
    @SerialName("thread_id")
    val threadId: String,
    @SerialName("thread_v2_id")
    val threadV2Id: String? = null,
    @SerialName("thread_fbid")
    val threadFbid: String? = null,
    @SerialName("thread_title")
    val threadTitle: String? = null,
    val users: List<UserDto> = emptyList(),
    val items: List<ItemDto> = emptyList(),
    @SerialName("last_activity_at")
    val lastActivityAt: Long? = null,
    @SerialName("has_older")
    val hasOlder: Boolean = false,
    @SerialName("oldest_cursor")
    val oldestCursor: String? = null,
    @SerialName("read_state")
    val readState: Int = 0,
    @SerialName("last_seen_at")
    val lastSeenAt: Map<String, LastSeenEntryDto> = emptyMap(),
    @SerialName("is_group")
    val isGroup: Boolean = false,
)

@Serializable
data class LastSeenEntryDto(
    val timestamp: String? = null,
    @SerialName("item_id")
    val itemId: String? = null,
)

@Serializable
data class UserDto(
    @SerialName("pk_id")
    val pkId: String? = null,
    @SerialName("pk")
    val pk: Long? = null,
    val username: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("profile_pic_url")
    val profilePicUrl: String? = null,
) {
    fun idString(): String = pkId ?: pk?.toString() ?: ""
}

@Serializable
data class ThreadResponse(val thread: ThreadDto? = null, val status: String? = null)

/**
 * A direct thread item. Instagram's schema is huge and mutable; we only name
 * fields we know and keep a raw JSON reference for everything else so the
 * mapper can fall back to [MediaItem.Unsupported] without crashing.
 */
@Serializable
data class ItemDto(
    @SerialName("item_id")
    val itemId: String,
    @SerialName("user_id")
    val userId: Long? = null,
    val timestamp: Long? = null,
    @SerialName("item_type")
    val itemType: String,
    val text: String? = null,
    val reactions: ReactionsDto? = null,
    val media: MediaDto? = null,
    @SerialName("voice_media")
    val voiceMedia: VoiceMediaDto? = null,
    @SerialName("media_share")
    val mediaShare: MediaShareDto? = null,
    @SerialName("direct_media_share")
    val directMediaShare: DirectMediaShareDto? = null,
    val clip: ClipDto? = null,
    @SerialName("felix_share")
    val felixShare: FelixShareDto? = null,
    @SerialName("story_share")
    val storyShare: StoryShareDto? = null,
    @SerialName("reel_share")
    val reelShare: ReelShareDto? = null,
    @SerialName("raven_media")
    val ravenMedia: RavenMediaDto? = null,
    @SerialName("visual_media")
    val visualMedia: RavenMediaDto? = null,
    @SerialName("action_log")
    val actionLog: ActionLogDto? = null,
    @SerialName("xma_media_share")
    val xmaMediaShare: List<XmaDto> = emptyList(),
    @SerialName("xma_story_share")
    val xmaStoryShare: List<XmaDto> = emptyList(),
    @SerialName("xma_reshare")
    val xmaReshare: List<XmaDto> = emptyList(),
    @SerialName("xma_link")
    val xmaLink: List<XmaDto> = emptyList(),
    /** Catch-all so mapper can inspect raw payload for unknown types. */
    @SerialName("preview")
    val preview: String? = null,
)

@Serializable
data class ActionLogDto(val description: String? = null)

@Serializable
data class XmaDto(
    @SerialName("header_title_text")
    val headerTitleText: String? = null,
    @SerialName("header_subtitle_text")
    val headerSubtitleText: String? = null,
    @SerialName("preview_url_info")
    val previewUrlInfo: XmaUrlDto? = null,
    @SerialName("playable_url")
    val playableUrl: String? = null,
    @SerialName("header_icon_url_info")
    val headerIconUrlInfo: XmaUrlDto? = null,
)

@Serializable
data class XmaUrlDto(val url: String? = null)

@Serializable
data class ReactionsDto(val emojis: List<EmojiReactionDto> = emptyList(), val likes: List<LikeDto> = emptyList())

@Serializable
data class EmojiReactionDto(
    @SerialName("sender_id")
    val senderId: Long,
    val emoji: String,
    val timestamp: Long? = null,
)

@Serializable
data class LikeDto(
    @SerialName("sender_id")
    val senderId: Long,
    val timestamp: Long? = null,
)

@Serializable
data class MediaDto(
    @SerialName("media_type")
    val mediaType: Int,
    @SerialName("image_versions2")
    val imageVersions2: ImageVersionsDto? = null,
    @SerialName("video_versions")
    val videoVersions: List<VideoVersionDto> = emptyList(),
    @SerialName("carousel_media")
    val carouselMedia: List<CarouselEntryDto> = emptyList(),
    @SerialName("original_width")
    val originalWidth: Int? = null,
    @SerialName("original_height")
    val originalHeight: Int? = null,
)

@Serializable
data class CarouselEntryDto(
    val id: String? = null,
    @SerialName("media_type")
    val mediaType: Int,
    @SerialName("image_versions2")
    val imageVersions2: ImageVersionsDto? = null,
    @SerialName("video_versions")
    val videoVersions: List<VideoVersionDto> = emptyList(),
)

@Serializable
data class ImageVersionsDto(val candidates: List<ImageCandidateDto> = emptyList())

@Serializable
data class ImageCandidateDto(val url: String, val width: Int? = null, val height: Int? = null)

@Serializable
data class VideoVersionDto(val url: String, val width: Int? = null, val height: Int? = null)

@Serializable
data class VoiceMediaDto(val media: VoiceInnerDto? = null)

@Serializable
data class VoiceInnerDto(
    @SerialName("audio")
    val audio: VoiceAudioDto? = null,
)

@Serializable
data class VoiceAudioDto(
    @SerialName("audio_src")
    val audioSrc: String,
    @SerialName("duration")
    val duration: Long? = null,
)

/** Wrapper used by web-client payloads: the shared post lives under `direct_media_share.media`. */
@Serializable
data class DirectMediaShareDto(val media: MediaShareDto? = null)

@Serializable
data class MediaShareDto(
    val id: String? = null,
    val code: String? = null,
    val caption: CaptionDto? = null,
    val user: UserDto? = null,
    @SerialName("media_type")
    val mediaType: Int? = null,
    /** "clips" for reels, "feed"/"carousel_container"/"igtv" otherwise. */
    @SerialName("product_type")
    val productType: String? = null,
    @SerialName("image_versions2")
    val imageVersions2: ImageVersionsDto? = null,
    @SerialName("video_versions")
    val videoVersions: List<VideoVersionDto> = emptyList(),
    @SerialName("carousel_media")
    val carouselMedia: List<CarouselEntryDto> = emptyList(),
    /** When a single carousel slide was shared, its media id (matches [CarouselEntryDto.id]). */
    @SerialName("carousel_share_child_media_id")
    val carouselShareChildMediaId: String? = null,
    @SerialName("original_width")
    val originalWidth: Int? = null,
    @SerialName("original_height")
    val originalHeight: Int? = null,
)

@Serializable
data class CaptionDto(val text: String? = null)

@Serializable
data class ClipDto(val clip: MediaShareDto? = null)

@Serializable
data class FelixShareDto(val video: MediaShareDto? = null)

@Serializable
data class StoryShareDto(
    val text: String? = null,
    val media: MediaShareDto? = null,
    @SerialName("story_share_type")
    val storyShareType: String? = null,
)

@Serializable
data class ReelShareDto(val text: String? = null, val type: String? = null, val media: MediaShareDto? = null)

@Serializable
data class RavenMediaDto(
    @SerialName("media_type")
    val mediaType: Int? = null,
    @SerialName("image_versions2")
    val imageVersions2: ImageVersionsDto? = null,
    @SerialName("video_versions")
    val videoVersions: List<VideoVersionDto> = emptyList(),
)

/** Holds the raw JSON body when something unexpected shows up. */
@Serializable
data class RawItem(val raw: JsonObject)

fun JsonElement.prettyHint(): String = toString().take(160)
