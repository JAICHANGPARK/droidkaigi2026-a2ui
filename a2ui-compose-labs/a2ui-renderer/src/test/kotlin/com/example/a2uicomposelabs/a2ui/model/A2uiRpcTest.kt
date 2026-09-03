package com.example.a2uicomposelabs.a2ui.model

import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import com.example.a2uicomposelabs.a2ui.runtime.BindingScope
import com.example.a2uicomposelabs.a2ui.runtime.SurfaceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0's two-way half, which is the first version of A2UI where the renderer gets to ask.
 *
 * Everything here goes through real JSON in both directions. That is deliberate: the value of
 * this layer is the shape on the wire — one `functionCallId` pairing a question with its answer
 * — and a test that called the Kotlin directly would prove nothing about it.
 */
class A2uiRpcTest {

    /** A function the renderer owns and can run. */
    private object Shout : A2uiFunction {
        override val definition = A2uiFunctionDefinition(
            name = "shout",
            description = "Upper-cases a string.",
            argumentSchema = A2uiObjectSchema(
                properties = mapOf("value" to A2uiStringSchema("The text.")),
                required = setOf("value"),
            ),
            returnType = A2uiFunctionReturnType.STRING,
            allowedCallers = A2uiFunctionCaller.RENDERER_OR_AGENT,
        )

        override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
            JsonPrimitive((args["value"] as? JsonPrimitive)?.contentOrNull.orEmpty().uppercase())
    }

    /** A function only the renderer may run, so the agent must not be able to drive it. */
    private object Local : A2uiFunction {
        override val definition = A2uiFunctionDefinition(
            name = "local",
            description = "Renderer-only.",
            argumentSchema = A2uiObjectSchema(),
            returnType = A2uiFunctionReturnType.STRING,
        )

        override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
            JsonPrimitive("ran locally")
    }

    /** A function the agent owns. The renderer publishes the name and nothing else. */
    private val remote = A2uiAgentFunction(
        A2uiFunctionDefinition(
            name = "ask_house",
            description = "Something only the other end knows.",
            argumentSchema = A2uiObjectSchema(
                properties = mapOf("party" to A2uiNumberSchema("How many.")),
            ),
            returnType = A2uiFunctionReturnType.OBJECT,
            allowedCallers = A2uiFunctionCaller.AGENT_ONLY,
        )
    )

    private fun catalog() = A2uiCatalog(
        id = "test/v1",
        definitions = BasicCatalogSchema.components.values.toList(),
        functions = listOf(Shout, Local, remote),
    )

    private fun call(name: String, args: JsonObject = JsonObject(emptyMap())) = buildJsonObject {
        put("call", name)
        put("args", args)
    }

    private fun answer(functionCallId: String, value: JsonElement) = buildJsonObject {
        put("version", "v1.0")
        putJsonObject("agentFunctionResponse") {
            put("functionCallId", functionCallId)
            put("value", value)
        }
    }.toString()

    private fun String.body(key: String): JsonObject =
        Json.parseToJsonElement(this).jsonObject.getValue(key).jsonObject

    private fun String.callId(key: String): String =
        (body(key)["functionCallId"] as JsonPrimitive).content

    /**
     * The next thing the renderer says, with the listener guaranteed to be attached first.
     *
     * `outbound` replays nothing, which is the right behaviour — an answer that arrives before
     * anyone is listening is an answer to a question nobody asked — but it means a test that
     * subscribes a beat late waits forever. So wait for the subscription itself.
     */
    private suspend fun A2uiClient.nextLine(scope: CoroutineScope): Deferred<String> {
        val listening = CompletableDeferred<Unit>()
        val line = scope.async {
            outbound.onSubscription { listening.complete(Unit) }.first()
        }
        listening.await()
        return line
    }

    // -----------------------------------------------------------------------
    // Renderer asks, agent answers.
    // -----------------------------------------------------------------------

    @Test
    fun `a call goes out as v1_0 callAgentFunction and waits for its own id`() = runTest {
        val client = A2uiClient(catalog())

        val sent = client.nextLine(this)
        val call = async { client.callAgentFunction("s1", call("ask_house")) }
        val line = sent.await()

        val body = line.body("callAgentFunction")
        assertEquals("v1.0", (Json.parseToJsonElement(line).jsonObject["version"] as JsonPrimitive).content)
        assertEquals("s1", (body["surfaceId"] as JsonPrimitive).content)
        assertEquals("ask_house", (body.getValue("callFunction").jsonObject["call"] as JsonPrimitive).content)

        // An answer to a different question must not satisfy this one.
        client.apply(answer("fc-not-mine", JsonPrimitive("nope")))
        assertTrue("a stray id resolved the wrong call", call.isActive)

        client.apply(answer(line.callId("callAgentFunction"), buildJsonObject { put("ticket", "W-42") }))
        val value = call.await().getOrThrow().jsonObject
        assertEquals("W-42", (value["ticket"] as JsonPrimitive).content)
    }

    @Test
    fun `an error answer fails the call rather than hanging it`() = runTest {
        val client = A2uiClient(catalog())

        val sent = client.nextLine(this)
        val call = async { client.callAgentFunction("s1", call("ask_house")) }
        val id = sent.await().callId("callAgentFunction")

        client.apply(
            buildJsonObject {
                put("version", "v1.0")
                putJsonObject("agentFunctionResponse") {
                    put("functionCallId", id)
                    putJsonObject("error") {
                        put("code", "NO_TABLES")
                        put("message", "closed for the night")
                    }
                }
            }.toString()
        )
        assertTrue(call.await().isFailure)
    }

    @Test
    fun `the renderer will not ask for a function it runs itself`() = runTest {
        val client = A2uiClient(catalog())

        val result = client.callAgentFunction("s1", call("local"))

        assertTrue(result.isFailure)
        assertTrue(
            "the refusal should name the declaration",
            client.errors.single().reason.contains("rendererOnly"),
        )
    }

    @Test
    fun `an agent-side function evaluated in a binding yields nothing, and does not throw`() {
        val surface = SurfaceState("s1")
        val scope = BindingScope(surface, onAction = {}, evaluator = catalog().evaluator)

        assertNull(scope.read(call("ask_house")))
    }

    // -----------------------------------------------------------------------
    // Agent asks, renderer answers.
    // -----------------------------------------------------------------------

    private fun agentCalls(functionCallId: String, name: String, args: JsonObject) =
        buildJsonObject {
            put("version", "v1.0")
            putJsonObject("callRendererFunction") {
                put("functionCallId", functionCallId)
                put("callFunction", buildJsonObject {
                    put("call", name)
                    put("catalogId", "test/v1")
                    put("args", args)
                })
            }
        }.toString()

    @Test
    fun `the renderer runs what the agent asks for and answers with the same id`() = runTest {
        val client = A2uiClient(catalog())
        val reply = client.nextLine(this)

        client.apply(
            agentCalls("fc-9", "shout", buildJsonObject { put("value", "table ready") })
        )

        val body = reply.await().body("rendererFunctionResponse")
        assertEquals("fc-9", (body["functionCallId"] as JsonPrimitive).content)
        assertEquals("TABLE READY", (body["value"] as JsonPrimitive).content)
    }

    @Test
    fun `a refused call is still answered, or the agent waits forever`() = runTest {
        val client = A2uiClient(catalog())
        val reply = client.nextLine(this)

        client.apply(agentCalls("fc-7", "ask_house", JsonObject(emptyMap())))

        val body = reply.await().body("rendererFunctionResponse")
        assertEquals("fc-7", (body["functionCallId"] as JsonPrimitive).content)
        assertEquals(
            A2uiRpc.INVALID_FUNCTION_CALL,
            (body.getValue("error").jsonObject["code"] as JsonPrimitive).content,
        )
        assertNull("a refusal must not carry a value", body["value"])
    }

    @Test
    fun `a function the catalog never published is refused by name`() = runTest {
        val client = A2uiClient(catalog())
        val reply = client.nextLine(this)

        client.apply(agentCalls("fc-3", "rm_rf", JsonObject(emptyMap())))

        val body = reply.await().body("rendererFunctionResponse")
        assertTrue(
            (body.getValue("error").jsonObject["message"] as JsonPrimitive).content
                .contains("rm_rf")
        )
    }

    // -----------------------------------------------------------------------
    // The button that asks.
    // -----------------------------------------------------------------------

    @Test
    fun `a functionCall button resolves its arguments before they leave the device`() {
        val surface = SurfaceState("s1")
        surface.updateData("/waitlist/party", JsonPrimitive(4))
        var seen: A2uiAction? = null
        val scope = BindingScope(surface, onAction = { seen = it })

        scope.dispatchAction(
            ComponentNode(
                id = "join",
                component = "Button",
                props = buildJsonObject {
                    put("label", "Join")
                    putJsonObject("action") {
                        putJsonObject("functionCall") {
                            put("call", "join_queue")
                            putJsonObject("args") {
                                putJsonObject("party") { put("path", "/waitlist/party") }
                            }
                        }
                    }
                },
            )
        )

        val action = seen ?: error("nothing dispatched")
        assertTrue("a functionCall action expects an answer", action.wantsAnswer)
        assertEquals("join_queue", action.name)
        // The path is gone: what travels is the number the user actually saw.
        assertEquals(
            "4",
            (action.functionCall!!.getValue("args").jsonObject["party"] as JsonPrimitive).content,
        )
    }

    @Test
    fun `an action is one form or the other, never both and never neither`() {
        val client = A2uiClient(catalog())
        val button = { action: String ->
            """{"version":"v1.0","createSurface":{"surfaceId":"s1","components":[
               {"id":"b","component":"Button","label":"Go","action":$action}]}}"""
        }

        client.apply(button("""{"name":"go","functionCall":{"call":"shout"}}"""))
        client.apply(button("""{}"""))

        assertEquals(2, client.errors.size)
        assertTrue(client.errors.all { it.reason.contains("exactly one of") })
    }
}
