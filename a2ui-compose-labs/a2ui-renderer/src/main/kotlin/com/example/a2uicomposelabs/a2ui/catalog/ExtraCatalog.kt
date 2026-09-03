@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.a2uicomposelabs.a2ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Defense layer 5 in code: media URLs must be https, optionally host-allowlisted.
 * (See slide 32 — URL / media policy.)
 */
object A2uiUrlPolicy {
    /** null = any https host (demo default). Set to restrict: `setOf("cdn.example.com")`. */
    var allowedHosts: Set<String>? = null

    fun allows(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val hosts = allowedHosts ?: return true
        return runCatching { URI(url).host in hosts }.getOrDefault(false)
    }
}

/**
 * The remaining Basic Catalog components (spec v1.0). Together with the ten in
 * [BasicCatalog] this covers all 18 components of
 * `specification/v1_0/catalogs/basic/catalog.json`.
 *
 * Not implemented (engine-level features, out of demo scope): `weight` (needs
 * RowScope/ColumnScope plumbing) and `checks`/CheckRule validation (needs the
 * catalog functions engine).
 */
val ExtraCatalog: Map<String, A2uiComponentFactory> = mapOf(

    // url · description (a11y) · fit (contain|cover|fill|none|scaleDown) · variant
    "Image" to { node, scope, _ ->
        val url = scope.readString(node.props["url"])
        if (A2uiUrlPolicy.allows(url)) {
            val variant = scope.readString(node.props["variant"]).ifEmpty { "mediumFeature" }
            val sizing = when (variant) {
                "icon" -> Modifier.size(24.dp)
                "avatar" -> Modifier.size(40.dp).clip(CircleShape)
                "smallFeature" -> Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                "largeFeature" -> Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp))
                "header" -> Modifier.fillMaxWidth().height(180.dp)
                else -> Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)) // mediumFeature
            }
            AsyncImage(
                model = url,
                contentDescription = scope.readString(node.props["description"]).ifEmpty { null },
                contentScale = when (scope.readString(node.props["fit"])) {
                    "contain" -> ContentScale.Fit
                    "cover" -> ContentScale.Crop
                    "none" -> ContentScale.None
                    "scaleDown" -> ContentScale.Inside
                    else -> ContentScale.FillBounds // spec default: "fill"
                },
                modifier = sizing,
            )
        }
    },

    // name: one of 59 enum names, or {"svgPath": "..."} for a custom 24x24 vector
    "Icon" to { node, scope, _ ->
        val nameProp = node.props["name"]
        val vector: ImageVector? = when {
            nameProp is JsonPrimitive -> materialIconFor(nameProp.contentOrNull.orEmpty())
            nameProp is JsonObject && nameProp.containsKey("svgPath") -> {
                val pathData = scope.readString(nameProp["svgPath"])
                remember(pathData) { svgPathIcon(pathData) }
            }
            nameProp is JsonObject -> materialIconFor(scope.readString(nameProp)) // bound name
            else -> null
        }
        vector?.let { Icon(it, contentDescription = null) }
    },

    // tabs: [{title, child}] — child is a component ID
    "Tabs" to { node, scope, renderChild ->
        val tabs = (node.props["tabs"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        if (tabs.isNotEmpty()) {
            var selected by remember { mutableIntStateOf(0) }
            val index = selected.coerceIn(0, tabs.size - 1)
            Column {
                PrimaryTabRow(selectedTabIndex = index) {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = index == i,
                            onClick = { selected = i },
                            text = { Text(scope.readString(tab["title"])) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                (tabs[index]["child"] as? JsonPrimitive)?.contentOrNull?.let { renderChild(it) }
            }
        }
    },

    // trigger: component ID that opens the modal · content: component ID shown inside
    "Modal" to { node, scope, renderChild ->
        var open by remember { mutableStateOf(false) }
        val trigger = (node.props["trigger"] as? JsonPrimitive)?.contentOrNull
        val content = (node.props["content"] as? JsonPrimitive)?.contentOrNull
        Box {
            trigger?.let { renderChild(it) }
            // Transparent overlay: interacting with the trigger opens the modal.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { open = true }
            )
        }
        if (open && content != null) {
            Dialog(onDismissRequest = { open = false }) {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    Box(Modifier.padding(24.dp)) { renderChild(content) }
                }
            }
        }
    },

    // label · variant (mutuallyExclusive|multipleSelection) · options: [{label, value}]
    // value: bound string array of selected values · displayStyle (checkbox|chips) · filterable
    "ChoicePicker" to { node, scope, _ ->
        val multiple = scope.readString(node.props["variant"]) == "multipleSelection"
        val chips = scope.readString(node.props["displayStyle"]) == "chips"
        val filterable = (node.props["filterable"] as? JsonPrimitive)?.booleanOrNull ?: false
        val options = (node.props["options"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        val selected = scope.readStringList(node.props["value"])

        fun select(value: String) {
            // Re-read at event time: the composition-time list may be stale.
            val current = scope.readStringList(node.props["value"])
            val next = when {
                !multiple -> listOf(value)
                value in current -> current - value
                else -> current + value
            }
            scope.write(node.props["value"], JsonArray(next.map(::JsonPrimitive)))
        }

        var query by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val label = scope.readString(node.props["label"])
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium)
            if (filterable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Filter") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val visible = options.filter {
                query.isEmpty() || scope.readString(it["label"]).contains(query, ignoreCase = true)
            }
            if (chips) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { opt ->
                        val value = (opt["value"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        FilterChip(
                            selected = value in selected,
                            onClick = { select(value) },
                            label = { Text(scope.readString(opt["label"])) },
                        )
                    }
                }
            } else {
                visible.forEach { opt ->
                    val value = (opt["value"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { select(value) },
                    ) {
                        if (multiple) {
                            Checkbox(checked = value in selected, onCheckedChange = { select(value) })
                        } else {
                            RadioButton(selected = value in selected, onClick = { select(value) })
                        }
                        Text(scope.readString(opt["label"]))
                    }
                }
            }
        }
    },

    // label · value: ISO 8601 (two-way) · enableDate · enableTime · min · max
    "DateTimeInput" to { node, scope, _ ->
        val enableTime = (node.props["enableTime"] as? JsonPrimitive)?.booleanOrNull ?: false
        // Spec defaults both flags to false; treat that as date-only so the control does something.
        val enableDate =
            ((node.props["enableDate"] as? JsonPrimitive)?.booleanOrNull ?: false) || !enableTime
        val value = scope.readString(node.props["value"])
        val min = scope.readString(node.props["min"])
        val max = scope.readString(node.props["max"])
        var showDate by remember { mutableStateOf(false) }
        var showTime by remember { mutableStateOf(false) }
        // Date chosen in step 1 of a date+time flow; committed only with the time.
        var pendingDate by remember { mutableStateOf<String?>(null) }

        fun commit(newValue: String) {
            // Bounds are agent-supplied hints: enforce only well-formed ISO bounds of the
            // same class (date vs time), comparing on the common prefix so a date-only
            // bound still constrains a datetime value.
            fun ok(bound: String, test: (Int) -> Boolean): Boolean {
                if (bound.isEmpty() || !bound.matches(ISO_BOUND)) return true
                val boundIsDate = bound.length >= 10 && bound[4] == '-'
                val valueIsDate = newValue.length >= 10 && newValue[4] == '-'
                if (boundIsDate != valueIsDate) return true
                val n = minOf(bound.length, newValue.length)
                return test(newValue.take(n).compareTo(bound.take(n)))
            }
            if (ok(min) { it >= 0 } && ok(max) { it <= 0 }) {
                scope.write(node.props["value"], JsonPrimitive(newValue))
            }
        }

        OutlinedButton(onClick = { if (enableDate) showDate = true else showTime = true }) {
            Icon(Icons.Filled.Event, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            val label = scope.readString(node.props["label"])
            Text(
                value.ifEmpty {
                    label.ifEmpty {
                        when {
                            enableDate && enableTime -> "Pick date & time"
                            enableTime -> "Pick time"
                            else -> "Pick date"
                        }
                    }
                }
            )
        }

        if (showDate) {
            val state = rememberDatePickerState(initialSelectedDateMillis = parseIsoDateMillis(value))
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = formatIsoDate(millis)
                            if (enableTime) {
                                pendingDate = date   // commit only once the time is known
                                showTime = true      // only proceed when a date was actually picked
                            } else {
                                commit(date)
                            }
                        }
                        showDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            ) { DatePicker(state) }
        }

        if (showTime) {
            val timeState = rememberTimePickerState()
            AlertDialog(
                onDismissRequest = { showTime = false; pendingDate = null },
                confirmButton = {
                    TextButton(onClick = {
                        val time = "%02d:%02d".format(timeState.hour, timeState.minute)
                        val date = pendingDate
                            ?: value.take(10).takeIf { enableDate && it.length == 10 }
                        commit(if (date != null) "${date}T$time" else time)
                        pendingDate = null
                        showTime = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showTime = false; pendingDate = null }) { Text("Cancel") }
                },
                text = { TimePicker(timeState) },
            )
        }
    },

    // url · description
    "AudioPlayer" to { node, scope, _ ->
        val url = scope.readString(node.props["url"])
        if (A2uiUrlPolicy.allows(url)) {
            val context = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                }
            }
            var playing by remember(url) { mutableStateOf(false) }
            DisposableEffect(player) {
                // Track real playback so the icon stays honest when the track ends.
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (playing) {
                        player.pause()
                    } else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                        player.play()
                    }
                }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                    )
                }
                Text(
                    scope.readString(node.props["description"]).ifEmpty { url.substringAfterLast('/') },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    },

    // url · posterUrl
    "Video" to { node, scope, _ ->
        val url = scope.readString(node.props["url"])
        if (A2uiUrlPolicy.allows(url)) {
            val context = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            var started by remember(url) { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AndroidView(
                    factory = { PlayerView(it) },
                    // Rebind on url change: the factory runs once, but the player is new.
                    update = { it.player = player },
                    modifier = Modifier.matchParentSize(),
                )
                val poster = scope.readString(node.props["posterUrl"])
                if (!started && A2uiUrlPolicy.allows(poster)) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                player.play()
                                started = true
                            },
                    )
                }
            }
        }
    },
)

/** Well-formed DateTimeInput bounds: `yyyy-MM-dd`, `yyyy-MM-ddTHH:mm`, or `HH:mm`. */
private val ISO_BOUND = Regex("""\d{4}-\d{2}-\d{2}(T\d{2}:\d{2})?|\d{2}:\d{2}""")

/** The 59 icon names of the Basic Catalog, mapped to Material symbols. */
private fun materialIconFor(name: String): ImageVector? = when (name) {
    "accountCircle" -> Icons.Filled.AccountCircle
    "add" -> Icons.Filled.Add
    "arrowBack" -> Icons.Filled.ArrowBack
    "arrowForward" -> Icons.Filled.ArrowForward
    "attachFile" -> Icons.Filled.AttachFile
    "calendarToday" -> Icons.Filled.CalendarToday
    "call" -> Icons.Filled.Call
    "camera" -> Icons.Filled.Camera
    "check" -> Icons.Filled.Check
    "close" -> Icons.Filled.Close
    "delete" -> Icons.Filled.Delete
    "download" -> Icons.Filled.Download
    "edit" -> Icons.Filled.Edit
    "event" -> Icons.Filled.Event
    "error" -> Icons.Filled.Error
    "fastForward" -> Icons.Filled.FastForward
    "favorite" -> Icons.Filled.Favorite
    "favoriteOff" -> Icons.Filled.FavoriteBorder
    "folder" -> Icons.Filled.Folder
    "help" -> Icons.Filled.Help
    "home" -> Icons.Filled.Home
    "info" -> Icons.Filled.Info
    "locationOn" -> Icons.Filled.LocationOn
    "lock" -> Icons.Filled.Lock
    "lockOpen" -> Icons.Filled.LockOpen
    "mail" -> Icons.Filled.Mail
    "menu" -> Icons.Filled.Menu
    "moreVert" -> Icons.Filled.MoreVert
    "moreHoriz" -> Icons.Filled.MoreHoriz
    "notificationsOff" -> Icons.Filled.NotificationsOff
    "notifications" -> Icons.Filled.Notifications
    "pause" -> Icons.Filled.Pause
    "payment" -> Icons.Filled.Payment
    "person" -> Icons.Filled.Person
    "phone" -> Icons.Filled.Phone
    "photo" -> Icons.Filled.Photo
    "play" -> Icons.Filled.PlayArrow
    "print" -> Icons.Filled.Print
    "refresh" -> Icons.Filled.Refresh
    "rewind" -> Icons.Filled.FastRewind
    "search" -> Icons.Filled.Search
    "send" -> Icons.Filled.Send
    "settings" -> Icons.Filled.Settings
    "share" -> Icons.Filled.Share
    "shoppingCart" -> Icons.Filled.ShoppingCart
    "skipNext" -> Icons.Filled.SkipNext
    "skipPrevious" -> Icons.Filled.SkipPrevious
    "star" -> Icons.Filled.Star
    "starHalf" -> Icons.Filled.StarHalf
    "starOff" -> Icons.Filled.StarBorder
    "stop" -> Icons.Filled.Stop
    "upload" -> Icons.Filled.Upload
    "visibility" -> Icons.Filled.Visibility
    "visibilityOff" -> Icons.Filled.VisibilityOff
    "volumeDown" -> Icons.Filled.VolumeDown
    "volumeMute" -> Icons.Filled.VolumeMute
    "volumeOff" -> Icons.Filled.VolumeOff
    "volumeUp" -> Icons.Filled.VolumeUp
    "warning" -> Icons.Filled.Warning
    else -> null
}

/** Custom icon from a 24x24 SVG path string (spec: Icon.name.svgPath). */
private fun svgPathIcon(pathData: String): ImageVector? = runCatching {
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black), // tinted by Icon() via LocalContentColor
    ).build()
}.getOrNull()

private fun parseIsoDateMillis(value: String): Long? = runCatching {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    format.parse(value.take(10))?.time
}.getOrNull()

private fun formatIsoDate(millis: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(millis))
}
