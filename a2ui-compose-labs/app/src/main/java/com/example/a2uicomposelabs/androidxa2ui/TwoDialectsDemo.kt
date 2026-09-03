package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import com.example.a2uicomposelabs.a2ui.ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.catalog.BasicCatalog
import com.example.a2uicomposelabs.demos.SurveyCatalog
import com.example.a2uicomposelabs.demos.SurveyCatalogSchema
import kotlinx.coroutines.launch
import androidx.compose.material3.a2ui.A2uiSurface as AndroidxA2uiSurface
import com.example.a2uicomposelabs.a2ui.ui.A2uiSurface as OurA2uiSurface

/**
 * The restaurant booking and the survey, each rendered twice: once by androidx.a2ui reading a
 * v0.9.1 wire, once by this app's renderer reading a v1.0 wire.
 *
 * Both halves are live. The androidx half depends on the :androidx-a2ui module, which compiles
 * the AOSP snapshot checked in at the repo root, so the components on screen really are
 * MaterialA2uiBasicCatalogV1Defaults.slider, .card and friends.
 */
private enum class Scenario(val label: String, val surfaceId: String, val catalogId: String) {
    Booking("Booking", "booking", BOOKING_CATALOG_ID),
    Survey("Survey", "survey", SURVEY_CATALOG_ID),
}

private enum class Dialect(val label: String) {
    Androidx("androidx.a2ui · v0.9.1"),
    Ours("our renderer · v1.0"),
}

@Composable
fun TwoDialectsDemo(modifier: Modifier = Modifier) {
    var scenario by remember { mutableStateOf(Scenario.Booking) }
    var dialect by remember { mutableStateOf(Dialect.Androidx) }
    var showWire by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<String>() }

    val script =
        when (scenario to dialect) {
            Scenario.Booking to Dialect.Androidx -> BookingAndroidxScript
            Scenario.Booking to Dialect.Ours -> BookingV1Script
            Scenario.Survey to Dialect.Androidx -> SurveyAndroidxScript
            else -> SurveyV1Script
        }

    Column(
        modifier =
            modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Two dialects, one screen", style = MaterialTheme.typography.titleLarge)
        Text(
            "The same booking form and the same survey, sent twice. androidx.a2ui accepts " +
                "v0.9 and v0.9.1 only; our renderer speaks v1.0. Switch and watch what the " +
                "protocol change actually costs.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Scenario.entries.forEach { option ->
                FilterChip(
                    selected = scenario == option,
                    onClick = { scenario = option; events.clear() },
                    label = { Text(option.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dialect.entries.forEach { option ->
                FilterChip(
                    selected = dialect == option,
                    onClick = { dialect = option; events.clear() },
                    label = { Text(option.label) },
                )
            }
        }

        // Rebuild the whole stack when either choice changes: a processor holds one surface
        // per id, and replaying a second script into it would merge the two screens.
        key(scenario, dialect) {
            when (dialect) {
                Dialect.Androidx ->
                    AndroidxPane(script, scenario.surfaceId) { events.add(0, it) }
                Dialect.Ours ->
                    OurPane(script, scenario.surfaceId, scenario.catalogId) { events.add(0, it) }
            }
        }

        if (events.isNotEmpty()) {
            Text("Back to the agent", style = MaterialTheme.typography.titleSmall)
            events.take(3).forEach { line -> Mono(line) }
        }

        AssistChip(
            onClick = { showWire = !showWire },
            label = { Text(if (showWire) "Hide the wire" else "Show the wire") },
        )
        if (showWire) {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    script.forEach { line -> Mono(line) }
                }
            }
        }

        HorizontalDivider()
        Text("What actually differs", style = MaterialTheme.typography.titleSmall)
        DialectNotes.forEach { note ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.title, style = MaterialTheme.typography.labelLarge)
                    Text("v0.9.1  ${note.v091}", style = MaterialTheme.typography.bodySmall)
                    Text("v1.0  ${note.v10}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** The androidx.a2ui half: real A2uiMessageProcessor, real A2uiSurface. */
@Composable
private fun AndroidxPane(script: List<String>, surfaceId: String, onEvent: (String) -> Unit) {
    // Both catalogs go in; the processor picks by the catalogId in createSurface. Handing a
    // renderer everything it supports and letting the message choose is how a real one works —
    // and it means the booking agent cannot reach a survey component, because that component
    // is not in the catalog its message named.
    val processor =
        remember {
            A2uiMessageProcessor(
                catalogs = listOf(AndroidxBookingCatalog, AndroidxSurveyCatalog)
            )
        }
    val parser = remember { A2uiMessageParser() }
    val surfaces by processor.activeSurfaces.collectAsStateWithLifecycle()

    LaunchedEffect(processor) {
        // collectMessages never returns: it is the engine's loop. Without it the queue fills
        // and nothing is ever drawn.
        //
        // The AOSP sample runs this on Dispatchers.Default. Don't. The loop builds the surface
        // out of Compose snapshot state, and creating that state on a background thread while
        // a recomposition is already in flight throws "Reading a state that was created after
        // the snapshot was taken". Left on the composition's own dispatcher the loop runs
        // between frames instead of during one, and the race is gone.
        launch { processor.collectMessages() }
        script.forEach { line -> processor.processInput(parser, line) }
    }
    LaunchedEffect(processor) {
        processor.outboundEvents.collect { message -> onEvent(describe(message)) }
    }

    val surface = surfaces.firstOrNull { it.id == surfaceId }
    if (surface == null) {
        Text("waiting for the agent…", style = MaterialTheme.typography.bodyMedium)
    } else {
        AndroidxA2uiSurface(surfaceModel = surface, modifier = Modifier.fillMaxWidth())
    }
}

/** Our own renderer, reading the v1.0 twin of the same screen. */
@Composable
private fun OurPane(
    script: List<String>,
    surfaceId: String,
    catalogId: String,
    onEvent: (String) -> Unit,
) {
    // The same id the androidx half was given, so the pair differs only in protocol version.
    val schema = remember(catalogId) { SurveyCatalogSchema.withId(catalogId) }
    val client = remember { A2uiClient(schema) }
    val registry = remember { ComponentRegistry(BasicCatalog) + SurveyCatalog }

    LaunchedEffect(client) { script.forEach(client::apply) }

    val surface = client.surfaces[surfaceId]
    if (surface == null) {
        Text("waiting for the agent…", style = MaterialTheme.typography.bodyMedium)
    } else {
        OurA2uiSurface(
            state = surface,
            registry = registry,
            onAction = { action -> onEvent("${action.name}  ${action.context}") },
            catalog = schema,
        )
    }
}

private fun describe(message: A2uiClientToServerMessage): String =
    when (message) {
        is A2uiClientEventMessage -> "${message.type}  ${message.context}"
        is A2uiClientErrorMessage -> "error ${message.code}  ${message.message}"
    }

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    )
}
