package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.replayAsset
import kotlinx.coroutines.launch

private sealed interface ChatItem {
  data class UserText(val text: String) : ChatItem
  data class AgentText(val text: String) : ChatItem
  data class AgentSurface(val surfaceId: String) : ChatItem
}

@Composable
fun ChatDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val client = remember { A2uiClient(BasicCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) }
  val transcript = remember { mutableStateListOf<ChatItem>() }
  var input by remember { mutableStateOf("") }
  var replayStarted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  Column(modifier = modifier.fillMaxSize().imePadding().padding(16.dp)) {
    Text("Chat assistant", style = MaterialTheme.typography.titleLarge)
    LazyColumn(
      modifier = Modifier.weight(1f).padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(transcript) { item ->
        when (item) {
          is ChatItem.UserText -> ChatBubble(item.text, fromUser = true)
          is ChatItem.AgentText -> ChatBubble(item.text, fromUser = false)
          is ChatItem.AgentSurface -> {
            val surface = client.surfaces[item.surfaceId]
            if (surface == null) {
              ChatBubble("…", fromUser = false)
            } else {
              A2uiSurface(
                state = surface,
                registry = registry,
                onAction = { action ->
                  transcript.add(ChatItem.AgentText("action → agent:\n${prettyJson(action.toJson())}"))
                },
              )
            }
          }
        }
      }
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Plan a weekend in Tokyo…") },
      )
      Button(
        onClick = {
          if (replayStarted) return@Button
          replayStarted = true
          transcript.add(ChatItem.UserText(input.ifBlank { "Plan a weekend in Tokyo" }))
          transcript.add(ChatItem.AgentSurface("trip_card"))
          coroutineScope.launch { client.replayAsset(context, "chat_demo.jsonl") }
        },
      ) {
        Text("Send")
      }
    }
  }
}

