package com.aneto.instachat.ui

import android.net.Uri
import com.aneto.instachat.domain.model.CarouselEntry
import com.aneto.instachat.domain.model.MediaOrigin

sealed class AppRoute(val route: String) {
    data object Login : AppRoute("login")
    data object Inbox : AppRoute("inbox")

    data object Thread : AppRoute("thread/{threadId}") {
        const val ARG_THREAD_ID = "threadId"
        fun build(threadId: String): String = "thread/${Uri.encode(threadId)}"
    }

    data object Media : AppRoute(
        "media?items={items}&index={index}&author={author}&avatar={avatar}&caption={caption}",
    ) {
        const val ARG_ITEMS = "items"
        const val ARG_INDEX = "index"
        const val ARG_AUTHOR = "author"
        const val ARG_AVATAR = "avatar"
        const val ARG_CAPTION = "caption"

        fun build(entries: List<CarouselEntry>, index: Int = 0, origin: MediaOrigin? = null): String {
            val packed = entries.joinToString(separator = "|") { entry ->
                Uri.encode(entry.url) + ":" + (if (entry.isVideo) "v" else "i") + ":" +
                    Uri.encode(entry.posterUrl.orEmpty())
            }
            return "media?items=${Uri.encode(packed)}&index=$index" +
                "&author=${Uri.encode(origin?.authorUsername.orEmpty())}" +
                "&avatar=${Uri.encode(origin?.authorAvatarUrl.orEmpty())}" +
                "&caption=${Uri.encode(origin?.caption.orEmpty())}"
        }

        fun parse(items: String): List<CarouselEntry> {
            if (items.isEmpty()) return emptyList()
            return items.split("|").mapNotNull { chunk ->
                val parts = chunk.split(":")
                if (parts.size < 2) return@mapNotNull null
                val url = Uri.decode(parts[0])
                val isVideo = parts[1] == "v"
                val poster = parts.getOrNull(2)?.let(Uri::decode)?.takeIf { it.isNotBlank() }
                CarouselEntry(url = url, posterUrl = poster, isVideo = isVideo)
            }
        }
    }
}
