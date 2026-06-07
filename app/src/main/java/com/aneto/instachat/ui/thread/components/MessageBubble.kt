package com.aneto.instachat.ui.thread.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.aneto.instachat.R
import com.aneto.instachat.domain.model.CarouselEntry
import com.aneto.instachat.domain.model.MediaItem
import com.aneto.instachat.domain.model.MediaOrigin
import com.aneto.instachat.domain.model.Message
import com.aneto.instachat.domain.model.Reaction
import com.aneto.instachat.ui.heroMedia
import com.aneto.instachat.ui.inbox.Avatar
import com.aneto.instachat.ui.preview.PreviewSamples
import com.aneto.instachat.ui.preview.PreviewTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onLongPress: () -> Unit,
    onOpenMedia: (List<CarouselEntry>, Int, MediaOrigin?) -> Unit,
    senderName: String? = null,
    senderAvatarUrl: String? = null,
    senderAvatarUserId: String? = null,
    showSenderAvatarSlot: Boolean = false,
    senderAvatarSize: Dp = 28.dp,
    senderAvatarGap: Dp = 6.dp,
    maxBubbleWidth: Dp = 300.dp,
    cornerRadius: Dp = 22.dp,
    stackedCornerRadius: Dp = 6.dp,
    topPaddingFirst: Dp = 10.dp,
    topPaddingStacked: Dp = topPaddingFirst / 2,
) {
    if (message.content is MediaItem.SystemEvent) {
        val rawJson = message.content.rawJson
        var showRaw by remember { mutableStateOf(false) }
        SystemEventChip(
            text = message.content.text,
            onClick = rawJson?.let { { showRaw = true } },
        )
        if (showRaw && rawJson != null) {
            RawItemDialog(
                title = message.content.text,
                rawJson = rawJson,
                onDismiss = { showRaw = false },
            )
        }
        return
    }

    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // Cantos do lado "interno" (deles: esquerda; minhas: direita) reduzidos para
    // marcar continuidade dentro de um grupo do mesmo remetente: primeira reduz
    // embaixo, do meio reduzem em cima e embaixo, última reduz só em cima.
    val shape = groupedShape(isMine, isFirstInGroup, isLastInGroup, cornerRadius, stackedCornerRadius)

    val topPadding = if (isFirstInGroup) topPaddingFirst else topPaddingStacked

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine && showSenderAvatarSlot) {
            if (isLastInGroup) {
                Avatar(
                    url = senderAvatarUrl,
                    size = senderAvatarSize.value.toInt(),
                    cacheKey = senderAvatarUserId,
                )
            } else {
                Spacer(Modifier.size(senderAvatarSize))
            }
            Spacer(Modifier.width(senderAvatarGap))
        }
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            if (!isMine && isFirstInGroup && !senderName.isNullOrBlank()) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
                )
            }
            Surface(
                color = bubbleColor,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .combinedClickable(
                        onClick = {
                            val open = message.content.openable()
                            if (open.isNotEmpty()) {
                                val start = (message.content as? MediaItem.SharedPost)
                                    ?.startIndex?.coerceIn(0, open.lastIndex) ?: 0
                                onOpenMedia(open, start, message.content.origin())
                            }
                        },
                        onLongClick = onLongPress,
                    ),
            ) {
                BubbleContent(
                    messageId = message.id,
                    content = message.content,
                    textColor = textColor,
                    shapeFor = { base, stacked ->
                        groupedShape(isMine, isFirstInGroup, isLastInGroup, base, stacked)
                    },
                )
            }
            if (message.reactions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                ReactionChip(message.reactions.joinToString(" ") { it.emoji })
            }
        }
    }
}

/**
 * Shape com os cantos do lado do remetente (deles: esquerda; minhas: direita)
 * reduzidos conforme a posição no grupo de mensagens sequenciais.
 */
private fun groupedShape(
    isMine: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    radius: Dp,
    stackedRadius: Dp,
): RoundedCornerShape {
    fun corner(shrink: Boolean) = if (shrink) stackedRadius else radius
    val topShrunk = !isFirstInGroup
    val bottomShrunk = !isLastInGroup
    return RoundedCornerShape(
        topStart = corner(!isMine && topShrunk),
        topEnd = corner(isMine && topShrunk),
        bottomEnd = corner(isMine && bottomShrunk),
        bottomStart = corner(!isMine && bottomShrunk),
    )
}

private fun MediaItem.origin(): MediaOrigin? = when (this) {
    is MediaItem.SharedPost -> MediaOrigin(authorUsername, authorAvatarUrl, caption)
    is MediaItem.StoryShare -> MediaOrigin(authorUsername, authorAvatarUrl, null)
    else -> null
}

private fun MediaItem.openable(): List<CarouselEntry> = when (this) {
    is MediaItem.Photo ->
        if (url.isNotBlank()) listOf(CarouselEntry(url, null, false)) else emptyList()

    is MediaItem.Video ->
        if (url.isNotBlank()) listOf(CarouselEntry(url, posterUrl, true)) else emptyList()

    is MediaItem.Carousel -> entries.filter { it.url.isNotBlank() }

    is MediaItem.SharedPost -> entries.filter { it.url.isNotBlank() }

    is MediaItem.Ephemeral -> url?.takeIf { it.isNotBlank() }
        ?.let { listOf(CarouselEntry(it, thumbUrl, isVideo)) }
        ?: emptyList()

    is MediaItem.StoryShare -> when {
        !videoUrl.isNullOrBlank() -> listOf(CarouselEntry(videoUrl, thumbUrl, true))
        !thumbUrl.isNullOrBlank() -> listOf(CarouselEntry(thumbUrl, null, false))
        else -> emptyList()
    }

    else -> emptyList()
}

@Composable
private fun stableImageRequest(data: Any?, cacheKey: String) = ImageRequest.Builder(LocalPlatformContext.current)
    .data(data)
    .memoryCacheKey(cacheKey)
    .diskCacheKey(cacheKey)
    .build()

@Composable
private fun BubbleImage(
    data: Any?,
    cacheKey: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceDim))
    } else {
        AsyncImage(
            model = stableImageRequest(data, cacheKey),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

@Composable
private fun BubbleContent(
    messageId: String,
    content: MediaItem,
    textColor: Color,
    shapeFor: (base: Dp, stacked: Dp) -> RoundedCornerShape = { base, _ -> RoundedCornerShape(base) },
    textHorizontalPadding: Dp = 14.dp,
    textVerticalPadding: Dp = 10.dp,
    mediaSize: Dp = 240.dp,
    mediaCornerRadius: Dp = 22.dp,
    mediaStackedCornerRadius: Dp = 6.dp,
    nestedCornerRadius: Dp = 16.dp,
    nestedStackedCornerRadius: Dp = 4.dp,
    portraitWidth: Dp = 220.dp,
) {
    // Mídia full-bleed acompanha exatamente o shape do bubble; cards com inset
    // usam raios menores nos mesmos cantos para manter a continuidade visual.
    val mediaShape = shapeFor(mediaCornerRadius, mediaStackedCornerRadius)
    val nestedShape = shapeFor(nestedCornerRadius, nestedStackedCornerRadius)
    // Key da transição hero — espelha a entry inicial aberta no media viewer.
    val heroKey = content.openable().let { open ->
        val start = (content as? MediaItem.SharedPost)
            ?.startIndex?.coerceIn(0, open.lastIndex.coerceAtLeast(0)) ?: 0
        open.getOrNull(start)?.url?.let { "hero:$it" }
    }
    when (content) {
        is MediaItem.Text -> Text(
            text = content.text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = textHorizontalPadding, vertical = textVerticalPadding),
        )

        is MediaItem.SystemEvent -> Text(
            text = content.text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = textHorizontalPadding, vertical = textVerticalPadding),
        )

        is MediaItem.Photo -> Box(modifier = Modifier.size(mediaSize)) {
            BubbleImage(
                data = content.url,
                cacheKey = "msg:$messageId:photo",
                modifier = Modifier.size(mediaSize).heroMedia(heroKey).clip(mediaShape),
            )
        }

        is MediaItem.Video -> Box(
            modifier = Modifier.size(mediaSize),
            contentAlignment = Alignment.Center,
        ) {
            // Poster como hazeSource: o play overlay amostra a própria mídia.
            val hazeState = rememberHazeState()
            BubbleImage(
                data = content.posterUrl,
                cacheKey = "msg:$messageId:video",
                modifier = Modifier.size(mediaSize).heroMedia(heroKey).clip(mediaShape)
                    .hazeSource(state = hazeState),
            )
            PlayOverlay(hazeState = hazeState)
        }

        is MediaItem.Carousel -> Box(
            modifier = Modifier.size(mediaSize),
            contentAlignment = Alignment.Center,
        ) {
            val hazeState = rememberHazeState()
            val first = content.entries.firstOrNull()
            BubbleImage(
                data = first?.posterUrl ?: first?.url,
                cacheKey = "msg:$messageId:carousel",
                modifier = Modifier.size(mediaSize).heroMedia(heroKey).clip(mediaShape)
                    .hazeSource(state = hazeState),
            )
            CountBadge(
                count = content.entries.size,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }

        is MediaItem.VoiceNote -> Row(
            modifier = Modifier.padding(horizontal = textHorizontalPadding, vertical = textVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = textColor)
            Spacer(Modifier.width(8.dp))
            val secs = ((content.durationMs ?: 0L) / 1000).toInt()
            Text(text = stringResource(R.string.media_voice_duration, secs), color = textColor)
        }

        is MediaItem.SharedPost -> if (content.isReel && !content.thumbUrl.isNullOrBlank()) {
            PortraitMediaCard(
                thumbUrl = content.thumbUrl,
                cacheKey = "msg:$messageId:reel",
                isVideo = content.entries.any { it.isVideo },
                authorUsername = content.authorUsername,
                authorAvatarUrl = content.authorAvatarUrl,
                shape = nestedShape,
                modifier = Modifier.width(portraitWidth).padding(6.dp).heroMedia(heroKey),
            )
        } else {
            Column(modifier = Modifier.width(mediaSize).padding(6.dp)) {
                if (!content.authorUsername.isNullOrBlank() || !content.authorAvatarUrl.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 8.dp),
                    ) {
                        Avatar(
                            url = content.authorAvatarUrl,
                            size = 20,
                            cacheKey = content.authorUsername?.let { "author:$it" },
                        )
                        if (!content.authorUsername.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = content.authorUsername,
                                color = textColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                content.thumbUrl?.let {
                    // Altura fixada pelo aspect ratio da mídia: a imagem inteira aparece
                    // sem crop e o bubble não muda de tamanho quando o load termina.
                    val ratio = (content.thumbAspectRatio ?: 0.8f).coerceIn(0.5f, 1.91f)
                    Box(contentAlignment = Alignment.Center) {
                        val hazeState = rememberHazeState()
                        BubbleImage(
                            data = it,
                            cacheKey = "msg:$messageId:shared",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                                .heroMedia(heroKey)
                                .clip(shapeFor(12.dp, nestedStackedCornerRadius))
                                .hazeSource(state = hazeState),
                            contentScale = ContentScale.Fit,
                        )
                        if (content.entries.any { e -> e.isVideo }) PlayOverlay(hazeState = hazeState)
                        if (content.entries.size > 1) {
                            CountBadge(
                                count = content.entries.size,
                                hazeState = hazeState,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            )
                        }
                    }
                }
                if (!content.caption.isNullOrBlank()) {
                    Text(
                        text = content.caption,
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
            }
        }

        is MediaItem.StoryShare -> if (!content.thumbUrl.isNullOrBlank()) {
            Column(modifier = Modifier.width(portraitWidth).padding(6.dp)) {
                PortraitMediaCard(
                    thumbUrl = content.thumbUrl,
                    cacheKey = "msg:$messageId:story",
                    isVideo = !content.videoUrl.isNullOrBlank(),
                    authorUsername = content.authorUsername,
                    authorAvatarUrl = content.authorAvatarUrl,
                    shape = nestedShape,
                    modifier = Modifier.heroMedia(heroKey),
                )
                if (!content.replyText.isNullOrBlank()) {
                    Text(
                        text = content.replyText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = textHorizontalPadding, vertical = textVerticalPadding),
            ) {
                Text(
                    text = stringResource(R.string.media_story_share),
                    color = textColor.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!content.replyText.isNullOrBlank()) {
                    Text(
                        text = content.replyText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        is MediaItem.Ephemeral -> Row(
            modifier = Modifier.padding(horizontal = textHorizontalPadding, vertical = textVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Visibility, contentDescription = null, tint = textColor)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.media_ephemeral_tap),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is MediaItem.Unsupported -> Text(
            text = stringResource(R.string.media_unsupported, content.kind) +
                if (content.rawPreview.isNotBlank()) "\n${content.rawPreview.take(120)}" else "",
            color = textColor,
            modifier = Modifier.padding(horizontal = textHorizontalPadding, vertical = textVerticalPadding),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Story/reel card: 9:16 with the media as full-bleed background, the original
 * author's avatar + username pinned to the top over a scrim, and a centered
 * play button when the media is a video.
 */
@Composable
private fun PortraitMediaCard(
    thumbUrl: String,
    cacheKey: String,
    isVideo: Boolean,
    authorUsername: String?,
    authorAvatarUrl: String?,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val hazeState = rememberHazeState()
        BubbleImage(
            data = thumbUrl,
            cacheKey = cacheKey,
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
            contentScale = ContentScale.Crop,
        )
        val hasHeader = !authorUsername.isNullOrBlank() || !authorAvatarUrl.isNullOrBlank()
        if (hasHeader) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    url = authorAvatarUrl,
                    size = 20,
                    cacheKey = authorUsername?.let { "author:$it" },
                )
                if (!authorUsername.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = authorUsername,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (isVideo) PlayOverlay(hazeState = hazeState)
    }
}

/**
 * Fundo glass dos overlays sobre mídia: blur (Haze) quando há hazeSource da
 * própria mídia; fallback escurecido sólido sem haze (previews).
 */
@OptIn(dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class)
@Composable
private fun Modifier.overlayGlass(hazeState: HazeState?, fallbackAlpha: Float): Modifier = if (hazeState != null) {
    hazeEffect(state = hazeState, style = HazeMaterials.thin(containerColor = Color.Black))
} else {
    background(Color.Black.copy(alpha = fallbackAlpha))
}

@Composable
private fun PlayOverlay(
    hazeState: HazeState? = null,
    small: Boolean = false,
    largeDiameter: Dp = 52.dp,
    smallDiameter: Dp = 32.dp,
) {
    val diameter = if (small) smallDiameter else largeDiameter
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(diameter)
            .clip(RoundedCornerShape(50))
            .overlayGlass(hazeState, fallbackAlpha = 0.5f),
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(diameter * 0.72f),
        )
    }
}

@Composable
private fun CountBadge(
    count: Int,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.55f,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .overlayGlass(hazeState, fallbackAlpha = backgroundAlpha)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SystemEventChip(text: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = MaterialTheme.colorScheme.surfaceContainer
        val shape = RoundedCornerShape(14.dp)
        val label = @Composable {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        if (onClick != null) {
            Surface(onClick = onClick, color = color, shape = shape) { label() }
        } else {
            Surface(color = color, shape = shape) { label() }
        }
    }
}

@Composable
private fun ReactionChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BubbleRow(contentLabel: String, body: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = contentLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        body()
    }
}

@Preview(name = "Bubble · Text (mine)", showBackground = true)
@Composable
private fun PreviewBubbleTextMine() {
    PreviewTheme {
        BubbleRow("Text mine") {
            MessageBubble(
                message = PreviewSamples.message(
                    senderId = PreviewSamples.VIEWER_ID,
                    content = MediaItem.Text("Coffee later today?"),
                ),
                isMine = true,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Text (theirs) + reaction", showBackground = true)
@Composable
private fun PreviewBubbleTextTheirs() {
    PreviewTheme {
        BubbleRow("Text theirs") {
            MessageBubble(
                message = PreviewSamples.message(
                    senderId = PreviewSamples.userLuna.id,
                    content = MediaItem.Text("it's a holiday today, you forgot 😂"),
                    reactions = listOf(
                        Reaction(PreviewSamples.VIEWER_ID, "❤"),
                        Reaction(PreviewSamples.userLuna.id, "😂"),
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Text (long)", showBackground = true)
@Composable
private fun PreviewBubbleTextLongo() {
    PreviewTheme {
        BubbleRow("Text longo") {
            MessageBubble(
                message = PreviewSamples.message(
                    senderId = PreviewSamples.userLuna.id,
                    content = MediaItem.Text(
                        "Found a new café near my place — it's exactly your vibe. " +
                            "Plants, quiet music, comfy chairs. Want to meet Saturday morning?",
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · grouping (first/middle/last)", showBackground = true, heightDp = 360)
@Composable
private fun PreviewBubbleGrouped() {
    PreviewTheme {
        Column(modifier = Modifier.padding(12.dp)) {
            MessageBubble(
                message = PreviewSamples.message(id = "g1", content = MediaItem.Text("Hey! How's it going?")),
                isMine = true,
                isFirstInGroup = true,
                isLastInGroup = false,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
            MessageBubble(
                message = PreviewSamples.message(id = "g2", content = MediaItem.Text("Sent you something")),
                isMine = true,
                isFirstInGroup = false,
                isLastInGroup = false,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
            MessageBubble(
                message = PreviewSamples.message(id = "g3", content = MediaItem.Text("Take a look :)")),
                isMine = true,
                isFirstInGroup = false,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
            Spacer(Modifier.height(8.dp))
            MessageBubble(
                message = PreviewSamples.message(
                    id = "g4",
                    senderId = PreviewSamples.userLuna.id,
                    content = MediaItem.Text("Saw it, love it!"),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = false,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
            MessageBubble(
                message = PreviewSamples.message(
                    id = "g5",
                    senderId = PreviewSamples.userLuna.id,
                    content = MediaItem.Text("Saturday it is then"),
                ),
                isMine = false,
                isFirstInGroup = false,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Photo", showBackground = true)
@Composable
private fun PreviewBubblePhoto() {
    PreviewTheme {
        BubbleRow("Photo") {
            MessageBubble(
                message = PreviewSamples.message(content = MediaItem.Photo("", 1080, 1080)),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Video", showBackground = true)
@Composable
private fun PreviewBubbleVideo() {
    PreviewTheme {
        BubbleRow("Video") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.Video(url = "", posterUrl = null),
                ),
                isMine = true,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Carousel", showBackground = true)
@Composable
private fun PreviewBubbleCarousel() {
    PreviewTheme {
        BubbleRow("Carousel") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.Carousel(PreviewSamples.carouselEntries),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · VoiceNote", showBackground = true)
@Composable
private fun PreviewBubbleVoice() {
    PreviewTheme {
        BubbleRow("VoiceNote") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.VoiceNote(url = "", durationMs = 23_000L),
                ),
                isMine = true,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · SharedPost", showBackground = true)
@Composable
private fun PreviewBubbleSharedPost() {
    PreviewTheme {
        BubbleRow("SharedPost") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.SharedPost(
                        thumbUrl = "https://placeholder",
                        caption = "Check out this tiramisu recipe — we have to try it this weekend.",
                        authorUsername = "chef.mari",
                        entries = PreviewSamples.carouselEntries.take(2),
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · StoryShare", showBackground = true, heightDp = 480)
@Composable
private fun PreviewBubbleStoryShare() {
    PreviewTheme {
        BubbleRow("StoryShare") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.StoryShare(
                        thumbUrl = "https://placeholder",
                        replyText = "loved your story today!",
                        kind = "reply",
                        videoUrl = "https://placeholder",
                        authorUsername = "luna.mendes",
                        authorAvatarUrl = "https://placeholder",
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · StoryShare (no media)", showBackground = true)
@Composable
private fun PreviewBubbleStoryShareTextOnly() {
    PreviewTheme {
        BubbleRow("StoryShare no media") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.StoryShare(
                        thumbUrl = null,
                        replyText = "replied to your story",
                        kind = "reply",
                        videoUrl = null,
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Reel", showBackground = true, heightDp = 520)
@Composable
private fun PreviewBubbleReel() {
    PreviewTheme {
        BubbleRow("Reel") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.SharedPost(
                        thumbUrl = "https://placeholder",
                        caption = "This cat playing piano is the best thing on the internet today 🐈🎹",
                        authorUsername = "catsofig",
                        authorAvatarUrl = "https://placeholder",
                        entries = listOf(CarouselEntry("https://placeholder", "https://placeholder", true)),
                        isReel = true,
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Ephemeral", showBackground = true)
@Composable
private fun PreviewBubbleEphemeral() {
    PreviewTheme {
        BubbleRow("Ephemeral") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.Ephemeral(thumbUrl = null, url = null, isVideo = false),
                ),
                isMine = true,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · Unsupported", showBackground = true)
@Composable
private fun PreviewBubbleUnsupported() {
    PreviewTheme {
        BubbleRow("Unsupported") {
            MessageBubble(
                message = PreviewSamples.message(
                    content = MediaItem.Unsupported(
                        kind = "xma_media_type_23",
                        rawPreview = "{ \"type\": 23, \"payload\": \"...\" }",
                    ),
                ),
                isMine = false,
                isFirstInGroup = true,
                isLastInGroup = true,
                onLongPress = {},
                onOpenMedia = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "Bubble · SystemEvent", showBackground = true)
@Composable
private fun PreviewBubbleSystemEvent() {
    PreviewTheme {
        MessageBubble(
            message = PreviewSamples.message(
                content = MediaItem.SystemEvent("Luna left the group"),
            ),
            isMine = false,
            isFirstInGroup = true,
            isLastInGroup = true,
            onLongPress = {},
            onOpenMedia = { _, _, _ -> },
        )
    }
}

@Preview(name = "Sub · SystemEventChip", showBackground = true)
@Composable
private fun PreviewSystemEventChip() {
    PreviewTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            SystemEventChip(text = "Reacted ❤ to your message")
        }
    }
}

@Preview(name = "Sub · ReactionChip", showBackground = true)
@Composable
private fun PreviewReactionChip() {
    PreviewTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            ReactionChip(text = "❤ 😂 🔥")
        }
    }
}

@Preview(name = "Sub · PlayOverlay (grande/pequeno)", showBackground = true)
@Composable
private fun PreviewPlayOverlay() {
    PreviewTheme {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayOverlay()
            PlayOverlay(small = true)
        }
    }
}

@Preview(name = "Sub · CountBadge", showBackground = true)
@Composable
private fun PreviewCountBadge() {
    PreviewTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountBadge(count = 3)
            CountBadge(count = 12, backgroundAlpha = 0.8f)
        }
    }
}
