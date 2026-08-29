package com.example.a2uicomposelabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.agent.AgentProvider
import com.example.a2uicomposelabs.agent.AnthropicAgent
import com.example.a2uicomposelabs.agent.GeminiAgent
import com.example.a2uicomposelabs.agent.OpenAiAgent
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import kotlinx.coroutines.launch

/**
 * Runtime configuration for the live-agent demo.
 *
 * A key entered here is stored in app-private preferences and takes precedence over anything
 * baked in at build time — so the app can be built with no key at all, which is the whole
 * point: nothing to commit, nothing to extract from the APK.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
  val settings = rememberAgentSettings()
  val coroutineScope = rememberCoroutineScope()

  var keyInput by remember { mutableStateOf(settings.apiKey) }
  var modelInput by remember { mutableStateOf(settings.model) }
  var keyVisible by remember { mutableStateOf(false) }
  var models by remember { mutableStateOf<List<String>>(emptyList()) }
  var loadingModels by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var isError by remember { mutableStateOf(false) }

  fun report(text: String, error: Boolean) {
    message = text
    isError = error
  }

  Column(
    modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Text(
      "Used by demo 4. Stored in this app's private preferences — not encrypted at rest, " +
        "and never written to a file the repository tracks.",
      style = MaterialTheme.typography.bodySmall,
    )

    HorizontalDivider()

    Text("Provider", style = MaterialTheme.typography.titleMedium)
    Text(
      "The renderer never learns which one drew the screen. Each provider keeps its own key.",
      style = MaterialTheme.typography.bodySmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      AgentProvider.entries.forEach { option ->
        FilterChip(
          selected = settings.provider == option,
          onClick = {
            settings.selectProvider(option)
            keyInput = settings.apiKey
            modelInput = settings.model
            models = emptyList()
            message = null
          },
          label = { Text(option.label) },
        )
      }
    }

    HorizontalDivider()

    Text("${settings.provider.label} API key", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
      value = keyInput,
      onValueChange = { keyInput = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("API key") },
      placeholder = { Text(settings.provider.keyHint) },
      singleLine = true,
      visualTransformation =
        if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        TextButton(onClick = { keyVisible = !keyVisible }) {
          Text(if (keyVisible) "Hide" else "Show")
        }
      },
    )
    Text(currentKeySource(settings.apiKey.isNotBlank(), settings.usingBuildKey), style = MaterialTheme.typography.bodySmall)

    HorizontalDivider()

    Text("Model", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
      value = modelInput,
      onValueChange = { modelInput = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Model") },
      placeholder = { Text(settings.provider.defaultModel) },
      singleLine = true,
    )

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        enabled = !loadingModels,
        onClick = {
          val key = keyInput.ifBlank { settings.effectiveApiKey }
          if (key.isBlank()) {
            report("Enter an API key first.", error = true)
            return@OutlinedButton
          }
          loadingModels = true
          message = null
          coroutineScope.launch {
            try {
              // Ask the API rather than guessing: model names outlive no talk.
              models = when (settings.provider) {
                AgentProvider.GEMINI -> GeminiAgent.listModels(key)
                AgentProvider.OPENAI -> OpenAiAgent.listModels(key)
                AgentProvider.ANTHROPIC -> AnthropicAgent.listModels(key)
              }
              report("${models.size} models available for this key.", error = false)
            } catch (e: Exception) {
              models = emptyList()
              report(e.message ?: "Could not list models.", error = true)
            } finally {
              loadingModels = false
            }
          }
        },
      ) {
        if (loadingModels) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
          Text("List models for this key")
        }
      }
    }

    if (models.isNotEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { name ->
          FilterChip(
            selected = modelInput == name,
            onClick = { modelInput = name },
            label = { Text(name) },
          )
        }
      }
    }

    HorizontalDivider()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {
          settings.save(keyInput, modelInput)
          keyInput = settings.apiKey
          modelInput = settings.model
          report("Saved. Demo 4 will use it on the next send.", error = false)
        }
      ) { Text("Save") }
      OutlinedButton(
        onClick = {
          settings.clear()
          keyInput = ""
          modelInput = ""
          models = emptyList()
          report("Cleared.", error = false)
        }
      ) { Text("Clear") }
    }

    message?.let { text ->
      Surface(
        color =
          if (isError) MaterialTheme.colorScheme.errorContainer
          else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text,
          modifier = Modifier.padding(12.dp),
          style = MaterialTheme.typography.bodySmall,
          color =
            if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Text(
      "A key on a device is extractable by whoever holds the device. This screen exists so " +
        "the demo needs no key in the repository or the APK; a production app calls the model " +
        "from its own backend instead.",
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

private fun currentKeySource(hasStoredKey: Boolean, usingBuildKey: Boolean): String = when {
  hasStoredKey -> "In use: the key saved on this device."
  usingBuildKey -> "In use: the build-time key from local.properties. Saving one here overrides it."
  else -> "No key configured — demo 4 stays in Replay mode."
}
