package com.example.a2uicomposelabs.a2ui

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * A message the renderer refused, and why. [detail] carries the offending line so a failed
 * generation can be copied out of the app and read.
 */
data class A2uiRejection(val reason: String, val detail: String? = null) {
    /** Reads as the reason wherever a rejection is printed. */
    override fun toString(): String = reason
}

/**
 * Renderer-side message processor.
 * Parses, validates, and only then applies each incoming line. Malformed JSON, unknown
 * components, and properties that do not match the catalog schema are all rejected here —
 * they never reach the UI.
 *
 * Pass a [catalog] to turn on schema validation (the same catalog that described the
 * components to the agent). Without one the client still parses and applies, which is what
 * the earlier demos used before the catalog existed.
 */
class A2uiClient(private val catalog: A2uiCatalog? = null) {

    val surfaces = mutableStateMapOf<String, SurfaceState>()
    val errors = mutableStateListOf<A2uiRejection>()

    private val validator = A2uiSchemaValidator(catalog)

    /**
     * Everything the renderer says back, as raw JSON lines: `callAgentFunction` when a screen
     * needs something only the agent knows, `rendererFunctionResponse` when the agent asked for
     * something only the renderer can run.
     *
     * A flow rather than a callback because the host owns the transport. This renderer has no
     * opinion about whether the other end is an HTTP call, a WebSocket, or — as in this app —
     * an object in the same process. Nothing is replayed: a subscriber that arrives late has
     * missed the call, and pretending otherwise would mean answering questions nobody asked.
     */
    private val sent = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val outbound: SharedFlow<String> = sent.asSharedFlow()

    /** Calls waiting on an answer, by the id the agent has to copy back. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Result<JsonElement>>>()
    private val callCounter = AtomicLong()

    /**
     * Asks the agent to run a function, and waits for its answer.
     *
     * This is the half of v1.0 that v0.9.1 has no equivalent for. Until now the only thing a
     * screen could do about a value it did not have was let the agent push one; now it can ask,
     * and the `functionCallId` is what makes the answer belong to this question rather than to
     * whichever one came back first.
     *
     * Use it for a value that is settled once — a booking reference, a ticket number. A value
     * that keeps changing on its own is the wrong shape for a question, because you would have
     * to keep asking; let the agent push `updateDataModel` for those.
     *
     * Returns a failure — never throws, and never waits forever — when the catalog does not
     * publish the function, when the agent answers with an error, or when nothing comes back
     * inside [timeoutMillis].
     */
    suspend fun callAgentFunction(
        surfaceId: String,
        call: JsonObject,
        timeoutMillis: Long = DEFAULT_CALL_TIMEOUT_MILLIS,
    ): Result<JsonElement> {
        val name = call.callName()
            ?: return failedCall("callFunction has no \"call\" name")
        val definition = catalog?.functions?.firstOrNull { it.definition.name == name }?.definition
            ?: return failedCall("'$name' is not a function this catalog publishes")
        if (!definition.allowedCallers.agentRuns) {
            return failedCall(
                "'$name' is declared ${definition.allowedCallers.value}, so the agent does not " +
                    "run it — evaluate it here instead"
            )
        }

        val functionCallId = "fc-${callCounter.incrementAndGet()}"
        val waiting = CompletableDeferred<Result<JsonElement>>()
        pending[functionCallId] = waiting
        emit(A2uiRpc.callAgentFunction(surfaceId, functionCallId, call))

        val answer = withTimeoutOrNull(timeoutMillis) { waiting.await() }
        pending.remove(functionCallId)
        return answer ?: failedCall("the agent did not answer '$name' within ${timeoutMillis}ms")
    }

    private fun failedCall(why: String): Result<JsonElement> {
        recordError("callAgentFunction refused: $why")
        return Result.failure(A2uiFunctionException(why))
    }

    private fun emit(line: String) {
        if (!sent.tryEmit(line)) recordError("outbound buffer full, message dropped", line)
    }

    /**
     * Applies one A2UI payload: a message, or an array of them.
     *
     * Nothing here repairs anything. The renderer is the security boundary, and a boundary that
     * quietly patches what it receives is not a boundary — it is a second, undocumented parser
     * that the agent can never be tested against. Broken JSON is refused and named, and the
     * caller hands that reason back to the agent so the message gets written again properly.
     */
    fun apply(document: String) {
        if (document.isBlank()) return
        val element = try {
            kotlinx.serialization.json.Json.parseToJsonElement(document)
        } catch (e: Exception) {
            Log.w(TAG, "unparseable message rejected: $document", e)
            recordError("malformed JSON: ${e.message}", document)
            return
        }
        when (element) {
            // Agents batch messages into one array often enough to be worth accepting.
            is JsonArray -> element.forEach { entry ->
                (entry as? JsonObject)?.let(::applyMessage)
                    ?: recordError("expected a message object in the batch", document)
            }
            is JsonObject -> applyMessage(element)
            else -> recordError("expected a JSON object", document)
        }
    }

    private fun applyMessage(obj: JsonObject) {
        val message = try {
            A2uiMessage.parse(obj)
        } catch (e: Exception) {
            Log.w(TAG, "unparseable message rejected: $obj", e)
            recordError("malformed message: ${e.message}", obj.toString())
            return
        } ?: return

        when (message) {
            is A2uiMessage.CreateSurface -> {
                val surface = SurfaceState(message.surfaceId)
                message.dataModel?.let(surface::setDataModel)
                if (!surface.putComponents(accept(message.components))) {
                    recordError("component cap exceeded: ${message.surfaceId}")
                }
                surfaces[message.surfaceId] = surface
            }
            is A2uiMessage.UpdateComponents ->
                if (surfaces[message.surfaceId]?.putComponents(accept(message.components)) == false) {
                    recordError("component cap exceeded: ${message.surfaceId}")
                }
            is A2uiMessage.UpdateDataModel ->
                surfaces[message.surfaceId]?.updateData(message.path, message.value)
            is A2uiMessage.DeleteSurface ->
                surfaces.remove(message.surfaceId)

            is A2uiMessage.AgentFunctionResponse -> {
                val waiting = pending.remove(message.functionCallId)
                if (waiting == null) {
                    // Either the call already timed out or the agent made the id up. Both are
                    // worth naming: an answer to nothing is how a hung screen starts.
                    recordError(
                        "agentFunctionResponse for '${message.functionCallId}', which nothing " +
                            "is waiting on",
                        obj.toString(),
                    )
                } else if (message.error != null) {
                    waiting.complete(Result.failure(A2uiFunctionException(message.error.toString())))
                } else {
                    waiting.complete(Result.success(message.value ?: JsonNull))
                }
            }

            is A2uiMessage.CallRendererFunction -> runForAgent(message)
        }
    }

    /**
     * Runs one of this renderer's own functions because the agent asked, and answers.
     *
     * Every exit answers. The agent is holding a `functionCallId` and will hold it until
     * something comes back, so refusing quietly would hang the conversation rather than protect
     * it — the refusal is the protection, and it has to be sent.
     *
     * The arguments are evaluated against a throwaway surface because `callRendererFunction`
     * names no surface. A `{"path"}` in there resolves to nothing, which is correct: there is
     * no data model to point into.
     */
    private fun runForAgent(message: A2uiMessage.CallRendererFunction) {
        fun refuse(why: String) {
            recordError("callRendererFunction refused: $why", message.call.toString())
            emit(
                A2uiRpc.rendererFunctionError(
                    message.functionCallId,
                    A2uiRpc.INVALID_FUNCTION_CALL,
                    why,
                )
            )
        }

        val catalog = catalog ?: return refuse("this renderer has no catalog to run against")
        val name = message.call.callName() ?: return refuse("callFunction has no \"call\" name")
        val function = catalog.functions.firstOrNull { it.definition.name == name }
            ?: return refuse("'$name' is not a function this catalog publishes")
        if (!function.definition.allowedCallers.rendererRuns) {
            return refuse(
                "'$name' is declared ${function.definition.allowedCallers.value} — the renderer " +
                    "does not run it"
            )
        }

        val scratch = SurfaceState(AGENT_CALL_SURFACE)
        val context = A2uiExecutionContext(scratch, null, catalog.evaluator)
        val args = (message.call["args"] as? JsonObject ?: JsonObject(emptyMap()))
            .mapValues { (_, raw) -> catalog.evaluator.evaluate(raw, context) ?: JsonNull }
        val value = try {
            function.execute(args, context) ?: JsonNull
        } catch (e: Exception) {
            return refuse("'$name' failed: ${e.message}")
        }
        emit(A2uiRpc.rendererFunctionResponse(message.functionCallId, value))
    }

    /**
     * Drops every component the catalog does not describe or whose properties do not match
     * its schema. One bad component is dropped on its own: the rest of the message still
     * renders, so a single hallucinated property cannot blank out the whole surface.
     */
    private fun accept(rawComponents: List<ComponentNode>): List<ComponentNode> {
        val catalog = catalog ?: return rawComponents

        return rawComponents.filter { node ->
            val definition = catalog.components[node.component]
            if (definition == null) {
                recordError(
                    "unknown component '${node.component}' (id=${node.id}) — skipped",
                    node.component,
                )
                return@filter false
            }
            val unknownCall = node.props.firstUnknownCall(catalog.evaluator.names)
            if (unknownCall != null) {
                recordError(
                    "unknown function '$unknownCall' (id=${node.id}) — skipped",
                    node.props.toString(),
                )
                return@filter false
            }
            try {
                validator.validate(node.props, definition.propertySchema, "/${node.id}")
                true
            } catch (e: A2uiValidationException) {
                recordError(
                    "invalid ${node.component} (id=${node.id}): ${e.message}",
                    node.props.toString(),
                )
                false
            }
        }
    }

    /**
     * The schema checks that a call is *shaped* right; this checks that it names a function
     * the app actually published. Without it an agent could invoke anything and merely get
     * null back at render time, with nothing said about why.
     */
    private fun JsonElement.firstUnknownCall(known: Set<String>): String? = when (this) {
        is JsonObject -> {
            val name = callName()
            when {
                name != null && name !in known -> name
                else -> values.firstNotNullOfOrNull { it.firstUnknownCall(known) }
            }
        }
        is JsonArray -> firstNotNullOfOrNull { it.firstUnknownCall(known) }
        else -> null
    }

    // Bounded like everything else: a spamming stream cannot grow this forever.
    private fun recordError(reason: String, detail: String? = null) {
        if (errors.size >= MAX_ERRORS) errors.removeAt(0)
        errors += A2uiRejection(reason, detail)
    }

    companion object {
        const val MAX_ERRORS = 50

        /** Long enough for a model to think, short enough that a dead agent does not hang a tap. */
        const val DEFAULT_CALL_TIMEOUT_MILLIS = 30_000L

        /** `callRendererFunction` names no surface, so its arguments resolve against this one. */
        private const val AGENT_CALL_SURFACE = "__agentCall__"

        private const val TAG = "A2uiClient"
    }
}
