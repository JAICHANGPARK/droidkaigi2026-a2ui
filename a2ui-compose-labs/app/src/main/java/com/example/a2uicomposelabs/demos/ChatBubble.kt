package com.example.a2uicomposelabs.demos

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * One chat bubble, shared by every demo that has a transcript.
 *
 * The agent's side gets a copy button. It is the ordinary thing every chat app does, and here it
 * earns its place twice over: these bubbles carry the action payloads and the raw JSONL, which
 * are exactly what you want to paste into an issue, a test fixture, or a slide.
 */
@Composable
internal fun ChatBubble(
    text: String,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    /** What the copy button yields, when that is more than what is worth showing. */
    copyText: String = text,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color =
                if (fromUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            // fill = false keeps a short bubble short; the weight only caps how wide a long
            // one may grow, leaving room for the button beside it.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (!fromUser) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(copyText))
                    // Android 13 shows its own clipboard confirmation; a second one is noise.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy this reply",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
