package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import com.example.a2uicomposelabs.demos.DiningCatalogSchema
import com.example.a2uicomposelabs.demos.DiningHouse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A kitchen's delivery range is the clearest thing on the order screen that the renderer cannot
 * work out for itself, which makes it the honest use for v1.0's `callAgentFunction`.
 *
 * These drive the real house through the real client, so the wire is exercised rather than the
 * Kotlin behind it.
 */
class DiningDeliveryAreaTest {

    private fun call(address: String) = buildJsonObject {
        put("call", "check_delivery_area")
        putJsonObject("args") { put("address", address) }
    }

    private fun houseFor(client: A2uiClient, scope: CoroutineScope) {
        scope.launch {
            client.outbound.collect { line -> DiningHouse.answer(line)?.let(client::apply) }
        }
    }

    @Test
    fun `an address the couriers reach comes back with a time`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = A2uiClient(DiningCatalogSchema)
        houseFor(client, scope)

        val area = client.callAgentFunction("t1", call("Shibuya 2-21-1, Tokyo"))
            .getOrThrow().jsonObject

        assertEquals("true", (area["deliverable"] as JsonPrimitive).content)
        assertTrue(
            "a reachable address needs an eta",
            (area["etaMinutes"] as JsonPrimitive).content.toInt() in 25..45,
        )
        scope.cancel()
    }

    @Test
    fun `an address outside the range is refused, and says where they do go`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = A2uiClient(DiningCatalogSchema)
        houseFor(client, scope)

        val area = client.callAgentFunction("t1", call("Yokohama 1-1, Kanagawa"))
            .getOrThrow().jsonObject

        assertEquals("false", (area["deliverable"] as JsonPrimitive).content)
        assertEquals("0", (area["etaMinutes"] as JsonPrimitive).content)
        assertTrue(
            "the refusal should name the wards it does cover",
            (area["note"] as JsonPrimitive).content.contains("Shibuya"),
        )
        scope.cancel()
    }

    /** The renderer must not be able to answer this one itself. */
    @Test
    fun `check_delivery_area is the agent's to run`() {
        val definition = DiningCatalogSchema.functions
            .single { it.definition.name == "check_delivery_area" }
            .definition
        assertTrue("the agent runs it", definition.allowedCallers.agentRuns)
        assertFalse("the renderer must not", definition.allowedCallers.rendererRuns)
    }

    @Test
    fun `the question goes out as v1_0 callAgentFunction`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val client = A2uiClient(DiningCatalogSchema)
        val sent = mutableListOf<String>()
        scope.launch {
            client.outbound.collect { line ->
                sent += line
                DiningHouse.answer(line)?.let(client::apply)
            }
        }

        client.callAgentFunction("t1", call("Minato 1-1, Tokyo")).getOrThrow()

        val body = Json.parseToJsonElement(sent.first()).jsonObject
            .getValue("callAgentFunction").jsonObject
        assertEquals("t1", (body["surfaceId"] as JsonPrimitive).content)
        assertEquals(
            "check_delivery_area",
            (body.getValue("callFunction").jsonObject["call"] as JsonPrimitive).content,
        )
        scope.cancel()
    }
}
