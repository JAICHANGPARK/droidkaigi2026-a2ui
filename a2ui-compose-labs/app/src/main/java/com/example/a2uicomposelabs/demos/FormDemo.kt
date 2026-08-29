package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.A2uiAction
import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.replayAsset

@Composable
fun FormDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val client = remember { A2uiClient(BasicCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) }
  var lastAction by remember { mutableStateOf<A2uiAction?>(null) }

  LaunchedEffect(Unit) { client.replayAsset(context, "contact_form.jsonl") }

  Column(
    modifier =
      modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Contact form", style = MaterialTheme.typography.titleLarge)
    Text(
      "This surface is built from streamed JSONL messages. Type in the fields " +
        "(two-way binding, local only), then press Submit to see the action message.",
      style = MaterialTheme.typography.bodySmall,
    )
    val surface = client.surfaces["contact_form"]
    if (surface == null) {
      Text("waiting for agent…", style = MaterialTheme.typography.bodyMedium)
    } else {
      A2uiSurface(
        state = surface,
        registry = registry,
        onAction = { lastAction = it },
        catalog = BasicCatalogSchema,
      )
    }
  }

  lastAction?.let { action ->
    AlertDialog(
      onDismissRequest = { lastAction = null },
      confirmButton = { TextButton(onClick = { lastAction = null }) { Text("OK") } },
      title = { Text("action → agent") },
      text = { Text(prettyJson(action.toJson()), style = MaterialTheme.typography.bodySmall) },
    )
  }
}
