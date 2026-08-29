package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.a2uicomposelabs.AlbumDemoKey
import com.example.a2uicomposelabs.AnalyticsDemoKey
import com.example.a2uicomposelabs.AssistantDemoKey
import com.example.a2uicomposelabs.ChatDemoKey
import com.example.a2uicomposelabs.DiningDemoKey
import com.example.a2uicomposelabs.FormDemoKey
import com.example.a2uicomposelabs.LiveAgentDemoKey
import com.example.a2uicomposelabs.PlaylistDemoKey
import com.example.a2uicomposelabs.SettingsKey
import com.example.a2uicomposelabs.SupportCsatDemoKey
import com.example.a2uicomposelabs.SurveyDemoKey
import com.example.a2uicomposelabs.TwoDialectsDemoKey

@Composable
fun DemoHome(onOpen: (NavKey) -> Unit, modifier: Modifier = Modifier) {
  Column(
    // The list outgrew the screen once there were six demos; scroll inside the padding so
    // the cards keep their margins and the last one is still reachable.
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("A2UI Compose Labs", style = MaterialTheme.typography.headlineMedium)
    Text(
      "One assistant that answers with whatever UI the question needs, plus small demos that " +
        "each isolate a single mechanism. The assistant is the point; the rest is how it works.",
      style = MaterialTheme.typography.bodyMedium,
    )

    SectionHeader("The whole idea")
    DemoCard(
      title = "Assistant ★",
      subtitle =
        "One catalog, one chat: device health, sales charts, albums, playlists, surveys, " +
          "table bookings and food orders",
    ) { onOpen(AssistantDemoKey) }

    SectionHeader("One mechanism each")
    DemoCard(
      title = "1. Chat assistant",
      subtitle = "Agent UI grows inside a chat bubble (streaming replay)",
    ) { onOpen(ChatDemoKey) }
    DemoCard(
      title = "2. Contact form",
      subtitle = "Spec-style form: two-way binding + action round-trip",
    ) { onOpen(FormDemoKey) }
    DemoCard(
      title = "3. Playlist builder",
      subtitle = "Custom catalog (SongRow, PlaylistCard) + real tracks from a keyless API",
    ) { onOpen(PlaylistDemoKey) }
    DemoCard(
      title = "4. Survey generator",
      subtitle = "Structure differs per request — the case A2UI actually wins",
    ) { onOpen(SurveyDemoKey) }
    DemoCard(
      title = "5. Album browser",
      subtitle = "Two surfaces, template lists, an action the app handles itself",
    ) { onOpen(AlbumDemoKey) }
    DemoCard(
      title = "6. Analytics",
      subtitle = "Canvas charts, app-owned numbers, tap a slice to drill down",
    ) { onOpen(AnalyticsDemoKey) }
    DemoCard(
      title = "7. Dining",
      subtitle =
        "One chat, saved per conversation: book a table, order delivery with quantity " +
          "steppers, pay, watch the rider live, then rate the food",
    ) { onOpen(DiningDemoKey) }
    DemoCard(
      title = "8. Live agent",
      subtitle = "Gemini writes the UI; the catalog schema validates every message",
    ) { onOpen(LiveAgentDemoKey) }

    SectionHeader("The library, and us")
    DemoCard(
      title = "9. Two dialects",
      subtitle =
        "The booking form and the survey rendered twice: androidx.a2ui on a v0.9.1 wire, " +
          "our renderer on a v1.0 one",
    ) { onOpen(TwoDialectsDemoKey) }
    DemoCard(
      title = "10. Support satisfaction",
      subtitle =
        "A CSAT form written per closed ticket and rendered by androidx.a2ui — and a " +
          "follow-up the app writes into the same surface when the rating is low",
    ) { onOpen(SupportCsatDemoKey) }

    SectionHeader("Setup")
    DemoCard(
      title = "Settings",
      subtitle = "API key and model for the live demos",
    ) { onOpen(SettingsKey) }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp),
  )
}

@Composable
private fun DemoCard(title: String, subtitle: String, onClick: () -> Unit) {
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
  }
}
