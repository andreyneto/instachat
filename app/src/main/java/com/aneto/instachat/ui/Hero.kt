package com.aneto.instachat.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Escopos da transição hero (shared element) entre destinos de navegação. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marca o elemento como hero compartilhado entre telas (bubble ↔ media viewer).
 * No-op quando não há escopo disponível (previews) ou quando [key] é nula.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.heroMedia(key: String?): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@heroMedia.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
