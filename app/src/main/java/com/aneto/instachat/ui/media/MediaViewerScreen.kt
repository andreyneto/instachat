package com.aneto.instachat.ui.media

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.aneto.instachat.R
import com.aneto.instachat.domain.model.CarouselEntry
import com.aneto.instachat.ui.heroMedia
import com.aneto.instachat.ui.inbox.Avatar
import com.aneto.instachat.ui.preview.PreviewTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem as ExoMediaItem

@Composable
fun MediaViewerScreen(
    entries: List<CarouselEntry>,
    initialIndex: Int,
    onClose: () -> Unit,
    authorUsername: String? = null,
    authorAvatarUrl: String? = null,
    caption: String? = null,
    emptyText: String = stringResource(R.string.media_empty),
    closeButtonSize: Dp = 40.dp,
) {
    BackHandler(onBack = onClose)

    val pagerState = rememberPagerState(
        initialPage = if (entries.isEmpty()) 0 else initialIndex.coerceIn(0, entries.lastIndex),
        pageCount = { entries.size },
    )

    MediaViewerContent(
        entries = entries,
        pagerState = pagerState,
        onClose = onClose,
        authorUsername = authorUsername,
        authorAvatarUrl = authorAvatarUrl,
        caption = caption,
        emptyText = emptyText,
        closeButtonSize = closeButtonSize,
    )
}

@OptIn(dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class)
@Composable
fun MediaViewerContent(
    entries: List<CarouselEntry>,
    pagerState: PagerState,
    onClose: () -> Unit,
    authorUsername: String? = null,
    authorAvatarUrl: String? = null,
    caption: String? = null,
    emptyText: String = stringResource(R.string.media_empty),
    closeButtonSize: Dp = 40.dp,
    avatarSize: Int = 32,
    captionMaxHeight: Dp = 320.dp,
) {
    val inPreview = LocalInspectionMode.current
    val hazeState = rememberHazeState()

    // Swipe down para sair, estilo galeria: a mídia segue o dedo (com leve
    // scale), fundo e chrome esmaecem; além do limiar (ou flick) fecha,
    // senão volta com spring. Lido só na fase de draw — sem recomposição.
    val scope = rememberCoroutineScope()
    val dismissOffset = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }
    val dismissProgress = {
        (dismissOffset.value / (dismissThresholdPx * 1.6f)).coerceIn(0f, 1f)
    }

    // Player da página ativa, hasteado para os controles desenhados fora do pager.
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val playerUi = rememberPlayerUiState(activePlayer)
    var muted by remember { mutableStateOf(false) }

    // Visibilidade dos controles de vídeo: tap alterna; auto-esconde tocando.
    // hideSignal reinicia o timer a cada interação (seek, mute, play/pause).
    var controlsVisible by remember { mutableStateOf(true) }
    var hideSignal by remember { mutableIntStateOf(0) }
    val currentEntry = entries.getOrNull(pagerState.currentPage)
    val videoControlsAvailable = currentEntry?.isVideo == true && activePlayer != null

    LaunchedEffect(pagerState.currentPage) { controlsVisible = true }
    LaunchedEffect(playerUi.isPlaying) { if (!playerUi.isPlaying) controlsVisible = true }
    LaunchedEffect(controlsVisible, playerUi.isPlaying, hideSignal) {
        if (controlsVisible && playerUi.isPlaying) {
            delay(3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(activePlayer, muted) { activePlayer?.volume = if (muted) 0f else 1f }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo + mídia dentro do hazeSource: o blur da caption amostra também o
        // preto de letterbox, não só a área coberta pela imagem.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 1f - 0.55f * dismissProgress()))
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            dismissOffset.snapTo((dismissOffset.value + delta).coerceAtLeast(0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        if (dismissOffset.value > dismissThresholdPx || velocity > 2_500f) {
                            onClose()
                        } else {
                            dismissOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                ),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = emptyText,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = dismissOffset.value
                            val scale = 1f - 0.15f * dismissProgress()
                            scaleX = scale
                            scaleY = scale
                        },
                ) { page ->
                    val entry = entries[page]
                    // Mesma key usada no bubble: hero animando do balão para cá (e de volta).
                    Box(modifier = Modifier.fillMaxSize().heroMedia("hero:${entry.url}")) {
                        if (entry.isVideo && entry.url.isNotBlank() && !inPreview) {
                            VideoPlayer(
                                url = entry.url,
                                active = pagerState.currentPage == page,
                                onActive = { activePlayer = it },
                                onInactive = { if (activePlayer == it) activePlayer = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Camada de toque sobre o PlayerView: alterna os controles.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { controlsVisible = !controlsVisible },
                            )
                        } else if (inPreview) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (entry.isVideo) "[video]" else "[image]",
                                    color = Color.White,
                                )
                            }
                        } else {
                            AsyncImage(
                                model = entry.url.ifBlank { entry.posterUrl },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }

        // Header: fechar + autor original (avatar e username) + contador do carousel.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - 1.4f * dismissProgress()).coerceAtLeast(0f) }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.6f),
                        1f to Color.Transparent,
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(closeButtonSize)) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
            }
            if (!authorUsername.isNullOrBlank() || !authorAvatarUrl.isNullOrBlank()) {
                Spacer(Modifier.width(4.dp))
                Avatar(
                    url = authorAvatarUrl,
                    size = avatarSize,
                    cacheKey = authorUsername?.let { "author:$it" },
                )
                if (!authorUsername.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = authorUsername,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (entries.size > 1) {
                Surface(color = Color.Black.copy(alpha = 0.55f)) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${entries.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Centro: apenas play/pause — glass blur com shape morph expressivo
        // (círculo pausado, squircle tocando) e replay ao terminar.
        AnimatedVisibility(
            visible = controlsVisible && videoControlsAvailable,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = (1f - 1.4f * dismissProgress()).coerceAtLeast(0f) },
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
        ) {
            PlayPauseButton(
                isPlaying = playerUi.isPlaying,
                ended = playerUi.ended,
                hazeState = hazeState,
                onClick = {
                    activePlayer?.let { p ->
                        when {
                            p.isPlaying -> p.pause()

                            p.playbackState == Player.STATE_ENDED -> {
                                p.seekTo(0)
                                p.play()
                            }

                            else -> p.play()
                        }
                    }
                    hideSignal++
                },
            )
        }

        // Pilha inferior: barra de progresso/duração/mute imediatamente acima da
        // caption — ela acompanha a posição da caption, inclusive expandida.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - 1.4f * dismissProgress()).coerceAtLeast(0f) }
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        ) {
            AnimatedVisibility(
                visible = controlsVisible && videoControlsAvailable,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                PlayerControlsBar(
                    positionMs = playerUi.positionMs,
                    durationMs = playerUi.durationMs,
                    isPlaying = playerUi.isPlaying,
                    muted = muted,
                    hazeState = hazeState,
                    onSeekTo = { fraction ->
                        activePlayer?.let { it.seekTo((fraction * playerUi.durationMs).toLong()) }
                        hideSignal++
                    },
                    onToggleMute = {
                        muted = !muted
                        hideSignal++
                    },
                    onInteraction = { hideSignal++ },
                    modifier = Modifier.padding(bottom = if (caption.isNullOrBlank()) 0.dp else 10.dp),
                )
            }

            // Caption: 3 linhas + tap para expandir; expandida rola sozinha,
            // a mídia ao fundo permanece estática. Container com blur (Haze),
            // como o composer e o top bar da thread.
            if (!caption.isNullOrBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Mesmo radius do pill de progresso acima, com padding
                        // proporcional para o texto respirar dentro da curva.
                        .clip(RoundedCornerShape(28.dp))
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(containerColor = Color.Black),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    if (expanded) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .heightIn(max = captionMaxHeight)
                                .verticalFadingEdges(scrollState)
                                .verticalScroll(scrollState),
                        ) {
                            Text(
                                text = caption,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            text = caption,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Botão central de play/pause em glass blur. M3 Expressive: shape morph com
 * spring bouncy — círculo quando pausado, squircle quando tocando.
 */
@OptIn(dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class)
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    ended: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 40.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "playPauseCorner",
    )
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(corner))
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(containerColor = Color.Black),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val iconKey = when {
            ended -> "replay"
            isPlaying -> "pause"
            else -> "play"
        }
        AnimatedContent(
            targetState = iconKey,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.6f))
            },
            label = "playPauseIcon",
        ) { key ->
            Icon(
                imageVector = when (key) {
                    "replay" -> Icons.Rounded.Replay
                    "pause" -> Icons.Rounded.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                contentDescription = when (key) {
                    "replay" -> stringResource(R.string.media_replay)
                    "pause" -> stringResource(R.string.media_pause)
                    else -> stringResource(R.string.media_play)
                },
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * Pill inferior de controles: tempo decorrido, slider wavy (M3 Expressive),
 * duração total e mute. A onda anima tocando e achata pausado/durante scrub.
 */
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class,
)
@Composable
private fun PlayerControlsBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    muted: Boolean,
    hazeState: HazeState,
    onSeekTo: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Durante o drag o progresso exibido é o do dedo; só busca ao soltar.
    var scrub by remember { mutableStateOf<Float?>(null) }
    val liveFraction =
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = scrub ?: liveFraction
    val shownPositionMs = scrub?.let { (it * durationMs).toLong() } ?: positionMs
    // Onda só enquanto toca (e sem scrub); o indicador anima a transição de
    // amplitude internamente — passar valor pré-animado quebra essa lógica.
    val waveAmplitude = if (isPlaying && scrub == null) 1f else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(containerColor = Color.Black),
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatPlayerTime(shownPositionMs),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 12.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        onSeekTo((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrub = (offset.x / size.width).coerceIn(0f, 1f)
                            onInteraction()
                        },
                        onDragEnd = {
                            scrub?.let(onSeekTo)
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                    ) { change, dragAmount ->
                        scrub = ((scrub ?: 0f) + dragAmount / size.width).coerceIn(0f, 1f)
                        change.consume()
                        onInteraction()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearWavyProgressIndicator(
                progress = { shownFraction },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                amplitude = { waveAmplitude },
            )
        }
        Text(
            text = formatPlayerTime(durationMs),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = onToggleMute, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = if (muted) {
                    Icons.AutoMirrored.Rounded.VolumeOff
                } else {
                    Icons.AutoMirrored.Rounded.VolumeUp
                },
                contentDescription = if (muted) "Ativar som" else "Silenciar",
                tint = Color.White,
            )
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Estado de playback observável pelos controles Compose. */
@Stable
private class PlayerUiState {
    var isPlaying by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
}

@Composable
private fun rememberPlayerUiState(player: ExoPlayer?): PlayerUiState {
    val state = remember(player) { PlayerUiState() }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.ended = playbackState == Player.STATE_ENDED
                state.durationMs = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        state.isPlaying = player.isPlaying
        state.ended = player.playbackState == Player.STATE_ENDED
        state.positionMs = player.currentPosition.coerceAtLeast(0L)
        state.durationMs = player.duration.coerceAtLeast(0L)
        onDispose { player.removeListener(listener) }
    }
    // Posição não emite eventos: polling leve enquanto o player existir.
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            state.positionMs = player.currentPosition.coerceAtLeast(0L)
            if (state.durationMs <= 0L) {
                state.durationMs = player.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }
    return state
}

/**
 * Esmaece as bordas verticais enquanto houver conteúdo para rolar naquela
 * direção — dica visual de overflow na caption expandida.
 */
private fun Modifier.verticalFadingEdges(scrollState: ScrollState, fadeHeight: Dp = 24.dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = fadeHeight.toPx()
            if (scrollState.canScrollBackward) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = fadePx,
                    ),
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scrollState.canScrollForward) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fadePx,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - fadePx),
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

@Composable
private fun VideoPlayer(
    url: String,
    active: Boolean,
    onActive: (ExoPlayer) -> Unit,
    onInactive: (ExoPlayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(url))
            prepare()
            playWhenReady = active
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    DisposableEffect(active, player) {
        player.playWhenReady = active
        if (active) onActive(player)
        // O dono dos controles confere a identidade antes de limpar — assim a
        // ordem de dispose entre páginas vizinhas do pager não importa.
        onDispose { onInactive(player) }
    }

    AndroidView(
        factory = { ctx ->
            // Inflado de XML: surface_type=texture_view (sem setter programático).
            // TextureView entra na composição normal, permitindo o blur do Haze
            // amostrar o vídeo — SurfaceView é furado na janela e sai preto.
            val view = LayoutInflater.from(ctx)
                .inflate(R.layout.media_player_texture, null) as PlayerView
            view.player = player
            view
        },
        modifier = modifier,
    )
}

@Preview(name = "MediaViewer · single image", showBackground = true, heightDp = 640)
@Composable
private fun PreviewMediaViewerSingle() {
    PreviewTheme {
        val entries = listOf(CarouselEntry(url = "fake", posterUrl = null, isVideo = false))
        MediaViewerContent(
            entries = entries,
            pagerState = rememberPagerState(initialPage = 0, pageCount = { entries.size }),
            onClose = {},
        )
    }
}

@Preview(name = "MediaViewer · carousel (3)", showBackground = true, heightDp = 640)
@Composable
private fun PreviewMediaViewerCarousel() {
    PreviewTheme {
        val entries = listOf(
            CarouselEntry(url = "a", posterUrl = null, isVideo = false),
            CarouselEntry(url = "b", posterUrl = null, isVideo = true),
            CarouselEntry(url = "c", posterUrl = null, isVideo = false),
        )
        MediaViewerContent(
            entries = entries,
            pagerState = rememberPagerState(initialPage = 1, pageCount = { entries.size }),
            onClose = {},
        )
    }
}

@Preview(name = "MediaViewer · autor + caption", showBackground = true, heightDp = 640)
@Composable
private fun PreviewMediaViewerAuthorCaption() {
    PreviewTheme {
        val entries = listOf(CarouselEntry(url = "fake", posterUrl = null, isVideo = false))
        MediaViewerContent(
            entries = entries,
            pagerState = rememberPagerState(initialPage = 0, pageCount = { entries.size }),
            onClose = {},
            authorUsername = "chef.mari",
            authorAvatarUrl = "https://placeholder",
            caption = "Nonna's tiramisu recipe: layers of ladyfingers soaked in " +
                "coffee, mascarpone cream with egg yolks, and dark cocoa dusted on top. " +
                "The secret is letting it rest in the fridge overnight. " +
                "Makes 8 generous servings and disappears in minutes.",
        )
    }
}

@Preview(name = "MediaViewer · video controls", showBackground = true, heightDp = 420)
@Composable
private fun PreviewMediaViewerVideoControls() {
    PreviewTheme {
        val hazeState = rememberHazeState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Color.DarkGray),
        ) {
            PlayPauseButton(
                isPlaying = false,
                ended = false,
                hazeState = hazeState,
                onClick = {},
                modifier = Modifier.align(Alignment.Center),
            )
            PlayerControlsBar(
                positionMs = 23_000L,
                durationMs = 95_000L,
                isPlaying = true,
                muted = false,
                hazeState = hazeState,
                onSeekTo = {},
                onToggleMute = {},
                onInteraction = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }
}

@Preview(name = "MediaViewer · empty", showBackground = true, heightDp = 640)
@Composable
private fun PreviewMediaViewerEmpty() {
    PreviewTheme {
        MediaViewerContent(
            entries = emptyList(),
            pagerState = rememberPagerState(initialPage = 0, pageCount = { 0 }),
            onClose = {},
        )
    }
}
