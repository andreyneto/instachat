package com.aneto.instachat.ui.thread.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aneto.instachat.R
import com.aneto.instachat.ui.preview.PreviewTheme

/** Matches Instagram's native quick-reaction tray, so each maps to a single DOM click. */
val DefaultQuickReactions = listOf("❤", "😂", "😮", "😢", "😡", "👍")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionSheet(onDismiss: () -> Unit, onPick: (String) -> Unit, reactions: List<String> = DefaultQuickReactions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        ReactionSheetContent(reactions = reactions, onPick = onPick)
    }
}

@Composable
fun ReactionSheetContent(
    reactions: List<String> = DefaultQuickReactions,
    onPick: (String) -> Unit,
    titleText: String = stringResource(R.string.reaction_sheet_title),
    buttonSize: Dp = 52.dp,
    emojiFontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    pressedScale: Float = 1.35f,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reactions.forEach { emoji ->
                EmojiButton(
                    emoji = emoji,
                    onClick = { onPick(emoji) },
                    size = buttonSize,
                    emojiFontSize = emojiFontSize,
                    pressedScale = pressedScale,
                )
            }
            AddButton(onClick = { onPick("❤") })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmojiButton(
    emoji: String,
    onClick: () -> Unit,
    size: Dp = 52.dp,
    emojiFontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    pressedScale: Float = 1.35f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "emojiScale",
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = emojiFontSize,
            modifier = Modifier.scale(scale),
        )
    }
}

@Composable
internal fun AddButton(onClick: () -> Unit, size: Dp = 44.dp) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CircleShape,
        modifier = Modifier.size(size).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(name = "ReactionSheetContent", showBackground = true)
@Composable
private fun PreviewReactionSheetContent() {
    PreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ReactionSheetContent(onPick = {})
        }
    }
}

@Preview(name = "ReactionSheetContent · dark", showBackground = true)
@Composable
private fun PreviewReactionSheetContentDark() {
    PreviewTheme(dark = true) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ReactionSheetContent(onPick = {})
        }
    }
}

@Preview(name = "ReactionSheetContent · conjunto custom", showBackground = true)
@Composable
private fun PreviewReactionSheetContentCustom() {
    PreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ReactionSheetContent(
                reactions = listOf("🚀", "🥰", "🤣", "💯"),
                onPick = {},
            )
        }
    }
}

@Preview(name = "EmojiButton · tamanhos", showBackground = true)
@Composable
private fun PreviewEmojiButton() {
    PreviewTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiButton(emoji = "❤", onClick = {}, size = 40.dp, emojiFontSize = 24.sp)
            EmojiButton(emoji = "❤", onClick = {}, size = 52.dp)
            EmojiButton(emoji = "❤", onClick = {}, size = 64.dp, emojiFontSize = 40.sp)
        }
    }
}

@Preview(name = "AddButton", showBackground = true)
@Composable
private fun PreviewAddButton() {
    PreviewTheme {
        Box(modifier = Modifier.padding(16.dp)) { AddButton(onClick = {}) }
    }
}
