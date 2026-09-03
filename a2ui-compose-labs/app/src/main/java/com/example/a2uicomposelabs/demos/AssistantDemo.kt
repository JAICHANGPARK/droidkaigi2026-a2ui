package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.model.A2uiAction
import com.example.a2uicomposelabs.a2ui.model.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.catalog.BasicCatalog
import com.example.a2uicomposelabs.a2ui.model.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.runtime.SurfaceState
import com.example.a2uicomposelabs.agent.AgentChunk
import com.example.a2uicomposelabs.agent.a2uiSystemPrompt
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import com.example.a2uicomposelabs.analytics.SalesData
import com.example.a2uicomposelabs.device.DeviceInfo
import com.example.a2uicomposelabs.music.ITunesSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Demo 6 — one chat, one catalog, whatever UI the question needs.
 *
 * The other demos each hand the agent a purpose-built catalog. This one hands it *everything the
 * app owns* — the eighteen basic components plus `Question`, `StarRating`, `PlaylistCard`,
 * `SongRow`, `AlbumCard`, `AlbumHeader` and `TrackRow` — and lets it choose. Ask for feedback and
 * you get a questionnaire; ask about an artist and you get their albums; ask for a playlist and
 * you get rows you can play. No routing code decides that: the component descriptions do, inside
 * the system prompt.
 *
 * The second idea here is the important one for anything that touches real data.
 *
 * **The agent is not allowed to know the music catalog.** Instead of naming albums it might have
 * hallucinated, it writes a request into the surface data model:
 *
 * ```json
 * {"updateDataModel":{"surfaceId":"turn3","path":"/request",
 *   "value":{"kind":"albums","query":"NewJeans"}}}
 * ```
 *
 * and binds its components to `/albums`. The app sees the request, calls the iTunes Search API,
 * fills the array and clears the request. So the structure comes from a model and every title,
 * sleeve and preview URL comes from a real catalog — which is also why nothing the user listens
 * to ever reaches the prompt.
 */

// ---------------------------------------------------------------------------
// One catalog: everything this app can draw.
// ---------------------------------------------------------------------------

private const val ASSISTANT_CATALOG_ID = "app.assistant.catalog/v1"

internal val AssistantCatalogSchema: A2uiCatalog =
  A2uiCatalog(
    ASSISTANT_CATALOG_ID,
    BasicCatalogSchema.components +
      SurveyCatalogSchema.components +
      MusicCatalogSchema.components +
      AlbumCatalogSchema.components +
      AnalyticsCatalogSchema.components +
      DiningComponentDefinitions.associateBy { it.name } +
      LayoutCatalogSchema.associateBy { it.name },
    // Every catalog above shares the basic functions; carrying them once is enough. Dining is
    // the exception: pricing a basket needs arithmetic the basic fourteen do not have.
    functions = BasicCatalogSchema.functions + DiningFunctions,
  )

internal val AssistantCatalog: Map<String, A2uiComponentFactory> =
  BasicCatalog + SurveyCatalog + MusicCatalog + AlbumCatalog + ChartCatalog + LayoutCatalog +
    DiningCatalog

/**
 * The contract that keeps invented data off the screen. It is short on purpose: everything else
 * the model needs is already in the component descriptions.
 */
private val ASSISTANT_RULES = """
Choose components by what the user is asking for:
- feedback, a survey, a questionnaire  -> Question wrapping StarRating / ChoicePicker /
  TextField / CheckBox, then one Button to submit.
- an artist, a band, "what albums..."  -> a List of AlbumCard, or AlbumHeader + TrackRow for
  one specific album.
- a playlist, "songs like ..."         -> PlaylistCard containing a List of SongRow.
- sales, revenue, orders, "how did we do" -> BarChart / LineChart / PieChart, and a List of
  StatTile for the headline figures.
- the phone itself: battery, sensors, memory, storage, how hot it is
                                       -> a List of StatTile, one per fact.
- a table, a booking, "reserve", "예약"  -> the booking form described under RESTAURANT below.
- food, a menu, delivery, "배달"         -> the order screen described under RESTAURANT below.
- anything else                        -> the basic components (Text, Card, Column, ...).

You do NOT know this user's music catalog, and you must never invent a song, album, artist or
image URL. To show music, ask the app for it instead:

{"version":"v1.0","updateDataModel":{"surfaceId":"<surface>","path":"/request","value":{"kind":"albums","query":"NewJeans"}}}

"kind" is one of "albums", "tracks", "chart", "device" or "sensors". Send that message BEFORE
the components, then bind to what the app fills in:
  /albums = [{"id","title","artist","artwork","meta"}, ...]
  /tracks = [{"title","artist","artwork","preview","selected"}, ...]
  /chart  = {"labels": [...], "values": [...]}     for kind "chart"
  /stats  = [{"label","value","delta"}, ...]       for "chart", "device" and "sensors"

For "chart" add "metric" ("sales" or "orders") and "dimension" ("region", "category", "month").
Use {"kind":"menu"} for the restaurant menu.
For "device" and "sensors" there is nothing to add — and note you have NO idea what this
particular phone contains, so never guess a battery level or a sensor name.

The same answer can be drawn several ways and the choice is yours: a horizontal List of
StatTile, a Grid of them two or three across, a vertical List, a BarChart of /levels, or just
Text bound to /headline and /lines. Pick whichever suits the moment, and if the user asks the
same thing again, pick a different one.
Render them with a List whose children are {"componentId": "...", "path": "/albums"} so one
component definition covers every row.

Inside a template the paths are RELATIVE to the current item: bind {"path":"title"}, not
{"path":"/title"} and not {"path":"/albums/0/title"}. The renderer resolves them per row.

Keep every message SHORT — at most two components per updateComponents, and send several
messages instead of one long one. A long message that ends up malformed is rejected whole and
the user sees nothing.

RESTAURANT
$DINING_RULES
""".trimIndent()

// ---------------------------------------------------------------------------

/** Key for the session-owned device sampler. */
private const val MONITOR = "device-monitor"

private val Examples = listOf(
  "How is this phone doing?",
  "Which regions sell the most?",
  "NewJeans albums",
  "Make me a running playlist",
  "Ask me how the workshop went",
  "Book a table for two at 7pm",
  "Order some lunch for delivery",
)

/**
 * Five recordings of the *same* answer, differing only in how it is drawn: a row of tiles, a
 * two-column grid, a plain list, a bar chart, or nothing but sentences. Asking twice gives a
 * different one, which is the whole point — the data is fixed, the presentation is a choice.
 */
private val DevicePresentations = listOf(
  "assistant_device_tiles.jsonl",
  "assistant_device_grid.jsonl",
  "assistant_device_list.jsonl",
  "assistant_device_bars.jsonl",
  "assistant_device_text.jsonl",
)

/**
 * True when the answer is meaningless without data only the app has. A survey needs nothing;
 * a device dashboard or a chart is an empty box until the app fills it.
 */
private fun needsAppData(prompt: String): Boolean {
  val p = prompt.lowercase()
  if (isDiningPrompt(prompt)) return false
  return listOf("survey", "feedback", "questionnaire", "설문", "how was").none(p::contains)
}

/** Keyword routing, used only when there is no API key — the model does this itself when live. */
private fun offlineAssetFor(prompt: String, attempt: Int): String {
  val p = prompt.lowercase()
  return when {
    listOf("battery", "sensor", "cpu", "memory", "device", "phone", "배터리", "센서", "기기")
      .any(p::contains) -> DevicePresentations[attempt % DevicePresentations.size]
    listOf("sales", "revenue", "orders", "chart", "매출", "차트")
      .any(p::contains) -> "assistant_analytics.jsonl"
    listOf("album", "앨범", "discography").any(p::contains) -> "assistant_albums.jsonl"
    listOf("survey", "feedback", "questionnaire", "설문", "how was", "rate")
      .any(p::contains) -> "assistant_survey.jsonl"
    isDiningPrompt(prompt) -> diningAssetFor(prompt)
    else -> "assistant_playlist.jsonl"
  }
}

/** Every Assistant conversation this process has held. Outlives the screen on purpose. */
internal val AssistantSessions = ChatSessionStore(AssistantCatalogSchema)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = rememberAgentSettings()
  val agent = remember(settings.effectiveApiKey, settings.effectiveModel) {
    settings.newAgent()
  }
  val store = AssistantSessions
  // Reading currentId here is what re-runs this body when the user switches conversation.
  val session = run { store.currentId; store.current() }
  val client = session.client
  val transcript = session.transcript
  val wire = session.wire
  val registry = remember { ComponentRegistry(BasicCatalog) + AssistantCatalog }
  val previewPlayer = remember { PreviewPlayer(context) }
  DisposableEffect(previewPlayer) { onDispose { previewPlayer.release() } }
  val listState = rememberLazyListState()
  var showWire by remember { mutableStateOf(false) }

  // Keep the newest turn in view. Driven from the composition rather than the session's
  // job: a scroll animation needs the frame clock, which only a composition scope carries.
  LaunchedEffect(transcript.size) {
      if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.size - 1)
  }

  // The restaurant, answering the one thing this catalog lets a screen ask for. Same wiring as
  // the Dining demo: without it a `join_queue` here would sit until it timed out.
  LaunchedEffect(session) {
      session.launchBackground("house-${session.id}") {
          client.outbound.collect { line ->
              wire += line
              DiningHouse.answer(line)?.let { reply -> wire += reply; client.apply(reply) }
          }
      }
  }

  /**
   * The part worth watching. The agent described a dashboard **once**; from here the app writes
   * `updateDataModel` into it about once a second, for as long as the turn is on screen. No new
   * components, no agent, no network — the surface is a live document.
   */
  fun startLiveMonitor(surface: SurfaceState) {
    session.launchBackground(MONITOR) {
      val sampler = DeviceInfo.Sampler()
      val history = ArrayDeque<Float>()
      while (isActive) {
        val live = sampler.live(context)

        // Deliberately written in several shapes at once. Whichever presentation the agent
        // chose — tiles, a grid, a list, bars, or a sentence — binds to the one it needs, and
        // the *data* never has to know how it is being drawn.
        surface.updateData("/stats", statsJson(live.map { Triple(it.label, it.value, it.detail) }))
        surface.updateData(
          "/lines",
          JsonArray(
            live.map { stat ->
              buildJsonObject {
                put(
                  "summary",
                  buildString {
                    append(stat.label).append(": ").append(stat.value)
                    if (stat.detail.isNotEmpty()) append(" (").append(stat.detail).append(")")
                  },
                )
              }
            }
          ),
        )
        surface.updateData("/headline", JsonPrimitive(live.joinToString(" · ") { "${it.label} ${it.value}" }))
        // Only the percentages, for a chart that compares them side by side.
        val numeric = live.mapNotNull { stat ->
          stat.value.removeSuffix("%").toFloatOrNull()?.let { stat.label to it }
        }
        surface.updateData(
          "/levels",
          buildJsonObject {
            put("labels", JsonArray(numeric.map { JsonPrimitive(it.first) }))
            put("values", JsonArray(numeric.map { JsonPrimitive(it.second) }))
          },
        )

        val cpu = live.firstOrNull { it.label == "App CPU" }
          ?.value?.removeSuffix("%")?.toFloatOrNull() ?: 0f
        history.addLast(cpu)
        while (history.size > 30) history.removeFirst()
        surface.updateData(
          "/chart",
          buildJsonObject {
            put("labels", JsonArray(history.indices.map { JsonPrimitive(if (it % 6 == 0) "${it}s" else "") }))
            put("values", JsonArray(history.map(::JsonPrimitive)))
          },
        )
        delay(1000L)
      }
    }
  }

  /**
   * Fulfils `/request` if the agent left one. Returns true when it filled something, so the
   * caller can keep checking after later messages arrive.
   */
  suspend fun fulfilRequest(surface: SurfaceState): Boolean {
    // No request, or one this screen does not answer: the app can still own its own data.
    // A model that writes four plausible dishes instead of asking is not hypothetical, and a
    // made-up price on an order screen is the one mistake worth stepping in for.
    val request = surface.read("/request") as? JsonObject ?: return enforceHouseMenu(surface)
    val kind = (request["kind"] as? JsonPrimitive)?.contentOrNull
      ?: return enforceHouseMenu(surface)
    val query = (request["query"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    // Consume it first: a slow lookup must not be started twice by the next message.
    surface.updateData("/request", null)
    when (kind) {
      "albums" ->
        surface.updateData(
          "/albums",
          JsonArray(
            ITunesSearch.findAlbums(query, limit = 8).map { album ->
              buildJsonObject {
                put("id", album.id)
                put("title", album.title)
                put("artist", album.artist)
                put("artwork", album.artworkUrl)
                put("meta", listOfNotNull(
                  album.year.takeIf(String::isNotEmpty),
                  "${album.trackCount} tracks",
                ).joinToString(" · "))
              }
            }
          ),
        )
      "chart" -> {
        val metric = (request["metric"] as? JsonPrimitive)?.contentOrNull ?: "sales"
        val dimension = (request["dimension"] as? JsonPrimitive)?.contentOrNull ?: "region"
        val top = (request["top"] as? JsonPrimitive)?.intOrNull ?: 6
        val series = SalesData.series(metric, dimension, top)
        surface.updateData(
          "/chart",
          buildJsonObject {
            put("labels", JsonArray(series.labels.map(::JsonPrimitive)))
            put("values", JsonArray(series.values.map(::JsonPrimitive)))
          },
        )
        surface.updateData("/stats", statsJson(SalesData.summary(metric).map { (l, v) -> Triple(l, v, "") }))
      }

      "menu" -> fulfilMenuRequest(surface, kind)

      "sensors" ->
        surface.updateData(
          "/stats",
          statsJson(DeviceInfo.sensors(context).map { Triple(it.label, it.value, it.detail) }),
        )

      // Not a one-shot answer: the app keeps writing into the surface the agent built.
      "device" -> startLiveMonitor(surface)

      "tracks" ->
        surface.updateData(
          "/tracks",
          JsonArray(
            ITunesSearch.findTracks(query, limit = 6).map { track ->
              buildJsonObject {
                put("title", track.title)
                put("artist", track.artist)
                put("artwork", track.artworkUrl)
                put("preview", track.previewUrl)
                put("selected", true)
              }
            }
          ),
        )
      else -> return false
    }
    return true
  }

  /**
   * The model is told to ask for music data, and usually does. When it forgets, an empty array
   * would render an empty card — so the app fills the obvious thing from the user's own words.
   * A demo should degrade into something, not nothing.
   */
  suspend fun fillFromPromptIfEmpty(surface: SurfaceState, prompt: String) {
    val albums = surface.read("/albums") as? JsonArray
    val tracks = surface.read("/tracks") as? JsonArray
    val stats = surface.read("/stats") as? JsonArray

    // A dashboard the model built but never asked to fill. Start feeding it anyway — an empty
    // card is the one outcome worse than a slightly presumptuous one.
    if (stats != null && stats.isEmpty()) {
      startLiveMonitor(surface)
      return
    }

    val kind = when {
      albums != null && albums.isEmpty() -> "albums"
      tracks != null && tracks.isEmpty() -> "tracks"
      else -> return
    }
    surface.updateData(
      "/request",
      buildJsonObject {
        put("kind", kind)
        put("query", prompt)
      },
    )
    fulfilRequest(surface)
  }

  /**
   * Plays a recorded answer into [surfaceId]. The recording is a template — the app takes the
   * agent's place and stamps this turn's surface id and the user's own words into it.
   */
  suspend fun playRecorded(prompt: String, surfaceId: String) {
    // The turn counter rises with every question, so asking twice draws the answer differently.
    val asset = offlineAssetFor(prompt, session.turnCount)
    val lines = context.assets.open(asset).bufferedReader().useLines { it.toList() }
    for (line in lines.filter(String::isNotBlank)) {
      val stamped = line.replace("__turn__", surfaceId).replace("__query__", prompt)
      client.apply(stamped)
      wire += stamped
      client.surfaces[surfaceId]?.let { fulfilRequest(it) }
      kotlinx.coroutines.delay(350L)
    }
  }


  /** Replays a recorded stream into [surfaceId], substituting this turn's tokens. */
  suspend fun playAsset(asset: String, surfaceId: String, values: Map<String, String>) {
    val lines = context.assets.open(asset).bufferedReader().useLines { it.toList() }
    for (line in lines.filter(String::isNotBlank)) {
      var stamped = line.replace("__turn__", surfaceId)
      values.forEach { (token, value) -> stamped = stamped.replace(token, value) }
      client.apply(stamped)
      wire += stamped
      delay(220L)
    }
  }

  /**
   * Actions the app answers itself. The restaurant flow writes back into its own surface — a
   * booking code, a priced receipt — with no agent in the loop. Anything else is just reported.
   */
  fun handleAction(action: A2uiAction) {
    transcript.add(
      ChatTurn.Assistant(actionSummary(action), copyText = prettyJson(action.toJson()))
    )
    val surface = client.surfaces[action.surfaceId] ?: return
    session.scope.launch {
      // Only what the app published actually runs. An action it never declared is not a crash
      // and not a guess — it is reported, so a button that did nothing says why.
      val handled = runDiningAction(
        action = action,
        session = session,
        play = { asset, values -> playAsset(asset, action.surfaceId, values) },
        say = { transcript.add(ChatTurn.Assistant(it)) },
      )
      if (!handled) {
        transcript.add(
          ChatTurn.Assistant(
            "This app has no handler for \"${action.name}\", so nothing happened. " +
              "The renderer sent the action; running it was never its job."
          )
        )
      }
    }
  }

  fun send() {
    val prompt = session.input.trim().ifBlank { Examples.first() }
    session.turnJob?.cancel()
    session.stopBackground(MONITOR)
    // Each turn reports its own failures. Without this the last error of the session
    // stays pinned under the input box, over turns that went fine.
    client.errors.clear()
    session.input = ""
    session.running = true
    session.rename(prompt)
    val surfaceId = session.nextSurfaceId()
    transcript.add(ChatTurn.User(prompt))

    session.turnJob = session.scope.launch {
      try {
        if (agent.isConfigured) {
          val errorsBefore = client.errors.size
          val systemPrompt = a2uiSystemPrompt(AssistantCatalogSchema, surfaceId, ASSISTANT_RULES)
          var announced = false
          var fulfilled = false
          val prose = StringBuilder()
          // UI arrives as a tool call, prose as text. Nothing has to be pulled apart.
          agent.streamUi(
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            surfaceId = surfaceId,
            // What the renderer refused, in the model's own next turn.
            applyUi = { json ->
              val before = client.errors.size
              client.apply(json)
              client.errors.drop(before).firstOrNull()?.toString()
            },
          ).collect { chunk ->
            when (chunk) {
              is AgentChunk.Prose -> prose.append(chunk.text)
              is AgentChunk.Ui -> {
                wire += chunk.json
                if (!announced && client.surfaces[surfaceId] != null) {
                  announced = true
                  transcript.add(ChatTurn.Ui(surfaceId))
                }
                client.surfaces[surfaceId]?.let { if (fulfilRequest(it)) fulfilled = true }
              }
            }
          }
          prose.toString().trim().takeIf(String::isNotBlank)
            ?.let { transcript.add(ChatTurn.Assistant(it)) }

          // The renderer refused something, or the model never built anything past the shell.
          // Rejecting bad output is the right behaviour; showing the user an empty card is not,
          // so the app answers with a recording instead.
          // Three ways a live turn leaves the user with nothing: the renderer refused a message,
          // the model never built past the shell, or — the common one — it drew a dashboard and
          // forgot to ask the app to fill it. Rejecting bad output is right; showing an empty
          // card is not, so the app answers with a recording instead.
          val built = client.surfaces[surfaceId]?.components?.size ?: 0
          val starved = !fulfilled && needsAppData(prompt)
          if (client.errors.size > errorsBefore || built <= 1 || starved) {
            // Say which of the several possible things actually happened, and quote the
            // model where there is something to quote.
            transcript.add(
              ChatTurn.Assistant(
                when {
                  starved ->
                    "The model built a dashboard but never asked the app to fill it, so this " +
                      "answer is a recorded layout."
                  agent.lastFinishReason == "MAX_TOKENS" ->
                    "The model ran out of output budget mid-message, so this answer is a " +
                      "recorded one."
                  agent.lastFailures.isNotEmpty() ->
                    "The model could not finish the screen, so this answer is a recorded one. " +
                      "What it got wrong:\n" +
                      agent.lastFailures.joinToString("\n") { "· $it" }
                  client.errors.size > errorsBefore ->
                    "The model's UI was rejected by the catalog, so this answer is a recorded one."
                  else ->
                    "The model stopped before the screen was finished, so this answer is a " +
                      "recorded one."
                }
              )
            )
            if (!announced) {
              announced = true
              transcript.add(ChatTurn.Ui(surfaceId))
            }
            playRecorded(prompt, surfaceId)
          }
        } else {
          transcript.add(ChatTurn.Assistant("No API key, so this turn replays a recorded answer."))
          transcript.add(ChatTurn.Ui(surfaceId))
          playRecorded(prompt, surfaceId)
        }
        // One last look: the request may have arrived in the final message.
        client.surfaces[surfaceId]?.let { surface ->
          if (!fulfilRequest(surface)) fillFromPromptIfEmpty(surface, prompt)
        }
      } catch (e: Exception) {
        transcript.add(ChatTurn.Assistant("Failed: ${e.message ?: e::class.simpleName}"))
      } finally {
        session.running = false
      }
    }
  }

  Column(modifier = modifier.fillMaxSize().imePadding().padding(16.dp)) {
    Text("Assistant", style = MaterialTheme.typography.titleLarge)
    Text(
      "One catalog with every component this app owns. Ask for a survey, an artist's albums, " +
        "or a playlist — the agent picks the components, and the app supplies the music data " +
        "so nothing on screen was invented.",
      style = MaterialTheme.typography.bodySmall,
    )

    ChatSessionBar(store = store, current = session, modifier = Modifier.padding(top = 4.dp))

    if (transcript.isEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
      ) {
        Examples.forEach { example ->
          AssistChip(onClick = { session.input = example }, label = { Text(example) })
        }
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(transcript) { turn ->
        when (turn) {
          is ChatTurn.User -> ChatBubble(turn.text, fromUser = true)
          is ChatTurn.Assistant ->
                        ChatBubble(turn.text, fromUser = false, copyText = turn.copyText)
          is ChatTurn.Ui -> {
            val surface = client.surfaces[turn.surfaceId]
            if (surface == null) {
              ChatBubble("…", fromUser = false)
            } else {
              CompositionLocalProvider(LocalPreviewPlayer provides previewPlayer) {
                A2uiSurface(
                  state = surface,
                  registry = registry,
                  onAction = ::handleAction,
                  // Without this the scope gets EmptyEvaluator and every {"call": ...} —
                  // totals, price labels, formatted dates — resolves to nothing.
                  catalog = AssistantCatalogSchema,
                )
              }
            }
          }
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = showWire, onCheckedChange = { showWire = it })
      Text("  Show the wire (${wire.size} lines)", style = MaterialTheme.typography.labelSmall)
    }
    if (showWire && wire.isNotEmpty()) {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
          wire.takeLast(6).forEach { line ->
            Text(
              line,
              style = MaterialTheme.typography.labelSmall,
              maxLines = 4,
              modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            )
          }
        }
      }
    }
    RejectionBanner(client.errors)

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = session.input,
        onValueChange = { session.input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text(Examples.first()) },
      )
      if (session.running) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      } else {
        Button(onClick = ::send) { Text("Send") }
      }
    }
  }
}


/** Stats always land in the data model in the same shape, whatever produced them. */
private fun statsJson(rows: List<Triple<String, String, String>>): JsonArray =
  JsonArray(
    rows.map { (label, value, detail) ->
      buildJsonObject {
        put("label", label)
        put("value", value)
        put("delta", detail)
      }
    }
  )
