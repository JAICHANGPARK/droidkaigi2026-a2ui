package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.a2uicomposelabs.a2ui.model.A2uiAction
import com.example.a2uicomposelabs.a2ui.model.A2uiAnySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import com.example.a2uicomposelabs.a2ui.model.A2uiComponentDefinition
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.model.A2uiObjectSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiStringSchema
import com.example.a2uicomposelabs.a2ui.ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.catalog.A2uiUrlPolicy
import com.example.a2uicomposelabs.a2ui.catalog.BasicCatalog
import com.example.a2uicomposelabs.a2ui.model.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.model.componentSchema
import com.example.a2uicomposelabs.a2ui.model.dynamicNumber
import com.example.a2uicomposelabs.a2ui.model.dynamicString
import com.example.a2uicomposelabs.a2ui.testing.replayAsset
import com.example.a2uicomposelabs.music.ITunesSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Demo 6 — an album browser over a real catalog.
 *
 * This is the pattern from the playlist demo taken one step further, and it is the shape most
 * real features have: **two surfaces, a tap between them, and not one byte of catalog data
 * passing through a model.**
 *
 * - The *structure* is fixed and comes from two recorded A2UI streams. Both use a template
 *   `List` — one `AlbumCard` definition renders every album, one `TrackRow` renders every
 *   track, each bound to its own slice of the data by relative path.
 * - The *content* is written straight into the surface data model by the app, from Apple's
 *   keyless iTunes Search API.
 * - The *navigation* is an `action`. Tapping a card sends `openAlbum` with the album id
 *   resolved out of that row's data — and the app, not an agent, decides what that means.
 *
 * Search "NewJeans" and you get their EPs; tap one and you get its sleeve and its four tracks,
 * each with a 30-second preview. The A2UI messages never mention NewJeans.
 */

// ---------------------------------------------------------------------------
// The catalog: the basic eighteen, plus the three this screen is made of.
// ---------------------------------------------------------------------------

private const val ALBUM_CATALOG_ID = "app.album.catalog/v1"

/** Same shape as the Basic Catalog's action property; declared here so the demo is self-contained. */
private val albumActionSchema: A2uiSchema = A2uiObjectSchema(
  properties = mapOf(
    "name" to A2uiStringSchema("Action name the app will receive."),
    "context" to A2uiObjectSchema(
      additionalPropertiesSchema = A2uiAnySchema(),
      description = "Values to send with the action; each may be a literal or {\"path\"}.",
    ),
  ),
  required = setOf("name"),
  isAdditionalPropertiesAllowed = false,
)

internal val AlbumCatalogSchema: A2uiCatalog =
  BasicCatalogSchema.withId(ALBUM_CATALOG_ID) + listOf(
    A2uiComponentDefinition(
      name = "AlbumCard",
      description =
        "One album in a list: its sleeve, title, artist and a short line of metadata. Tapping " +
          "it dispatches the action, so use it inside a List bound to an array of albums.",
      propertySchema = componentSchema(
        properties = mapOf(
          "artworkUrl" to dynamicString("https URL of the album sleeve. Bind it; never invent one."),
          "title" to dynamicString("Album title."),
          "artist" to dynamicString("Artist name."),
          "meta" to dynamicString("A short line such as \"2022 · 4 tracks\"."),
          "action" to albumActionSchema,
        ),
        required = setOf("title"),
      ),
    ),
    A2uiComponentDefinition(
      name = "AlbumHeader",
      description =
        "The top of an album page: a large sleeve with the title, artist and metadata beside it.",
      propertySchema = componentSchema(
        properties = mapOf(
          "artworkUrl" to dynamicString("https URL of the album sleeve. Bind it; never invent one."),
          "title" to dynamicString("Album title."),
          "artist" to dynamicString("Artist name."),
          "meta" to dynamicString("A short line such as \"2022 · 4 tracks\"."),
        ),
        required = setOf("title"),
      ),
    ),
    A2uiComponentDefinition(
      name = "TrackRow",
      description =
        "One track inside an album: its position, title, running time, and a play button when a " +
          "preview URL is bound. Use it inside a List bound to the album's track array.",
      propertySchema = componentSchema(
        properties = mapOf(
          "number" to dynamicNumber("Position of the track on the album."),
          "title" to dynamicString("Track title."),
          "duration" to dynamicString("Running time, already formatted as m:ss."),
          "previewUrl" to dynamicString("https URL of a short preview. Bind it; never invent one."),
        ),
        required = setOf("title"),
      ),
    ),
  )

internal val AlbumCatalog: Map<String, A2uiComponentFactory> = mapOf(

  "AlbumCard" to { node, scope, _ ->
    Card(
      modifier = Modifier.fillMaxWidth().clickable { scope.dispatchAction(node) },
    ) {
      Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtwork(scope.readString(node.props["artworkUrl"]), 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            scope.readString(node.props["title"]),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(scope.readString(node.props["artist"]), style = MaterialTheme.typography.bodySmall)
          Text(
            scope.readString(node.props["meta"]),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  },

  "AlbumHeader" to { node, scope, _ ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      AlbumArtwork(scope.readString(node.props["artworkUrl"]), 120.dp)
      Spacer(Modifier.width(16.dp))
      Column {
        Text(scope.readString(node.props["title"]), style = MaterialTheme.typography.titleLarge)
        Text(scope.readString(node.props["artist"]), style = MaterialTheme.typography.bodyMedium)
        Text(
          scope.readString(node.props["meta"]),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    }
  },

  "TrackRow" to { node, scope, _ ->
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        scope.readFloat(node.props["number"], 0f).toInt().toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.width(28.dp),
      )
      Text(
        scope.readString(node.props["title"]),
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        scope.readString(node.props["duration"]),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
      )
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

/** Shared by the card and the header: an https-gated sleeve, or a neutral placeholder. */
@Composable
private fun AlbumArtwork(url: String, size: Dp) {
  val shape = RoundedCornerShape(8.dp)
  if (A2uiUrlPolicy.allows(url)) {
    AsyncImage(
      model = url,
      contentDescription = null,
      modifier = Modifier.size(size).clip(shape),
    )
  } else {
    Card(modifier = Modifier.size(size), shape = shape) {}
  }
}

// ---------------------------------------------------------------------------

@Composable
fun AlbumDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val client = remember { A2uiClient(AlbumCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) + AlbumCatalog }
  val previewPlayer = remember { PreviewPlayer(context) }
  DisposableEffect(previewPlayer) { onDispose { previewPlayer.release() } }
  val coroutineScope = rememberCoroutineScope()

  var query by remember { mutableStateOf("NewJeans") }
  var loading by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<String?>(null) }
  var showingAlbum by remember { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }

  // Both structures arrive once, up front. Nothing below sends another A2UI message.
  LaunchedEffect(Unit) {
    client.replayAsset(context, "album_search.jsonl", lineDelayMs = 0L)
    client.replayAsset(context, "album_detail.jsonl", lineDelayMs = 0L)
  }

  fun searchAlbums() {
    val surface = client.surfaces["albums"] ?: return
    job?.cancel()
    loading = true
    status = null
    showingAlbum = false
    job = coroutineScope.launch {
      val albums = ITunesSearch.findAlbums(query, limit = 12)
      surface.updateData("/heading", JsonPrimitive("Albums matching \"$query\""))
      surface.updateData(
        "/albums",
        JsonArray(
          albums.map { album ->
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
      status = "${albums.size} albums from the iTunes Search API — no key, no account."
      loading = false
    }
  }

  /**
   * The action handler. `openAlbum` is not sent anywhere: the app reads the id the renderer
   * already resolved out of that row and looks the album up itself.
   */
  fun openAlbum(action: A2uiAction) {
    val id = (action.context["albumId"] as? JsonPrimitive)?.longOrNull ?: return
    val surface = client.surfaces["album"] ?: return
    job?.cancel()
    loading = true
    status = null
    job = coroutineScope.launch {
      val detail = ITunesSearch.lookupAlbum(id)
      if (detail == null) {
        status = "Could not load that album."
      } else {
        surface.updateData(
          "/album",
          buildJsonObject {
            put("artwork", detail.album.artworkLargeUrl)
            put("title", detail.album.title)
            put("artist", detail.album.artist)
            put("meta", listOfNotNull(
              detail.album.year.takeIf(String::isNotEmpty),
              "${detail.tracks.size} tracks",
            ).joinToString(" · "))
            put(
              "tracks",
              JsonArray(
                detail.tracks.map { track ->
                  buildJsonObject {
                    put("number", track.number)
                    put("title", track.title)
                    put("duration", track.duration)
                    put("preview", track.previewUrl)
                  }
                }
              ),
            )
          },
        )
        showingAlbum = true
        status = "${detail.tracks.size} tracks · previews are 30-second clips from the store."
      }
      loading = false
    }
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Album browser", style = MaterialTheme.typography.titleLarge)
    Text(
      "Two recorded surfaces — a list and a detail page — filled from a real catalog. One " +
        "AlbumCard definition renders every album and one TrackRow renders every track, " +
        "because both lists are templates bound to an array. Tapping a card is an action the " +
        "app handles itself.",
      style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      label = { Text("Artist or album") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = ::searchAlbums, enabled = !loading) { Text("Find albums") }
      if (showingAlbum) {
        OutlinedButton(onClick = { showingAlbum = false }) { Text("Back to albums") }
      }
      if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
    status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }

    val surface = client.surfaces[if (showingAlbum) "album" else "albums"]
    if (surface == null) {
      Text("waiting for the surface…", style = MaterialTheme.typography.bodyMedium)
    } else {
      CompositionLocalProvider(LocalPreviewPlayer provides previewPlayer) {
        A2uiSurface(
          state = surface,
          registry = registry,
          onAction = { action -> if (action.name == "openAlbum") openAlbum(action) },
        )
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}
