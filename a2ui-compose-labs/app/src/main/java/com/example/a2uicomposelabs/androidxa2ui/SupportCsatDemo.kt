package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.a2uicomposelabs.agent.A2uiAgent
import com.example.a2uicomposelabs.agent.AgentChunk
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.a2ui.A2uiSurface as AndroidxA2uiSurface

/**
 * Demo 10 — the survey that could not have been written in advance.
 *
 * A support team wants to know how a ticket felt to the person who raised it. The trouble is
 * that "how did we do" depends entirely on what they came for: a parcel that arrived three days
 * late and an account someone was locked out of have no question in common worth asking. The
 * usual answer is one generic form for everything, which is why nobody fills those in.
 *
 * So the form is generated per ticket. Pick one of the four below and the agent writes five
 * questions about that ticket — and androidx.a2ui, the real one, renders them. Nothing here
 * knows what the questions will be; the app supplies the ticket, the catalog and the surface,
 * and reads the answers back off the paths it agreed to.
 *
 * Two things happen after the customer taps send, and both are the app's doing rather than the
 * agent's. An empty rating is refused — this dialect has no `checks`, so nothing on the wire
 * could have stopped it. And one or two stars gets a follow-up written into the surface that is
 * already on screen: three more components into an id the form left empty, no regeneration, no
 * second model call. The agent wrote the screen; the app still owns what happens on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportCsatDemo(onOpenSettings: () -> Unit = {}, modifier: Modifier = Modifier) {
    val settings = rememberAgentSettings()
    val agent =
        remember(settings.effectiveApiKey, settings.effectiveModel) { settings.newAgent() }
    // Building it means serializing the catalog, so do it once rather than on every keystroke.
    val systemPrompt = remember { csatSystemPrompt() }
    val run = remember { CsatRun() }

    var chosen by remember { mutableStateOf(SupportTickets.first()) }
    var preferLive by remember { mutableStateOf(false) }
    var showWire by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf<SupportTicket?>(null) }
    val live = preferLive && agent.isConfigured

    Column(
        modifier =
            modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Support satisfaction", style = MaterialTheme.typography.titleLarge)
        Text(
            "One closed ticket, one survey about that ticket. The questions on a late parcel " +
                "and the questions on a locked account have nothing in common, so there is no " +
                "form to reuse — which is the case A2UI is for. Rendered by androidx.a2ui.",
            style = MaterialTheme.typography.bodySmall,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportTickets.forEach { ticket ->
                FilterChip(
                    selected = ticket == chosen,
                    onClick = { chosen = ticket },
                    label = { Text(ticket.kind.label) },
                )
            }
        }

        TicketCard(chosen)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = preferLive,
                onCheckedChange = { preferLive = it },
                enabled = agent.isConfigured,
            )
            Text(
                when {
                    !agent.isConfigured -> "  Replaying a recorded generation"
                    preferLive -> "  Writing the form with ${settings.provider.label}"
                    else -> "  Replaying a recorded generation"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!agent.isConfigured) {
            TextButton(onClick = onOpenSettings) { Text("Add an API key to generate live") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    run.reset()
                    runId++
                    asked = chosen
                },
                enabled = !run.running,
            ) {
                Text(if (asked == null) "Ask for the survey" else "Ask again")
            }
            if (run.running) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            run.source?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }

        run.failure?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (run.prose.isNotBlank()) {
            Text(run.prose, style = MaterialTheme.typography.bodyMedium)
        }

        asked?.let { ticket ->
            // A processor holds one surface per id, so a second generation has to start from a
            // new one — replaying into the old one would merge two forms into one screen.
            key(runId) {
                CsatPane(
                    ticket = ticket,
                    live = live,
                    agent = agent,
                    systemPrompt = systemPrompt,
                    sourceLabel = "${settings.provider.label} · ${settings.effectiveModel}",
                    run = run,
                )
            }
        }

        if (run.needsRating) {
            Text(
                "Rate the first question before sending. Nothing on this wire could have " +
                    "stopped an empty form — v0.9.1 has no checks — so the app checks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (run.refusals.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Refused by the engine (${run.refusals.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "The catalog is the allowlist. These never reached the screen, and the " +
                            "complaint went back to the model as the tool's result.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    run.refusals.forEach { Mono(it) }
                }
            }
        }

        run.submitted?.let { answers -> SubmittedCard(answers, run.followUpAsked) }

        if (run.wire.isNotEmpty()) {
            AssistChip(
                onClick = { showWire = !showWire },
                label = {
                    Text(if (showWire) "Hide the wire" else "Show the wire (${run.wire.size})")
                },
            )
            if (showWire) {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        run.wire.forEach { line -> Mono(line) }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("What this one shows", style = MaterialTheme.typography.titleSmall)
        Notes.forEach { note ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.first, style = MaterialTheme.typography.labelLarge)
                    Text(note.second, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** What one generation is doing, and what came of it. Held above the pane so it survives it. */
@Stable
private class CsatRun {
    var running by mutableStateOf(false)
    var source by mutableStateOf<String?>(null)
    var prose by mutableStateOf("")
    var failure by mutableStateOf<String?>(null)
    var followUpAsked by mutableStateOf(false)
    var needsRating by mutableStateOf(false)
    var submitted by mutableStateOf<List<Pair<String, String>>?>(null)
    var closing by mutableStateOf(false)
    val wire = mutableStateListOf<String>()
    val refusals = mutableStateListOf<String>()

    fun reset() {
        running = false
        source = null
        prose = ""
        failure = null
        followUpAsked = false
        needsRating = false
        submitted = null
        closing = false
        wire.clear()
        refusals.clear()
    }
}

/** How long a recorded line waits before the next one, so the form builds itself on stage. */
private const val REPLAY_DELAY_MS = 380L

/**
 * The androidx.a2ui half: a real [A2uiMessageProcessor], a real surface, real Material
 * components. Everything that fills it — the model, the recording, and the app's own follow-up
 * — arrives through the same door, [processInput].
 */
@Composable
private fun CsatPane(
    ticket: SupportTicket,
    live: Boolean,
    agent: A2uiAgent,
    systemPrompt: String,
    sourceLabel: String,
    run: CsatRun,
    modifier: Modifier = Modifier,
) {
    val processor = remember { A2uiMessageProcessor(catalogs = listOf(AndroidxSupportCatalog)) }
    val parser = remember { A2uiMessageParser() }
    val surfaces by processor.activeSurfaces.collectAsStateWithLifecycle()
    val recorded = remember(ticket.id) { RecordedCsat.getValue(ticket.id) }
    // Serializing the catalog is not free; the reply turn needs its own prompt, once.
    val closingPrompt = remember { csatClosingPrompt() }

    LaunchedEffect(processor) {
        fun send(line: String) {
            run.wire += line
            processor.processInput(parser, line)
        }

        /** The tool's result: null when the engine took the message, the complaint when not. */
        suspend fun accept(line: String): String? {
            val before = run.refusals.size
            toAndroidxDialect(line).forEach(::send)
            // The engine reports refusals asynchronously, on the same channel it sends events
            // on, so there is nothing to return yet. Give its loop a beat to answer.
            delay(120)
            return run.refusals.drop(before).firstOrNull()
        }

        /**
         * The second turn: the answers go back to the model, and what comes back is a screen.
         *
         * This is the loop closing. The first turn was given a ticket and wrote a form; this one
         * is given the form's answers and writes the reply — same tool, same catalog, same
         * dialect adapter, and a surface of its own because v0.9.1 will not let createSurface
         * reuse a live id.
         *
         * The recorded fallback is deliberately generic. A reply that never read the answers
         * cannot name them, and dressing one up would be this demo lying about the one thing it
         * exists to show.
         */
        suspend fun reply(summary: List<Pair<String, String>>, lowRating: Boolean) {
            run.closing = true
            try {
                if (live) {
                    agent
                        .streamUi(
                            systemPrompt = closingPrompt,
                            userPrompt = closingBriefing(ticket, summary),
                            surfaceId = CSAT_DONE_SURFACE_ID,
                            applyUi = ::accept,
                        )
                        .collect { /* the reply is the screen; prose is not needed here */ }
                }
            } catch (e: Exception) {
                run.failure = e.message ?: e::class.simpleName
            }

            if (processor.activeSurfaces.value.none { it.id == CSAT_DONE_SURFACE_ID }) {
                recordedClosing(ticket.id, lowRating).forEach { delay(REPLAY_DELAY_MS); send(it) }
            }
            // Only now: the form has been replaced, so there is nothing to look at while it goes.
            send(deleteFormSurface())
            run.closing = false
        }

        suspend fun onSubmit(event: A2uiClientEventMessage) {
            val answers = event.context["answers"] as? Map<*, *>
            val overall = (answers?.get("overall") as? Number)?.toInt() ?: 0
            when {
                // No `checks` in this dialect: the button was always live. Whether a form may
                // be sent was never the agent's call anyway.
                overall == 0 -> run.needsRating = true
                // The app's own rule, kept out of the prompt on purpose: a policy the support
                // organisation depends on should not be something a model can decide to skip.
                overall <= 2 && !run.followUpAsked -> {
                    run.needsRating = false
                    run.followUpAsked = true
                    recorded.followUp.forEach(::send)
                }
                else -> {
                    run.needsRating = false
                    val summary = readable(answers ?: event.context)
                    run.submitted = summary
                    reply(summary, lowRating = overall in 1..2)
                }
            }
        }

        // collectMessages never returns: it is the engine's loop, and without it the queue
        // fills and nothing is drawn. It stays on the composition's dispatcher deliberately —
        // the AOSP sample puts it on Dispatchers.Default, which races the snapshot system.
        launch { processor.collectMessages() }
        launch {
            processor.outboundEvents.collect { message ->
                when (message) {
                    is A2uiClientErrorMessage -> run.refusals += "${message.code}  ${message.message}"
                    is A2uiClientEventMessage -> onSubmit(message)
                }
            }
        }

        run.running = true
        try {
            if (live) {
                run.source = sourceLabel
                val spoken = StringBuilder()
                agent
                    .streamUi(
                        systemPrompt = systemPrompt,
                        userPrompt = ticket.briefing(),
                        surfaceId = CSAT_SURFACE_ID,
                        applyUi = ::accept,
                    )
                    .collect { chunk ->
                        if (chunk is AgentChunk.Prose) {
                            spoken.append(chunk.text)
                            run.prose = spoken.toString().trim()
                        }
                    }
            }
        } catch (e: Exception) {
            run.failure = e.message ?: e::class.simpleName
        }

        // Whatever happened up there, the room still needs a form. A surface that never arrived
        // is the honest signal: the model answered in prose, or every component it sent was
        // refused. Deliberately not in a `finally` — when this effect is cancelled because the
        // screen moved on, there is nobody left to replay a form to.
        if (processor.activeSurfaces.value.none { it.id == CSAT_SURFACE_ID }) {
            if (live && run.failure == null) {
                run.failure = "No screen came back from the model — replaying the recording."
            }
            run.source = "Recorded generation"
            recorded.form.forEach { line ->
                delay(REPLAY_DELAY_MS)
                send(line)
            }
        }
        run.running = false
    }

    // The reply, once there is one; the form until then. Both are real surfaces on the same
    // processor, and for one beat both exist — which is exactly when the form gets deleted.
    val surface =
        surfaces.firstOrNull { it.id == CSAT_DONE_SURFACE_ID }
            ?: surfaces.firstOrNull { it.id == CSAT_SURFACE_ID }
    if (surface == null) {
        Text(
            if (run.running || run.closing) "waiting for the agent…"
            else "Nothing on the surface.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    } else {
        AndroidxA2uiSurface(surfaceModel = surface, modifier = modifier.fillMaxWidth())
    }
}

/** The ticket the survey will be about — the app's facts, not the agent's. */
@Composable
private fun TicketCard(ticket: SupportTicket) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "#${ticket.id} · ${ticket.kind.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(ticket.subject, style = MaterialTheme.typography.titleSmall)
            Text(ticket.history, style = MaterialTheme.typography.bodySmall)
            Text(
                "${ticket.channel} · closed in ${ticket.daysToClose} days · ${ticket.handledBy}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * What came back. The app never learned the questions — it gets the answers keyed by the paths
 * the agent was told to bind, which is exactly as much as a form's own submit button knows.
 */
@Composable
private fun SubmittedCard(answers: List<Pair<String, String>>, hadFollowUp: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sent to the support team", style = MaterialTheme.typography.titleSmall)
            Text(
                if (hadFollowUp) {
                    "Including the follow-up the app added after the low rating."
                } else {
                    "Resolved from the data model by the renderer and handed to the app. " +
                        "Nothing left the device."
                },
                style = MaterialTheme.typography.labelSmall,
            )
            answers.forEach { (path, value) ->
                Column {
                    Text(path, style = MaterialTheme.typography.labelSmall)
                    Text(
                        value.ifBlank { "— not answered" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * The event's `answers` object as lines, the app's own follow-up last.
 *
 * The order is the data model's, not the form's — a JSON object has no question order to
 * recover. The paths are all the app has, and they are enough, which is the point.
 */
private fun readable(answers: Map<*, *>?): List<Pair<String, String>> =
    flatten(answers).partition { (path, _) -> !path.contains('/') }.let { (own, nested) ->
        own + nested
    }

private fun flatten(answers: Map<*, *>?, prefix: String = ""): List<Pair<String, String>> =
    answers.orEmpty().flatMap { (key, value) ->
        val name = "$prefix$key"
        when (value) {
            is Map<*, *> -> flatten(value, "$name/")
            is List<*> -> listOf(name to value.joinToString(", "))
            else -> listOf(name to value?.toString().orEmpty())
        }
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

private val Notes: List<Pair<String, String>> =
    listOf(
        "The questions are the variable, not the data" to
            "Four tickets, four different sets of five questions, and the middle three could " +
                "not be swapped between them. A template would have to ask all twenty.",
        "One catalog, ten components, no new screen" to
            "AndroidxSupportCatalog is fixed and this file draws nothing itself. Six of the " +
                "ten come from material3-a2ui; Question, StarRating, ChoicePicker and " +
                "TextField are written in this app because the library has no equivalent yet.",
        "The app writes to the surface too" to
            "A rating of one or two stars fills in the empty `more` container the form left " +
                "behind — three components into a screen that is already up, from the app, " +
                "with no second call to the model.",
    )
