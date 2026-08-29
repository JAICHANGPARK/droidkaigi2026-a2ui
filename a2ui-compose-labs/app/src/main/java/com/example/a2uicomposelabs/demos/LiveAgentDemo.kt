package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.replayAsset
import com.example.a2uicomposelabs.agent.AgentChunk
import com.example.a2uicomposelabs.agent.a2uiSystemPrompt
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val FALLBACK_ASSET = "live_agent_fallback.jsonl"

/** One line off the wire, and what the renderer decided about it. */
private data class WireLine(val json: String, val rejection: String?)

/**
 * Demo 4 — the full loop, with a real model on the other end.
 *
 * The catalog does double duty here: [BasicCatalogSchema] is serialized into Gemini's system
 * prompt so it knows what it may draw, and the same object is handed to [A2uiClient] so every
 * message that comes back is validated against it. Whatever the model invents anyway shows up
 * in the wire log as rejected, and never reaches the screen.
 */
@Composable
fun LiveAgentDemo(onOpenSettings: () -> Unit = {}, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = rememberAgentSettings()
  // Rebuilt whenever Settings changes, so a key saved on the next screen takes effect here.
  val agent = remember(settings.effectiveApiKey, settings.effectiveModel) {
    settings.newAgent()
  }
  val client = remember { A2uiClient(BasicCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) }
  val systemPrompt = remember { a2uiSystemPrompt(BasicCatalogSchema) }
  val wire = remember { mutableStateListOf<WireLine>() }
  val coroutineScope = rememberCoroutineScope()

  var input by remember { mutableStateOf("") }
  var prose by remember { mutableStateOf("") }
  var failure by remember { mutableStateOf<String?>(null) }
  var running by remember { mutableStateOf(false) }
  // User intent; a missing key forces replay regardless.
  var preferLive by remember { mutableStateOf(true) }
  var job by remember { mutableStateOf<Job?>(null) }
  val live = preferLive && agent.isConfigured

  // Applies one line, records whether the renderer let it through, and returns the reason it
  // did not. That string is what goes back to the model as the tool's result.
  fun applyAndLog(line: String): String? {
    val before = client.errors.size
    client.apply(line)
    val rejection = client.errors.drop(before).joinToString("; ").ifEmpty { null }
    wire += WireLine(line, rejection)
    return rejection
  }

  fun send() {
    job?.cancel()
    client.surfaces.clear()
    client.errors.clear()
    wire.clear()
    prose = ""
    failure = null
    running = true
    val prompt = input.ifBlank { "Plan a two-day trip to Kyoto" }
    job = coroutineScope.launch {
      try {
        if (live && agent.isConfigured) {
          // UI arrives as a tool call, prose as text — the two never have to be pulled apart.
          val spoken = StringBuilder()
          agent.streamUi(
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            applyUi = { json -> applyAndLog(json) },
          ).collect { chunk ->
            when (chunk) {
              is AgentChunk.Prose -> {
                spoken.append(chunk.text)
                prose = spoken.toString()
              }
              // Already applied by applyUi; the wire log picked it up there.
              is AgentChunk.Ui -> Unit
            }
          }
        } else {
          prose = "Replaying a recorded agent response — no network."
          client.replayAsset(context, FALLBACK_ASSET, lineDelayMs = 500L) { line ->
            applyAndLog(line)
          }
        }
      } catch (e: Exception) {
        failure = e.message ?: e::class.simpleName
      } finally {
        running = false
      }
    }
  }

  Column(modifier = modifier.fillMaxSize().imePadding().padding(16.dp)) {
    Text("Live agent", style = MaterialTheme.typography.titleLarge)
    Text(
      "One catalog, two jobs: it is serialized into the system prompt, and it validates " +
        "every message that comes back.",
      style = MaterialTheme.typography.bodySmall,
    )

    Row(
      modifier = Modifier.padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilterChip(
        selected = live,
        onClick = { preferLive = true },
        enabled = agent.isConfigured,
        label = { Text(if (agent.isConfigured) "Live · ${settings.effectiveModel}" else "Live") },
      )
      FilterChip(
        selected = !live,
        onClick = { preferLive = false },
        label = { Text("Replay") },
      )
      TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
    if (!agent.isConfigured) {
      Text(
        "No API key yet — open Settings to add one. Replay works without it.",
        style = MaterialTheme.typography.bodySmall,
      )
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (prose.isNotEmpty()) {
        item { Text(prose, style = MaterialTheme.typography.bodyMedium) }
      }
      failure?.let { message ->
        item {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                message,
                modifier = Modifier.weight(1f).padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              val clipboard = LocalClipboardManager.current
              TextButton(onClick = { clipboard.setText(AnnotatedString(message)) }) { Text("Copy") }
            }
          }
        }
      }
      if (client.errors.isNotEmpty()) {
        item { RejectionReport(client.errors) }
      }
      items(client.surfaces.keys.toList()) { surfaceId ->
        client.surfaces[surfaceId]?.let { surface ->
          A2uiSurface(
            state = surface,
            registry = registry,
            onAction = { action -> prose = "action → agent:\n${prettyJson(action.toJson())}" },
          )
        }
      }
      if (wire.isNotEmpty()) {
        item {
          HorizontalDivider(Modifier.padding(top = 8.dp))
          Text(
            "Wire log — ${wire.count { it.rejection == null }} applied, " +
              "${wire.count { it.rejection != null }} rejected",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
      items(wire) { line -> WireRow(line) }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Plan a two-day trip to Kyoto…") },
      )
      Button(onClick = ::send, enabled = !running) {
        if (running) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
          Text("Send")
        }
      }
    }
  }
}

@Composable
private fun WireRow(line: WireLine) {
  val rejected = line.rejection != null
  Surface(
    color =
      if (rejected) MaterialTheme.colorScheme.errorContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        (if (rejected) "✗ " else "✓ ") + line.json,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      line.rejection?.let {
        Text(
          it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}
