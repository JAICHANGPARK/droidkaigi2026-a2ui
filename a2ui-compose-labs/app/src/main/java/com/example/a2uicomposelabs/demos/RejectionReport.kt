package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.runtime.A2uiRejection

/**
 * Shows what the renderer refused, with the offending payload one tap away from the
 * clipboard. A rejection is only useful if you can read the message that caused it, and on
 * a phone that means being able to paste it somewhere.
 */
@Composable
fun RejectionReport(
  rejections: List<A2uiRejection>,
  modifier: Modifier = Modifier,
  title: String = "Rejected before rendering",
  /** The model's untouched output, when there was one. Copyable alongside the reasons. */
  rawOutput: String = "",
) {
  if (rejections.isEmpty()) return
  val clipboard = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }

  Card(modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "$title (${rejections.size})",
          style = MaterialTheme.typography.labelMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (rawOutput.isNotEmpty()) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(rawOutput)) }) {
              Text("Copy raw")
            }
          }
          TextButton(
            onClick = {
              clipboard.setText(AnnotatedString(rejections.toReport()))
              copied = true
            }
          ) { Text(if (copied) "Copied" else "Copy details") }
        }
      }
      rejections.forEach { rejection ->
        Text(rejection.reason, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

/** Reason plus the payload that caused it — the thing worth pasting into a bug report. */
internal fun List<A2uiRejection>.toReport(): String = buildString {
  this@toReport.forEachIndexed { index, rejection ->
    if (index > 0) appendLine()
    appendLine("[${index + 1}] ${rejection.reason}")
    rejection.detail?.let { appendLine(it) }
  }
}
