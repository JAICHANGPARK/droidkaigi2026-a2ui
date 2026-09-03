package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.a2ui.model.A2uiAction
import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import com.example.a2uicomposelabs.a2ui.runtime.BindingScope
import com.example.a2uicomposelabs.a2ui.runtime.SurfaceState
import com.example.a2uicomposelabs.demos.ChatSession
import com.example.a2uicomposelabs.demos.DiningCatalogSchema
import com.example.a2uicomposelabs.agent.A2uiToolCall
import com.example.a2uicomposelabs.demos.DiningHouse
import com.example.a2uicomposelabs.demos.HOLD_MINUTES
import com.example.a2uicomposelabs.demos.WaitlistStages
import com.example.a2uicomposelabs.demos.showClosed
import com.example.a2uicomposelabs.demos.showHeld
import com.example.a2uicomposelabs.demos.runDiningAction
import com.example.a2uicomposelabs.demos.showQueueing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The waitlist's whole claim is that after the ticket is issued nothing but data ever moves.
 *
 * That claim is only worth making if the data actually reaches the screen, so none of this
 * asserts on the writers directly. It plays the recorded streams, calls what the ticker calls,
 * and then reads the components back through the same [BindingScope] the renderer uses — so a
 * path renamed in the JSON and not in Kotlin fails here rather than in front of a room.
 */
class DiningWaitlistTest {

    private val surfaceId = "t1"

    private fun asset(name: String): List<String> =
        (listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull(File::exists) ?: error("$name not found"))
            .readLines().filter(String::isNotBlank)

    /** Plays the given recordings onto one surface and hands back what the renderer would see. */
    private fun surfaceAfter(vararg assets: String): Pair<SurfaceState, BindingScope> {
        val client = A2uiClient(DiningCatalogSchema)
        assets.flatMap(::asset).forEach { line ->
            client.apply(line.replace("__turn__", surfaceId))
        }
        assertEquals("the recorded stream was rejected: ${client.errors}", 0, client.errors.size)

        val surface = client.surfaces.getValue(surfaceId)
        // What join_waitlist writes before the queue screen is played.
        surface.updateData("/waitlist/ticket", JsonPrimitive("W-42"))
        surface.updateData("/waitlist/party", JsonPrimitive(4))
        surface.updateData("/waitlist/steps", JsonArray(WaitlistStages.map(::JsonPrimitive)))
        return surface to BindingScope(
            surface = surface,
            onAction = {},
            itemBase = null,
            evaluator = DiningCatalogSchema.evaluator,
        )
    }

    /** The queue screen exactly as `join_waitlist` leaves it, ticket and all. */
    private fun queueSurface() =
        surfaceAfter("dining_waitlist.jsonl", "dining_waitlist_done.jsonl")

    /** The ending screen, which serves seated, left and expired alike. */
    private fun closedSurface() = surfaceAfter(
        "dining_waitlist.jsonl", "dining_waitlist_done.jsonl", "dining_waitlist_closed.jsonl",
    )

    private fun BindingScope.textOf(surface: SurfaceState, id: String): String =
        readString(surface.components.getValue(id).props["text"])

    private fun SurfaceState.int(path: String): Int? =
        (read(path) as? JsonPrimitive)?.intOrNull

    private fun SurfaceState.text(path: String): String? =
        (read(path) as? JsonPrimitive)?.contentOrNull

    @Test
    fun `the queue line reads as one sentence on screen`() {
        val (surface, scope) = queueSurface()
        surface.showQueueing(3, "Jai")

        assertEquals("Ticket W-42 · party of 4", scope.textOf(surface, "q_title"))
        assertEquals("3 ahead of you · about 15 min", scope.textOf(surface, "q_line"))
        assertEquals("3 teams ahead of you.", surface.text("/waitlist/note"))
    }

    @Test
    fun `the stages advance as the line shortens, with no components sent`() {
        val (surface, _) = queueSurface()
        val before = surface.components.keys.toSet()

        surface.showQueueing(5, "Jai")
        assertEquals(0, surface.int("/waitlist/current"))
        surface.showQueueing(3, "Jai")
        assertEquals(1, surface.int("/waitlist/current"))
        surface.showQueueing(1, "Jai")
        assertEquals(2, surface.int("/waitlist/current"))
        assertEquals("One team ahead of you. Stay close.", surface.text("/waitlist/note"))

        assertEquals("the queue moved by sending components", before, surface.components.keys.toSet())
    }

    @Test
    fun `a ready table shows the hold, and the hold counts down`() {
        val (surface, scope) = queueSurface()

        surface.showHeld(HOLD_MINUTES, "Jai")
        assertEquals(WaitlistStages.lastIndex, surface.int("/waitlist/current"))
        assertEquals("Table ready · held for 10 more min", scope.textOf(surface, "q_line"))
        assertTrue(surface.text("/waitlist/note")!!.contains("Come to the host stand"))

        surface.showHeld(4, "Jai")
        assertEquals("Table ready · held for 4 more min", scope.textOf(surface, "q_line"))
    }

    @Test
    fun `the last two minutes say what happens when they run out`() {
        val (surface, _) = queueSurface()

        surface.showHeld(2, "Jai")
        assertEquals("2 min left before the table goes to the next party.", surface.text("/waitlist/note"))
        surface.showHeld(1, "Jai")
        assertEquals("1 min left before the table goes to the next party.", surface.text("/waitlist/note"))
    }

    @Test
    fun `seated, left and expired are the same screen with different words`() {
        val (surface, scope) = closedSurface()

        surface.showClosed("seated", "The table is yours. Enjoy the meal.")
        assertEquals("Ticket W-42 · seated", scope.textOf(surface, "c_title"))
        assertEquals("The table is yours. Enjoy the meal.", scope.textOf(surface, "c_note"))

        val components = surface.components.keys.toSet()
        surface.showClosed("hold expired", "Nobody arrived, so the table went to the next party.")
        assertEquals("Ticket W-42 · hold expired", scope.textOf(surface, "c_title"))
        assertEquals("a second ending sent components", components, surface.components.keys.toSet())
    }

    /**
     * The one that would have caught renaming `/waitlist/ahead` to `/waitlist/line` in the JSON
     * and forgetting the Kotlin — a screen that renders without complaint and says nothing.
     */
    @Test
    fun `every waitlist path the screens read is a path the app writes`() {
        val paths = (asset("dining_waitlist_done.jsonl") + asset("dining_waitlist_closed.jsonl"))
            .flatMap { Regex("/waitlist/[A-Za-z]+").findAll(it).map(MatchResult::value) }
            .toSortedSet()
        assertTrue("no waitlist paths found — did the assets move?", paths.size >= 6)

        val (surface, _) = closedSurface()
        surface.showQueueing(3, "Jai")
        surface.showHeld(HOLD_MINUTES, "Jai")
        surface.showClosed("seated", "The table is yours.")

        val missing = paths.filter { surface.read(it) == null }
        assertEquals("paths the screen reads but nothing writes: $missing", emptyList<String>(), missing)
    }

    // -----------------------------------------------------------------------
    // The two endings a person can choose, driven through the real handler.
    // -----------------------------------------------------------------------

    /** A session holding the queue screen, plus what the app said and replayed into it. */
    private class Waiting {
        val played = mutableListOf<String>()
        val said = mutableListOf<String>()
        val session = ChatSession("s1", DiningCatalogSchema, CoroutineScope(UnconfinedTestDispatcher()))

        val surface: SurfaceState get() = session.client.surfaces.getValue("t1")

        fun tap(name: String) = A2uiAction(
            name = name,
            surfaceId = "t1",
            sourceComponentId = "q_here",
            timestamp = "2026-08-20T12:00:00Z",
            context = JsonObject(emptyMap()),
        )
    }

    private fun waiting(ahead: Int): Waiting {
        val w = Waiting()
        (asset("dining_waitlist.jsonl") + asset("dining_waitlist_done.jsonl")).forEach { line ->
            w.session.client.apply(line.replace("__turn__", "t1"))
        }
        w.surface.updateData("/waitlist/ticket", JsonPrimitive("W-42"))
        w.surface.updateData("/waitlist/steps", JsonArray(WaitlistStages.map(::JsonPrimitive)))
        if (ahead > 0) w.surface.showQueueing(ahead, "Jai") else w.surface.showHeld(HOLD_MINUTES, "Jai")
        return w
    }

    private suspend fun Waiting.run(name: String): Boolean = runDiningAction(
        action = tap(name),
        session = session,
        play = { assetName, _ ->
            played += assetName
            asset(assetName).forEach { session.client.apply(it.replace("__turn__", "t1")) }
        },
        say = { said += it },
    )

    @Test
    fun `turning up early is answered, not obeyed`() = runTest {
        val w = waiting(ahead = 2)

        assertTrue(w.run("check_in_waitlist"))
        assertEquals(listOf("Not yet — 2 teams are still ahead of you."), w.said)
        assertEquals("an early arrival changed the screen", emptyList<String>(), w.played)
        assertEquals(2, w.surface.int("/waitlist/ahead"))
    }

    @Test
    fun `turning up to a ready table seats the ticket`() = runTest {
        val w = waiting(ahead = 0)

        assertTrue(w.run("check_in_waitlist"))
        assertEquals(listOf("dining_waitlist_closed.jsonl"), w.played)
        assertEquals("seated", w.surface.text("/waitlist/outcome"))
        assertTrue(w.said.single().contains("W-42"))
    }

    @Test
    fun `leaving says no table is being held`() = runTest {
        val w = waiting(ahead = 3)

        assertTrue(w.run("cancel_waitlist"))
        assertEquals(listOf("dining_waitlist_closed.jsonl"), w.played)
        assertEquals("left the queue", w.surface.text("/waitlist/outcome"))
    }

    // -----------------------------------------------------------------------
    // v1.0's two-way half: the button that asks.
    // -----------------------------------------------------------------------

    /**
     * The whole round trip, in the JSON both ends actually exchange.
     *
     * Joining is the one moment in this app where the renderer speaks first. Everything else it
     * says is an `action` — fired and forgotten, with no id and nowhere for an answer to land,
     * because v1.0 deleted `actionResponse`. This is the other kind: a question with an id on
     * it, answered by the house with the same id, and only then is there a ticket to draw.
     */
    @Test
    fun `joining the queue goes out as a question and comes back as a ticket`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = ChatSession("s1", DiningCatalogSchema, scope)
        val client = session.client
        val wire = mutableListOf<String>()
        val played = mutableListOf<String>()
        val said = mutableListOf<String>()

        // The other end of the wire, exactly as the demo screen wires it.
        scope.launch {
            client.outbound.collect { line ->
                wire += line
                DiningHouse.answer(line)?.let { reply -> wire += reply; client.apply(reply) }
            }
        }

        asset("dining_waitlist.jsonl").forEach { client.apply(it.replace("__turn__", "t1")) }
        assertEquals("the join form was rejected: ${client.errors}", 0, client.errors.size)
        val surface = client.surfaces.getValue("t1")
        surface.updateData("/waitlist/name", JsonPrimitive("Jai"))
        surface.updateData("/waitlist/phone", JsonPrimitive("010-1234-5678"))
        surface.updateData("/waitlist/party", JsonPrimitive(4))

        // The tap.
        var tapped: A2uiAction? = null
        BindingScope(
            surface = surface,
            onAction = { tapped = it },
            itemBase = null,
            evaluator = DiningCatalogSchema.evaluator,
        ).dispatchAction(surface.components.getValue("join"))

        val action = tapped ?: error("the join button dispatched nothing")
        assertTrue("the join button should ask, not announce", action.wantsAnswer)

        runDiningAction(
            action = action,
            session = session,
            play = { assetName, _ ->
                played += assetName
                asset(assetName).forEach { client.apply(it.replace("__turn__", "t1")) }
            },
            say = { said += it },
        )

        // Both halves went over the wire, in order, paired by the one id that makes them a pair.
        assertEquals(2, wire.size)
        val question = Json.parseToJsonElement(wire[0]).jsonObject
            .getValue("callAgentFunction").jsonObject
        val reply = Json.parseToJsonElement(wire[1]).jsonObject
            .getValue("agentFunctionResponse").jsonObject
        assertEquals("v1.0", (Json.parseToJsonElement(wire[0]).jsonObject["version"] as JsonPrimitive).content)
        assertEquals(
            "the answer must carry the question's id",
            question["functionCallId"],
            reply["functionCallId"],
        )
        assertEquals(
            "join_queue",
            (question.getValue("callFunction").jsonObject["call"] as JsonPrimitive).content,
        )
        // Resolved before it left: a path never travels.
        assertEquals(
            "4",
            (question.getValue("callFunction").jsonObject
                .getValue("args").jsonObject["party"] as JsonPrimitive).content,
        )

        // And the ticket the house issued is the one on screen.
        val issued = (reply.getValue("value").jsonObject["ticket"] as JsonPrimitive).content
        assertEquals(issued, surface.text("/waitlist/ticket"))
        assertEquals(listOf("dining_waitlist_done.jsonl"), played)
        assertTrue(said.single().contains(issued))

        scope.cancel()
    }

    @Test
    fun `the house refuses a function it does not have, so nothing hangs`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = ChatSession("s1", DiningCatalogSchema, scope)
        val client = session.client
        scope.launch {
            client.outbound.collect { line -> DiningHouse.answer(line)?.let(client::apply) }
        }

        val call = buildJsonObject { put("call", "cancel_everyones_booking") }
        val result = client.callAgentFunction("t1", call)

        // Refused before it ever reached the house: the catalog is the allowlist.
        assertTrue(result.isFailure)
        assertTrue(client.errors.single().reason.contains("cancel_everyones_booking"))
        scope.cancel()
    }

    /**
     * The spec's ComponentsList is `minItems: 1`, so `"components": []` is not "no components
     * yet" — it is an invalid message. The key is optional; an empty one is not.
     */
    @Test
    fun `the opening message never carries an empty components array`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = ChatSession("s1", DiningCatalogSchema, scope)
        val sent = mutableListOf<String>()

        A2uiToolCall.apply(
            args = buildJsonObject {
                put("surfaceId", "t1")
                put("catalogId", "app.dining.catalog/v1")
                putJsonArray(A2uiToolCall.COMPONENTS_ARG) {
                    add(JsonPrimitive("""{"id":"root","component":"Card","children":[]}"""))
                }
            },
            pinnedSurfaceId = "t1",
            openedSurfaces = mutableSetOf(),
            applyUi = { session.client.apply(it); null },
            emitUi = { sent += it },
        )

        val create = Json.parseToJsonElement(sent.first()).jsonObject
            .getValue("createSurface").jsonObject
        assertEquals("t1", (create["surfaceId"] as JsonPrimitive).content)
        assertNull("an empty components array is schema-invalid", create["components"])
        // And the tree still arrives, one component at a time, right behind it.
        assertTrue(sent.drop(1).all { "updateComponents" in it })
        scope.cancel()
    }
}
