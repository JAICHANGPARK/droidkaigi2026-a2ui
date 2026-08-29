package com.example.a2uicomposelabs.a2ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Renderer → agent message: "the user did something".
 *
 * v1.0 gives a component's `action` two forms, and this carries both. An `event` is the old
 * one: a name and some context, sent and forgotten — there is no id on it and no way to answer
 * it, because `actionResponse` was deleted from the spec. A `functionCall` is the new one: the
 * button is asking a question, and [functionCall] holds it with its arguments already resolved.
 *
 * The host decides what to do with a question, because answering it takes a coroutine and a
 * transport, and a renderer has no business owning either. See `A2uiClient.callAgentFunction`.
 */
data class A2uiAction(
    val name: String,
    val surfaceId: String,
    val sourceComponentId: String,
    val timestamp: String,
    val context: JsonObject,
    /** Non-null when the button asked rather than announced. Arguments already resolved. */
    val functionCall: JsonObject? = null,
) {
    /** True when this action expects an answer, which only a `functionCall` ever does. */
    val wantsAnswer: Boolean get() = functionCall != null

    /**
     * The `action` message, for the form that is one. A [functionCall] leaves as
     * `callAgentFunction` with an id the client assigns, so it is not reproducible from here —
     * print [functionCall] itself instead.
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("version", "v1.0")
        put("action", buildJsonObject {
            put("name", name)
            put("surfaceId", surfaceId)
            put("sourceComponentId", sourceComponentId)
            put("timestamp", timestamp)
            put("context", context)
        })
    }
}
