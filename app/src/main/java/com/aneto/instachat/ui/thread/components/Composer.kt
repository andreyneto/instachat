package com.aneto.instachat.ui.thread.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aneto.instachat.R
import com.aneto.instachat.ui.preview.PreviewTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun Composer(
    enabled: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    sendButtonSize: Dp = 56.dp,
    fieldHorizontalPadding: Dp = 24.dp,
    fieldVerticalPadding: Dp = 18.dp,
    rowHorizontalPadding: Dp = 14.dp,
    rowVerticalPadding: Dp = 10.dp,
    applyNavigationBarPadding: Boolean = true,
    hazeState: HazeState? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    val canSend = enabled && text.isNotBlank()
    val blurred = hazeState != null

    Surface(
        color = if (blurred) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = rowHorizontalPadding, vertical = rowVerticalPadding)
            .then(
                if (blurred) {
                    Modifier
                        .clip(CircleShape)
                        .hazeEffect(state = hazeState!!, style = HazeMaterials.thin())
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = fieldHorizontalPadding,
                        vertical = fieldVerticalPadding,
                    ),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.thread_input_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Enter &&
                                    !event.isShiftPressed
                                ) {
                                    if (canSend) {
                                        onSend(text)
                                        text = ""
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend) {
                                    onSend(text)
                                    text = ""
                                }
                            },
                        ),
                        enabled = enabled,
                    )
                }
            }

            FilledIconButton(
                onClick = {
                    if (canSend) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = canSend,
                shapes = IconButtonDefaults.shapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(12.dp),
                ),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(sendButtonSize),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.thread_send),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Preview(name = "Composer · empty", showBackground = true)
@Composable
private fun PreviewComposerEmpty() {
    PreviewTheme {
        Composer(enabled = true, onSend = {}, applyNavigationBarPadding = false)
    }
}

@Preview(name = "Composer · com texto", showBackground = true)
@Composable
private fun PreviewComposerFilled() {
    PreviewTheme {
        Composer(
            enabled = true,
            onSend = {},
            initialText = "Message ready to send",
            applyNavigationBarPadding = false,
        )
    }
}

@Preview(name = "Composer · desabilitado", showBackground = true)
@Composable
private fun PreviewComposerDisabled() {
    PreviewTheme {
        Composer(
            enabled = false,
            onSend = {},
            initialText = "Enviando...",
            applyNavigationBarPadding = false,
        )
    }
}

@Preview(name = "Composer · dark", showBackground = true)
@Composable
private fun PreviewComposerDark() {
    PreviewTheme(dark = true) {
        Composer(
            enabled = true,
            onSend = {},
            initialText = "escuro fica assim",
            applyNavigationBarPadding = false,
        )
    }
}
