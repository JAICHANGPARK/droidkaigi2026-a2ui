package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.A2uiAction
import com.example.a2uicomposelabs.a2ui.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.A2uiComponentDefinition
import com.example.a2uicomposelabs.a2ui.A2uiComponentFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.a2uicomposelabs.a2ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.A2uiUrlPolicy
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.childList
import com.example.a2uicomposelabs.a2ui.componentSchema
import com.example.a2uicomposelabs.a2ui.dynamicString
import com.example.a2uicomposelabs.a2ui.replayAsset
import com.example.a2uicomposelabs.a2ui.twoWay
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import com.example.a2uicomposelabs.music.ITunesSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The talk's case study, half one: the schema. This is what an agent would be told it may
 * emit, and — because [A2uiClient] validates against the same object — exactly what the
 * renderer will accept. Adding a component to the app means adding it here once.
 */
internal val MusicCatalogSchema: A2uiCatalog =
  BasicCatalogSchema.withId("app.music.catalog/v1") + listOf(
    A2uiComponentDefinition(
      name = "PlaylistCard",
      description = "The outer card of a playlist, with its title and its songs.",
      propertySchema = componentSchema(
        properties = mapOf(
          "title" to dynamicString("Playlist name."),
          "children" to childList("IDs of the rows inside the card."),
        ),
        required = setOf("title"),
      ),
    ),
    A2uiComponentDefinition(
      name = "SongRow",
      description = "One song: title, artist, and a checkbox for whether it is included.",
      propertySchema = componentSchema(
        properties = mapOf(
          "title" to dynamicString("Song title."),
          "artist" to dynamicString("Performing artist."),
          "artworkUrl" to dynamicString("https URL of the album art. Bind it; never invent one."),
          "previewUrl" to dynamicString("https URL of a short audio preview. Bind it; never invent one."),
          "selected" to twoWay("Bound path holding whether the song is included."),
        ),
        required = setOf("title", "selected"),
      ),
    ),
  )

/**
 * One player for the whole screen, so starting a second preview stops the first.
 *
 * It is deliberately *not* part of the A2UI message: the agent asks for a `SongRow` and binds a
 * URL, and how audio is actually played is a decision the app keeps to itself.
 */
internal class PreviewPlayer(context: Context) {

  private val player = ExoPlayer.Builder(context).build()

  /** The URL currently playing, so each row can draw the right icon. */
  var playingUrl by mutableStateOf<String?>(null)
    private set

  init {
    player.addListener(
      object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
          // Only clear on a real end; pausing for buffering must not flip every icon.
          if (state == Player.STATE_ENDED) playingUrl = null
        }
      }
    )
  }

  fun toggle(url: String) {
    if (playingUrl == url) {
      player.pause()
      playingUrl = null
      return
    }
    player.setMediaItem(MediaItem.fromUri(url))
    player.prepare()
    player.play()
    playingUrl = url
  }

  fun release() {
    player.release()
  }
}

/** Null in tests and previews; SongRow simply omits the play button then. */
internal val LocalPreviewPlayer = staticCompositionLocalOf<PreviewPlayer?> { null }

/**
 * Half two: the code. The agent can request a SongRow, but only the SongRow we wrote and
 * approved — and only with the properties the schema above declares.
 */
internal val MusicCatalog: Map<String, A2uiComponentFactory> = mapOf(

  "PlaylistCard" to { node, scope, renderChild ->
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          scope.readString(node.props["title"]),
          style = MaterialTheme.typography.titleLarge,
        )
        scope.children(node.props).forEach { renderChild(it) }
      }
    }
  },

  "SongRow" to { node, scope, _ ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = scope.readBoolean(node.props["selected"]),
        onCheckedChange = { scope.write(node.props["selected"], JsonPrimitive(it)) },
      )
      // Same https-only rule the basic catalog's Image uses: an agent-supplied URL is not
      // trusted just because it arrived in a property.
      val artwork = scope.readString(node.props["artworkUrl"])
      if (A2uiUrlPolicy.allows(artwork)) {
        AsyncImage(
          model = artwork,
          contentDescription = null,
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
      }
      Column(Modifier.weight(1f)) {
        Text(scope.readString(node.props["title"]), style = MaterialTheme.typography.bodyLarge)
        Text(scope.readString(node.props["artist"]), style = MaterialTheme.typography.bodySmall)
      }
      val preview = scope.readString(node.props["previewUrl"])
      val player = LocalPreviewPlayer.current
      if (player != null && A2uiUrlPolicy.allows(preview)) {
        val playing = player.playingUrl == preview
        IconButton(onClick = { player.toggle(preview) }) {
          Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause preview" else "Play preview",
          )
        }
      }
    }
  },
)

/** A plain-text prompt — no A2UI here. The model only names songs; it never describes UI. */
private const val TITLES_PROMPT = """
You suggest songs. Given a vibe, reply with exactly four lines and nothing else.
Each line is "Title - Artist". No numbering, no commentary, no markdown fences.
Only real, released songs.
"""

@Composable
fun PlaylistDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = rememberAgentSettings()
  val agent = remember(settings.effectiveApiKey, settings.effectiveModel) {
    settings.newAgent()
  }
  val client = remember { A2uiClient(MusicCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) + MusicCatalog }
  val coroutineScope = rememberCoroutineScope()
  val previewPlayer = remember { PreviewPlayer(context) }
  DisposableEffect(previewPlayer) { onDispose { previewPlayer.release() } }
  var lastAction by remember { mutableStateOf<A2uiAction?>(null) }

  var vibe by remember { mutableStateOf("running 150 bpm upbeat") }
  var askGemini by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<String?>(null) }
  var job by remember { mutableStateOf<Job?>(null) }

  LaunchedEffect(Unit) { client.replayAsset(context, "playlist_demo.jsonl") }

  /**
   * The hybrid: the agent already sent the *structure* (a PlaylistCard and four SongRows bound
   * to `/songs/N/...`). This fills the *content* underneath those bindings from a real catalog,
   * without sending a single new A2UI message. The model never sees the user's library, and a
   * song it invented simply fails to resolve and is dropped.
   */
  fun loadRealTracks() {
    val surface = client.surfaces["playlist"] ?: return
    job?.cancel()
    loading = true
    status = null
    job = coroutineScope.launch {
      try {
        val tracks =
          if (askGemini && agent.isConfigured) {
            val reply = StringBuilder()
            agent.stream(TITLES_PROMPT, vibe).collect { reply.append(it) }
            val named = reply.lines().map(String::trim).filter(String::isNotEmpty).take(6)
            val resolved = ITunesSearch.resolveAll(named, limit = 4)
            status = "Gemini named ${named.size}; the catalog had ${resolved.size}."
            resolved
          } else {
            val found = ITunesSearch.findTracks(vibe, limit = 4)
            status = "${found.size} tracks from the iTunes Search API — no key, no account."
            found
          }
        if (tracks.isEmpty()) {
          status = "Nothing found for \"$vibe\"."
        } else {
          surface.updateData(
            "/songs",
            JsonArray(
              tracks.map { track ->
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
        }
      } catch (e: Exception) {
        status = "Failed: ${e.message ?: e::class.simpleName}"
      } finally {
        loading = false
      }
    }
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Playlist builder", style = MaterialTheme.typography.titleLarge)
    Text(
      "\"Make me a running playlist.\" The agent fills the surface with the app's " +
        "own components (SongRow, PlaylistCard). Edit locally, then Save sends one action.\n\n" +
        "The rows start with recorded data. Load real tracks to replace what is under " +
        "/songs from Apple's catalog — the agent sends nothing new, and never sees the result.",
      style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
      value = vibe,
      onValueChange = { vibe = it },
      label = { Text("Vibe or search terms") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(
        checked = askGemini,
        onCheckedChange = { askGemini = it },
        enabled = agent.isConfigured,
      )
      Text(
        if (!agent.isConfigured) "  Search the catalog directly (add a key in Settings to let Gemini pick)"
        else if (askGemini) "  Gemini names the songs, the catalog verifies them"
        else "  Search the catalog directly",
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = ::loadRealTracks, enabled = !loading) { Text("Load real tracks") }
      if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
    status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }

    val surface = client.surfaces["playlist"]
    if (surface == null) {
      Text("waiting for agent…", style = MaterialTheme.typography.bodyMedium)
    } else {
      CompositionLocalProvider(LocalPreviewPlayer provides previewPlayer) {
        A2uiSurface(
          state = surface,
          registry = registry,
          onAction = { lastAction = it },
          catalog = MusicCatalogSchema,
        )
      }
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
