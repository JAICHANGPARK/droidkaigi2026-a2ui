package com.example.a2uicomposelabs.a2ui.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The four messages that travel from the renderer back to the agent, as text.
 *
 * v1.0 is the first version where this direction is more than a shrug. Up to v0.9.1 the only
 * thing the renderer could say was "the user pressed something" — `action` — and it could not
 * even hear an answer, because `actionResponse` was deleted along with `wantResponse` and
 * `responsePath` on 2026-08-12. What replaced it is a pair of proper RPCs with an id on each
 * side, which is the difference between shouting and asking.
 *
 * Written out by hand rather than serialized from a class, for the same reason the rest of this
 * renderer is: the shape on the wire is the thing being demonstrated, and it should be readable
 * in the file that produces it.
 */
object A2uiRpc {

    const val VERSION: String = "v1.0"

    /**
     * Renderer → agent: run this, and copy [functionCallId] into your answer.
     *
     * The surface is required here and absent from `callRendererFunction`, which is not an
     * oversight: a renderer-initiated call comes from a screen with a data model behind it, and
     * an agent-initiated one comes from nowhere in particular.
     */
    fun callAgentFunction(surfaceId: String, functionCallId: String, call: JsonObject): String =
        buildJsonObject {
            put("version", VERSION)
            putJsonObject("callAgentFunction") {
                put("surfaceId", surfaceId)
                put("functionCallId", functionCallId)
                put("callFunction", call)
            }
        }.toString()

    /** Renderer → agent: the answer to a `callRendererFunction`. */
    fun rendererFunctionResponse(functionCallId: String, value: JsonElement): String =
        buildJsonObject {
            put("version", VERSION)
            putJsonObject("rendererFunctionResponse") {
                put("functionCallId", functionCallId)
                put("value", value)
            }
        }.toString()

    /**
     * Renderer → agent: that call did not work, and here is a code the agent can branch on.
     *
     * A refusal is still an answer. The agent is holding a [functionCallId] and will hold it
     * forever unless something comes back, so a renderer that silently drops a call it does not
     * like has hung the conversation rather than protected it.
     */
    fun rendererFunctionError(functionCallId: String, code: String, message: String): String =
        buildJsonObject {
            put("version", VERSION)
            putJsonObject("rendererFunctionResponse") {
                put("functionCallId", functionCallId)
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            }
        }.toString()

    /** The code the spec reserves for a call the renderer will not make or run. */
    const val INVALID_FUNCTION_CALL: String = "INVALID_FUNCTION_CALL"
}
