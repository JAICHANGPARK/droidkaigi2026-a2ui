package com.example.a2uicomposelabs.demos

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.A2uiAction
import com.example.a2uicomposelabs.a2ui.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.SurfaceState
import com.example.a2uicomposelabs.agent.AgentChunk
import com.example.a2uicomposelabs.agent.a2uiSystemPrompt
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import com.example.a2uicomposelabs.analytics.SalesData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Demo 7 — ask a question, get the chart that answers it.
 *
 * This is the demo that earns A2UI its place, and it earns it for a reason the playlist and
 * album screens cannot: **the shape of the answer depends on the question.** "Sales by region"
 * is a bar chart. "How did the year go?" is a line with headline numbers above it. "What is
 * selling?" is a donut with a legend. You cannot pre-build a screen per question, and a
 * dashboard that tries ends up as forty screens nobody opens.
 *
 * It also makes the data rule impossible to hand-wave. A model that invents a survey question is
 * annoying; a model that invents a revenue figure is dangerous. So here the agent is told, in as
 * many words, that it does not have the numbers — it may only say *how* to display them:
 *
 * ```json
 * {"updateDataModel":{"path":"/request","value":{"metric":"sales","dimension":"region","top":5}}}
 * ```
 *
 * The app reads that, aggregates [SalesData] on the device, and fills `/chart`. Three words
 * crossed the wire — "sales", "region", "bar". Every number on screen came from the app.
 *
 * One more thing worth watching: the measure chips. Switching Revenue to Orders re-aggregates
 * locally and redraws, with **no agent round trip at all** — the surface the agent built is a
 * live document, not a screenshot.
 */

private const val ANALYTICS_CATALOG_ID = "app.analytics.catalog/v1"

internal val AnalyticsCatalogSchema: A2uiCatalog =
  BasicCatalogSchema.withId(ANALYTICS_CATALOG_ID) + ChartCatalogSchema

internal val AnalyticsCatalog: Map<String, A2uiComponentFactory> = BasicCatalog + ChartCatalog

private val ANALYTICS_RULES = """
You are looking at a sales dashboard. You do NOT have the data and must never state, guess or
invent a number, a total or a percentage — not in prose, and never as a literal in a component.

Say how to display it instead, by writing a request into the data model BEFORE the components:

{"version":"v1.0","updateDataModel":{"surfaceId":"<surface>","path":"/request","value":{"metric":"sales","dimension":"region","top":5}}}

  metric    = "sales" or "orders"
  dimension = "region", "category" or "month"

The app aggregates it and fills:
  /chart = {"labels": [...], "values": [...]}     two arrays of equal length
  /stats = [{"label","value","delta"}, ...]       headline figures

Then choose the chart that fits the question:
  comparing named categories  -> BarChart
  anything across time        -> LineChart, and put a List of StatTile above it
  share of a total            -> PieChart with donut true, under about six slices

Bind labels to {"path":"/chart/labels"} and values to {"path":"/chart/values"}. Add a
ChoicePicker bound to {"path":"/metric"} with options "sales" and "orders" so the reader can
switch measure without asking you again.
""".trimIndent()

private data class AnalyticsPreset(val chip: String, val prompt: String, val asset: String)

private val AnalyticsPresets = listOf(
  AnalyticsPreset("By region", "Which regions sell the most?", "analytics_region.jsonl"),
  AnalyticsPreset("This year", "How did the year go month by month?", "analytics_trend.jsonl"),
  AnalyticsPreset("What sells", "What share of revenue does each category take?", "analytics_breakdown.jsonl"),
)

private fun offlineAnalyticsAsset(prompt: String): String {
  val p = prompt.lowercase()
  return when {
    listOf("month", "trend", "year", "추이", "월별").any(p::contains) -> "analytics_trend.jsonl"
    listOf("share", "category", "breakdown", "비중", "카테고리").any(p::contains) ->
      "analytics_breakdown.jsonl"
    else -> "analytics_region.jsonl"
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = rememberAgentSettings()
  val agent = remember(settings.effectiveApiKey, settings.effectiveModel) {
    settings.newAgent()
  }
  val client = remember { A2uiClient(AnalyticsCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) + AnalyticsCatalog }
  val coroutineScope = rememberCoroutineScope()

  var prompt by remember { mutableStateOf(AnalyticsPresets.first().prompt) }
  var running by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<String?>(null) }
  var turn by remember { mutableStateOf(0) }
  var lastAction by remember { mutableStateOf<A2uiAction?>(null) }
  var useRecorded by remember { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }

  val surfaceId = "chart$turn"
  val surface = client.surfaces[surfaceId]

  /** Aggregates whatever the agent asked for. Returns the dimension it served, if any. */
  fun fillFrom(request: JsonObject, target: SurfaceState): String {
    val metric = (request["metric"] as? JsonPrimitive)?.contentOrNull ?: "sales"
    val dimension = (request["dimension"] as? JsonPrimitive)?.contentOrNull ?: "region"
    val top = (request["top"] as? JsonPrimitive)?.intOrNull ?: 6
    val filterRegion = (request["filterRegion"] as? JsonPrimitive)?.contentOrNull
    val filterCategory = (request["filterCategory"] as? JsonPrimitive)?.contentOrNull
    val series = SalesData.series(metric, dimension, top, filterRegion, filterCategory)

    target.updateData(
      "/chart",
      buildJsonObject {
        put("labels", JsonArray(series.labels.map(::JsonPrimitive)))
        put("values", JsonArray(series.values.map(::JsonPrimitive)))
      },
    )
    target.updateData(
      "/stats",
      JsonArray(
        SalesData.summary(metric).map { (label, value) ->
          buildJsonObject {
            put("label", label)
            put("value", value)
            put(
              "delta",
              if (label == "Weakest month") SalesData.monthOverMonth(metric, value) else "",
            )
          }
        }
      ),
    )
    // Remember the shape so a measure change can re-run the same aggregation.
    target.updateData(
      "/shape",
      buildJsonObject {
        put("dimension", dimension)
        put("top", top)
        filterRegion?.let { put("filterRegion", it) }
        filterCategory?.let { put("filterCategory", it) }
      },
    )
    target.updateData("/request", null)
    return dimension
  }

  fun fulfil(target: SurfaceState): Boolean {
    val request = target.read("/request") as? JsonObject ?: return false
    fillFrom(request, target)
    return true
  }

  suspend fun playRecorded(asset: String, id: String) {
    val lines = context.assets.open(asset).bufferedReader().useLines { it.toList() }
    for (line in lines.filter(String::isNotBlank)) {
      client.apply(line.replace("__turn__", id))
      client.surfaces[id]?.let { fulfil(it) }
      delay(300L)
    }
  }

  /**
   * A tapped slice or bar. The agent is not consulted: the app narrows its own data and swaps
   * the component in place — a pie of categories becomes a line of months for the one tapped.
   * The app is allowed to speak A2UI too; nothing about the protocol says only agents may.
   */
  fun drillDown(action: A2uiAction, target: SurfaceState, id: String) {
    val label = (action.context["label"] as? JsonPrimitive)?.contentOrNull ?: return
    val shape = target.read("/shape") as? JsonObject ?: return
    val dimension = (shape["dimension"] as? JsonPrimitive)?.contentOrNull ?: return
    if (dimension == "month") {
      status = "Already showing months — nothing to drill into."
      return
    }
    val metric = (target.read("/metric") as? JsonArray)
      ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull } ?: "sales"

    fillFrom(
      buildJsonObject {
        put("metric", metric)
        put("dimension", "month")
        put("top", 12)
        if (dimension == "category") put("filterCategory", label) else put("filterRegion", label)
      },
      target,
    )
    client.apply(
      """{"version":"v1.0","updateComponents":{"surfaceId":"$id","components":[""" +
        """{"id":"chart","component":"LineChart","title":"$label — by month",""" +
        """"labels":{"path":"/chart/labels"},"values":{"path":"/chart/values"}}]}}"""
    )
    status = "Drilled into $label. The app swapped the component — no agent, no network."
  }

  fun ask() {
    job?.cancel()
    // Each turn reports its own failures. Without this the last error of the session stays
    // pinned under the input box, over turns that went fine.
    client.errors.clear()
    turn += 1
    val id = "chart$turn"
    running = true
    status = null
    job = coroutineScope.launch {
      try {
        if (agent.isConfigured && !useRecorded) {
          val errorsBefore = client.errors.size
          agent.streamUi(
            systemPrompt = a2uiSystemPrompt(AnalyticsCatalogSchema, id, ANALYTICS_RULES),
            userPrompt = prompt,
            surfaceId = id,
            // Whatever the catalog refused goes back as the tool's result, so the model
            // gets to rewrite it instead of the app quietly falling back to a recording.
            applyUi = { json ->
              val before = client.errors.size
              client.apply(json)
              client.surfaces[id]?.let { fulfil(it) }
              client.errors.drop(before).firstOrNull()?.toString()
            },
          ).collect { chunk ->
            // The chart demo has no prose panel; the screen is the whole answer.
            if (chunk is AgentChunk.Ui) client.surfaces[id]?.let { fulfil(it) }
          }
          val built = client.surfaces[id]?.components?.size ?: 0
          if (client.errors.size > errorsBefore || built <= 1) {
            status = "The model's UI was rejected, so this is a recorded answer."
            playRecorded(offlineAnalyticsAsset(prompt), id)
          } else {
            status = "Gemini chose the chart; every number came from the app."
          }
        } else {
          status =
            if (agent.isConfigured) "Recorded answer — same surfaces, no model in the loop."
            else "No API key — replaying a recorded answer. The numbers are still the app's."
          playRecorded(offlineAnalyticsAsset(prompt), id)
        }
      } catch (e: Exception) {
        status = "Failed: ${e.message ?: e::class.simpleName}"
      } finally {
        running = false
      }
    }
  }

  // The measure chips are an ordinary two-way binding, so switching them changes /metric and
  // this re-aggregates on the device. No agent, no network, no new A2UI message.
  val metric = surface?.read("/metric")
  LaunchedEffect(surfaceId, metric) {
    val target = surface ?: return@LaunchedEffect
    val chosen = (target.read("/metric") as? JsonArray)
      ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull } ?: return@LaunchedEffect
    val shape = target.read("/shape") as? JsonObject ?: return@LaunchedEffect
    fillFrom(
      buildJsonObject {
        put("metric", chosen)
        put("dimension", (shape["dimension"] as? JsonPrimitive)?.contentOrNull ?: "region")
        put("top", (shape["top"] as? JsonPrimitive)?.intOrNull ?: 6)
        (shape["filterRegion"] as? JsonPrimitive)?.contentOrNull?.let { put("filterRegion", it) }
        (shape["filterCategory"] as? JsonPrimitive)?.contentOrNull?.let { put("filterCategory", it) }
      },
      target,
    )
  }

  Column(
    modifier =
      modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Analytics", style = MaterialTheme.typography.titleLarge)
    Text(
      "Ask a question about the sales data. The agent picks the chart; it is never told the " +
        "numbers. Every figure is aggregated on the device, and the measure chips re-aggregate " +
        "with no round trip.",
      style = MaterialTheme.typography.bodySmall,
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      AnalyticsPresets.forEach { preset ->
        AssistChip(onClick = { prompt = preset.prompt }, label = { Text(preset.chip) })
      }
    }

    OutlinedTextField(
      value = prompt,
      onValueChange = { prompt = it },
      label = { Text("Ask about the data") },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = useRecorded, onCheckedChange = { useRecorded = it })
      Text(
        if (useRecorded) "  Recorded answers (stage-safe)" else "  Ask the model",
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = ::ask, enabled = !running) { Text("Ask") }
      if (running) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
    status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    RejectionBanner(client.errors)

    if (surface == null) {
      Text("Press Ask to build a chart.", style = MaterialTheme.typography.bodyMedium)
    } else {
      A2uiSurface(
        state = surface,
        registry = registry,
        onAction = { action ->
          lastAction = action
          if (action.name == "drillDown") drillDown(action, surface, surfaceId)
        },
      )
    }
  }
}
