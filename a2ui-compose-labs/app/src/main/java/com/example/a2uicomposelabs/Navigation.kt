package com.example.a2uicomposelabs

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.a2uicomposelabs.demos.AlbumDemo
import com.example.a2uicomposelabs.demos.AnalyticsDemo
import com.example.a2uicomposelabs.demos.AssistantDemo
import com.example.a2uicomposelabs.demos.ChatDemo
import com.example.a2uicomposelabs.demos.DemoHome
import com.example.a2uicomposelabs.demos.DiningDemo
import com.example.a2uicomposelabs.demos.FormDemo
import com.example.a2uicomposelabs.demos.LiveAgentDemo
import com.example.a2uicomposelabs.demos.PlaylistDemo
import com.example.a2uicomposelabs.androidxa2ui.SupportCsatDemo
import com.example.a2uicomposelabs.androidxa2ui.TwoDialectsDemo
import com.example.a2uicomposelabs.demos.SurveyDemo

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          DemoHome(onOpen = { key -> backStack.add(key) }, modifier = Modifier.safeDrawingPadding())
        }
        entry<ChatDemoKey> { ChatDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<FormDemoKey> { FormDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<PlaylistDemoKey> { PlaylistDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<SurveyDemoKey> { SurveyDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<AlbumDemoKey> { AlbumDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<AssistantDemoKey> { AssistantDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<AnalyticsDemoKey> { AnalyticsDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<DiningDemoKey> { DiningDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<LiveAgentDemoKey> {
          LiveAgentDemo(
            onOpenSettings = { backStack.add(SettingsKey) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TwoDialectsDemoKey> { TwoDialectsDemo(modifier = Modifier.safeDrawingPadding()) }
        entry<SupportCsatDemoKey> {
          SupportCsatDemo(
            onOpenSettings = { backStack.add(SettingsKey) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<SettingsKey> { SettingsScreen(modifier = Modifier.safeDrawingPadding()) }
      },
  )
}
