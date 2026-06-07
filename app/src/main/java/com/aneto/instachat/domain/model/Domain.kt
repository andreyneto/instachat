package com.aneto.instachat.domain.model

data class User(val id: String, val username: String, val fullName: String?, val profilePicUrl: String?)

data class Thread(
    val id: String,
    val title: String,
    val users: List<User>,
    val lastPreview: String,
    val lastActivityAt: Long,
    val unread: Boolean,
    val isGroup: Boolean,
)

data class Reaction(val senderId: String, val emoji: String)

data class Message(
    val id: String,
    val senderId: String,
    val timestampMicros: Long,
    val content: MediaItem,
    val reactions: List<Reaction>,
) {
    val timestampMillis: Long get() = timestampMicros / 1000L
}

sealed interface MediaItem {
    data class Text(val text: String) : MediaItem
    data class SystemEvent(val text: String, val rawJson: String? = null) : MediaItem
    data class Photo(val url: String, val width: Int?, val height: Int?) : MediaItem
    data class Video(val url: String, val posterUrl: String?) : MediaItem
    data class VoiceNote(val url: String, val durationMs: Long?) : MediaItem
    data class Carousel(val entries: List<CarouselEntry>) : MediaItem
    data class SharedPost(
        val thumbUrl: String?,
        val caption: String?,
        val authorUsername: String?,
        val authorAvatarUrl: String? = null,
        /** width / height da mídia — fixa a altura do bubble antes da imagem carregar. */
        val thumbAspectRatio: Float? = null,
        val entries: List<CarouselEntry>,
        /** Slide to open first — set when a single carousel slide was shared. */
        val startIndex: Int = 0,
        /** Reels render as 9:16 portrait cards. */
        val isReel: Boolean = false,
    ) : MediaItem
    data class StoryShare(
        val thumbUrl: String?,
        val replyText: String?,
        val kind: String?,
        val videoUrl: String?,
        val authorUsername: String? = null,
        val authorAvatarUrl: String? = null,
    ) : MediaItem
    data class Ephemeral(val thumbUrl: String?, val url: String?, val isVideo: Boolean) : MediaItem
    data class Unsupported(val kind: String, val rawPreview: String) : MediaItem
}

data class CarouselEntry(val url: String, val posterUrl: String?, val isVideo: Boolean)

/** Autor original + caption do conteúdo aberto no media viewer. */
data class MediaOrigin(
    val authorUsername: String? = null,
    val authorAvatarUrl: String? = null,
    val caption: String? = null,
)
