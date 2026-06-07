package com.aneto.instachat.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aneto.instachat.ui.theme.AppShapes
import com.aneto.instachat.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreviewTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
fun PreviewSurface(dark: Boolean = false, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    PreviewTheme(dark = dark) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding),
        ) {
            content()
        }
    }
}
