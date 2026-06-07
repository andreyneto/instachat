package com.aneto.instachat.ui.thread.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aneto.instachat.R
import com.aneto.instachat.ui.preview.PreviewTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Debug-friendly inspector for items the mapper couldn't render ("Post unavailable" etc.).
 * Shows every field of the raw item JSON flattened to dot-paths, plus a button to open
 * the most promising URL found in the payload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawItemDialog(title: String, rawJson: String, onDismiss: () -> Unit) {
    val fields = remember(rawJson) { flattenJson(rawJson) }
    val browserUrl = remember(fields) { pickBrowserUrl(fields) }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.DataObject,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(rawJson)) }) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.raw_item_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (fields.isEmpty()) {
                                Text(
                                    text = rawJson.ifBlank { stringResource(R.string.raw_item_empty) },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                fields.forEach { (path, value) ->
                                    Column {
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (browserUrl != null) {
                    Button(
                        onClick = { uriHandler.openUri(browserUrl) },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Icon(
                            Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.raw_item_open_browser),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.raw_item_close))
                }
            }
        }
    }
}

/** Flattens a JSON document into (dot.path, value) pairs; empty list when unparseable. */
private fun flattenJson(raw: String): List<Pair<String, String>> = runCatching {
    val out = mutableListOf<Pair<String, String>>()
    flatten(Json.parseToJsonElement(raw), path = "", out = out)
    out.toList()
}.getOrDefault(emptyList())

private fun flatten(element: JsonElement, path: String, out: MutableList<Pair<String, String>>) {
    when (element) {
        is JsonObject -> element.forEach { (key, value) ->
            flatten(value, if (path.isEmpty()) key else "$path.$key", out)
        }

        is JsonArray -> element.forEachIndexed { index, value ->
            flatten(value, "$path[$index]", out)
        }

        is JsonPrimitive -> out += path.ifEmpty { "·" } to element.content
    }
}

/**
 * Prefers a permalink built from a `code` shortcode, then an instagram.com URL,
 * then any URL found (CDN media as last resort).
 */
private fun pickBrowserUrl(fields: List<Pair<String, String>>): String? {
    val shortcode = fields.firstOrNull { (path, value) ->
        (path == "code" || path.endsWith(".code")) && value.matches(Regex("[A-Za-z0-9_-]{5,}"))
    }?.second
    if (shortcode != null) return "https://www.instagram.com/p/$shortcode/"
    val urls = fields.map { it.second }.filter { it.startsWith("http://") || it.startsWith("https://") }
    return urls.firstOrNull { "instagram.com" in it && "cdninstagram" !in it && "fbcdn" !in it }
        ?: urls.firstOrNull()
}

@Preview(name = "RawItemDialog", showBackground = true, heightDp = 640)
@Composable
private fun PreviewRawItemDialog() {
    PreviewTheme {
        RawItemDialog(
            title = "Post unavailable",
            rawJson = """
                {
                  "item_id": "31415926535",
                  "item_type": "xma_media_share",
                  "xma_media_share": [
                    {
                      "header_title_text": "@chef.mari",
                      "target_url": "https://www.instagram.com/p/AbCdEf123/",
                      "preview_url_info": null
                    }
                  ]
                }
            """.trimIndent(),
            onDismiss = {},
        )
    }
}
