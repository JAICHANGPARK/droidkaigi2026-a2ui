package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.model.A2uiAction
import com.example.a2uicomposelabs.a2ui.ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.catalog.BasicCatalog
import com.example.a2uicomposelabs.a2ui.ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.runtime.SurfaceState
import com.example.a2uicomposelabs.agent.AgentChunk
import com.example.a2uicomposelabs.agent.a2uiSystemPrompt
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Demo 8 — one chat that books a table, joins tonight's queue, and takes a delivery order.
 *
 * Three flows in a single conversation, on one catalog. What makes them worth showing together
 * is that they fail differently:
 *
 * **Reservation** is a form. Every keystroke, the phone-number `regex`, the disabled submit
 * button — all of it happens inside the renderer. The network sees exactly one message, when
 * the button is finally pressed.
 *
 * **Delivery** is a form that has to *count*. The moment a second dish is added, something must
 * multiply price by quantity and add the results up, and A2UI has no operator that can. This
 * app registers [DiningFunctions] so the renderer does it locally: tap `+` and the total, the
 * item count and the submit button's own check all re-evaluate in the same frame, still without
 * a single byte leaving the device.
 *
 * **Waitlist** is neither. There is no form to get wrong after the first screen: the ticket is
 * issued, and from then on the position, the ten-minute hold on a ready table and the ending —
 * seated, left, or expired — all arrive as `updateDataModel` into paths the screen is already
 * bound to. Not one component is re-sent, and the queue keeps moving while this screen is not
 * even in the back stack.
 *
 * The last thing on show is who answers an action. When `confirm_reservation` or `place_order`
 * comes back, there is no agent in the loop — the app assigns the booking code, prices the
 * basket, and replaces the surface it already owns. An A2UI surface is a document both sides may
 * write to, not a screenshot the agent posted.
 */

private val Examples = listOf(
    "Book a table for two at 7pm tonight",
    "Put us on the waitlist for four",
    "Actually, let's just order delivery",
    "Table for four on Friday evening",
    "What can I get delivered for lunch?",
)

/** What the live agent is told on top of the catalog. */
private val DINING_SCREEN_RULES = """
This is a restaurant assistant. A conversation can move between three jobs, and you decide which
one the user is asking for:

$DINING_RULES

Keep every message SHORT — at most two components per updateComponents, several messages rather
than one long one. A long message that ends up malformed is rejected whole.
""".trimIndent()

/** Every Dining conversation this process has held. Outlives the screen on purpose. */
internal val DiningSessions = ChatSessionStore(DiningCatalogSchema)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiningDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = rememberAgentSettings()
    val agent = remember(settings.effectiveApiKey, settings.effectiveModel) {
        settings.newAgent()
    }
    val store = DiningSessions
    // Reading currentId here is what re-runs this body when the user switches conversation.
    val session = run { store.currentId; store.current() }
    val client = session.client
    val transcript = session.transcript
    val wire = session.wire
    val registry = remember { ComponentRegistry(BasicCatalog) + DiningCatalog }
    val listState = rememberLazyListState()
    var showWire by remember { mutableStateOf(false) }

    // Keep the newest turn in view. Driven from the composition rather than the session's
    // job: a scroll animation needs the frame clock, which only a composition scope carries.
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.size - 1)
    }

    // The other end of the wire.
    //
    // Everything the renderer says back arrives here as raw JSON, and [DiningHouse] answers the
    // questions among it. Owned by the session rather than the composition on purpose: a call
    // in flight when the user walks off this screen still needs somebody listening, or it sits
    // there until it times out.
    LaunchedEffect(session) {
        session.launchBackground("house-${session.id}") {
            client.outbound.collect { line ->
                wire += line
                DiningHouse.answer(line)?.let { reply ->
                    wire += reply
                    client.apply(reply)
                }
            }
        }
    }

    /** Plays a recorded stream into [surfaceId], stamping this turn's ids into the template. */
    suspend fun play(asset: String, surfaceId: String, values: Map<String, String> = emptyMap()) {
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
     * Fills `/menu` from the app. The agent never names a dish or a price.
     *
     * Two ways in: it asks with `{"kind":"menu"}`, or it writes dishes of its own and the app
     * quietly puts the real ones back. Both end at the same place, which is the point.
     */
    fun fulfilRequest(surface: SurfaceState): Boolean {
        val request = surface.read("/request") as? JsonObject
        val kind = (request?.get("kind") as? JsonPrimitive)?.contentOrNull
        if (kind != null) {
            surface.updateData("/request", null)
            if (fulfilMenuRequest(surface, kind)) return true
        }
        return enforceHouseMenu(surface)
    }

    /** Every action here is the restaurant's; the shared flow answers all of them. */
    fun handleAction(action: A2uiAction) {
        val surface = client.surfaces[action.surfaceId] ?: return
        transcript.add(
            ChatTurn.Assistant(
                actionSummary(action),
                // A question is not an action message, so do not print one. What went out for
                // it is the resolved call; the envelope around it, functionCallId and all, is
                // in the wire panel where it can be read next to the answer.
                copyText = prettyJson(action.functionCall ?: action.toJson()),
            )
        )
        session.scope.launch {
                // Only what the app published actually runs. An action it never declared
                // is not a crash and not a guess — it is simply reported, so the button
                // that did nothing says why it did nothing.
            val handled = runDiningAction(
                action = action,
                session = session,
                play = { asset, values -> play(asset, action.surfaceId, values) },
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
        // Each turn reports its own failures. Without this the last error of the
        // session stays pinned under the input box, over turns that went fine.
        client.errors.clear()
        session.input = ""
        session.running = true
        session.rename(prompt)
        val surfaceId = session.nextSurfaceId()
        transcript.add(ChatTurn.User(prompt))
        // Which recording answers this turn if the live model never gets there.
        val recorded = diningAssetFor(prompt)

        session.turnJob = session.scope.launch {
            try {
                if (agent.isConfigured) {
                    val errorsBefore = client.errors.size
                    val systemPrompt = a2uiSystemPrompt(DiningCatalogSchema, surfaceId, DINING_SCREEN_RULES)
                    var announced = false
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
                                client.surfaces[surfaceId]?.let(::fulfilRequest)
                            }
                        }
                    }
                    prose.toString().trim().takeIf(String::isNotBlank)
                        ?.let { transcript.add(ChatTurn.Assistant(it)) }

                    // A rejected message or a shell with nothing in it leaves the user staring at
                    // an empty card. Rejecting bad output is right; showing nothing is not.
                    val built = client.surfaces[surfaceId]?.components?.size ?: 0
                    if (client.errors.size > errorsBefore || built <= 1) {
                        // Say which of the several possible things actually happened, and
                        // quote the model where there is something to quote. "Rejected by the
                        // catalog" is a real event worth showing; being cut off mid-message is
                        // a different one, and calling it a rejection blames the wrong thing.
                        transcript.add(
                            ChatTurn.Assistant(
                                when {
                                    agent.lastFinishReason == "MAX_TOKENS" ->
                                        "The model ran out of output budget mid-message, so " +
                                            "this answer is a recorded one."
                                    agent.lastFailures.isNotEmpty() ->
                                        "The model could not finish the screen, so this answer " +
                                            "is a recorded one. What it got wrong:\n" +
                                            agent.lastFailures.joinToString("\n") { "· $it" }
                                    client.errors.size > errorsBefore ->
                                        "The model's UI was rejected by the catalog, so this " +
                                            "answer is a recorded one."
                                    else ->
                                        "The model stopped before the screen was finished, so " +
                                            "this answer is a recorded one."
                                }
                            )
                        )
                        if (!announced) {
                            announced = true
                            transcript.add(ChatTurn.Ui(surfaceId))
                        }
                        play(recorded, surfaceId)
                    }
                } else {
                    transcript.add(ChatTurn.Assistant("No API key, so this turn replays a recorded answer."))
                    transcript.add(ChatTurn.Ui(surfaceId))
                    play(recorded, surfaceId)
                }
                client.surfaces[surfaceId]?.let(::fulfilRequest)
            } catch (e: Exception) {
                transcript.add(ChatTurn.Assistant("Failed: ${e.message ?: e::class.simpleName}"))
            } finally {
                session.running = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding().padding(16.dp)) {
        Text("Dining", style = MaterialTheme.typography.titleLarge)
        Text(
            "One chat that books a table, joins the queue, and takes a delivery order. Change a " +
                "quantity and watch the total follow — that sum is a catalog function, because " +
                "the protocol has no arithmetic of its own. Join the queue and watch the line " +
                "move with no components sent at all.",
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
                            A2uiSurface(
                                state = surface,
                                registry = registry,
                                onAction = ::handleAction,
                                // Without the catalog the scope gets EmptyEvaluator, and every
                                // {"call": ...} — the total, the price labels, the receipt —
                                // silently resolves to nothing.
                                catalog = DiningCatalogSchema,
                            )
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
                    // Six, not five: a join is a question and an answer, and the four messages
                    // that draw the queue screen land on top of them straight away.
                    wire.takeLast(6).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 4,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 2.dp),
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

