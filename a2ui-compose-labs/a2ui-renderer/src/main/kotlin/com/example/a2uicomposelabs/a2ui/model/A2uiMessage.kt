package com.example.a2uicomposelabs.a2ui.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One component definition from the flat adjacency list. */
data class ComponentNode(
    val id: String,
    val component: String,
    val props: JsonObject,
)

/** Agent → renderer messages. One JSONL line = one message. */
sealed interface A2uiMessage {

    data class CreateSurface(
        val surfaceId: String,
        val catalogId: String?,
        val components: List<ComponentNode>,
        val dataModel: JsonObject?,
    ) : A2uiMessage

    data class UpdateComponents(
        val surfaceId: String,
        val components: List<ComponentNode>,
    ) : A2uiMessage

    data class UpdateDataModel(
        val surfaceId: String,
        val path: String,
        val value: JsonElement?,
    ) : A2uiMessage

    data class DeleteSurface(val surfaceId: String) : A2uiMessage

    /**
     * The agent asking the renderer to run one of its own catalog functions.
     *
     * Note what is *not* here: a surfaceId. The spec's `callRendererFunction` carries only the
     * call and an id to answer with, so this runs outside any surface — which is why a `path`
     * in its arguments resolves to nothing. Send literals.
     */
    data class CallRendererFunction(
        val functionCallId: String,
        val call: JsonObject,
    ) : A2uiMessage

    /**
     * The agent answering a `callAgentFunction` the renderer sent.
     *
     * Exactly one of [value] and [error] is set — the spec requires one and forbids both.
     */
    data class AgentFunctionResponse(
        val functionCallId: String,
        val value: JsonElement?,
        val error: JsonObject?,
    ) : A2uiMessage

    companion object {

        /** Parses one JSONL line. Returns null for blank or unsupported lines. */
        fun parse(line: String): A2uiMessage? {
            if (line.isBlank()) return null
            return parse(Json.parseToJsonElement(line).jsonObject)
        }

        /**
         * Same, for a payload that has already been decoded.
         *
         * Every rejection here is phrased for the agent, not for a stack trace. The reason
         * string travels back to the model as the result of its tool call, so "expected an
         * object with surfaceId and components" is worth writing out — it is the difference
         * between a model that fixes its next attempt and one that repeats the same mistake.
         */
        fun parse(obj: JsonObject): A2uiMessage? {

            obj["createSurface"]?.let { body ->
                val fields = body.body("createSurface")
                return CreateSurface(
                    surfaceId = fields.requireSurfaceId("createSurface"),
                    catalogId = fields.string("catalogId"),
                    components = fields["components"].toNodes(),
                    dataModel = fields["dataModel"] as? JsonObject,
                )
            }
            obj["updateComponents"]?.let { body ->
                val fields = body.body("updateComponents")
                return UpdateComponents(
                    surfaceId = fields.requireSurfaceId("updateComponents"),
                    components = fields["components"].toNodes(),
                )
            }
            obj["updateDataModel"]?.let { body ->
                val fields = body.body("updateDataModel")
                return UpdateDataModel(
                    surfaceId = fields.requireSurfaceId("updateDataModel"),
                    path = fields.string("path") ?: "",
                    value = fields["value"],
                )
            }
            obj["deleteSurface"]?.let { body ->
                return DeleteSurface(body.body("deleteSurface").requireSurfaceId("deleteSurface"))
            }
            obj["callRendererFunction"]?.let { body ->
                val fields = body.body("callRendererFunction")
                return CallRendererFunction(
                    functionCallId = fields.requireCallId("callRendererFunction"),
                    call = fields["callFunction"] as? JsonObject
                        ?: throw IllegalArgumentException(
                            "\"callRendererFunction\" is missing \"callFunction\" — write " +
                                "{\"callFunction\":{\"call\":\"...\",\"catalogId\":\"...\"}}"
                        ),
                )
            }
            obj["agentFunctionResponse"]?.let { body ->
                val fields = body.body("agentFunctionResponse")
                val error = fields["error"] as? JsonObject
                val value = fields["value"]
                if (error == null && value == null) {
                    throw IllegalArgumentException(
                        "\"agentFunctionResponse\" needs exactly one of \"value\" or \"error\""
                    )
                }
                return AgentFunctionResponse(
                    functionCallId = fields.requireCallId("agentFunctionResponse"),
                    value = value,
                    error = error,
                )
            }
            throw IllegalArgumentException(
                "no A2UI message here — expected one of createSurface, updateComponents, " +
                    "updateDataModel, deleteSurface, callRendererFunction or " +
                    "agentFunctionResponse at the top level, found " +
                    "${obj.keys.joinToString(", ")}"
            )
        }

        /** The body of a message is always an object; a bare array is the common slip. */
        private fun JsonElement.body(name: String): JsonObject = this as? JsonObject
            ?: throw IllegalArgumentException(
                "\"$name\" must be an object with \"surfaceId\" and its fields, not a " +
                    "bare ${if (this is JsonArray) "array" else "value"} — write " +
                    "{\"$name\":{\"surfaceId\":\"...\",\"components\":[...]}}"
            )

        private fun JsonObject.requireSurfaceId(name: String): String = string("surfaceId")
            ?: throw IllegalArgumentException("\"$name\" is missing \"surfaceId\"")

        private fun JsonObject.requireCallId(name: String): String = string("functionCallId")
            ?: throw IllegalArgumentException(
                "\"$name\" is missing \"functionCallId\" — it is what pairs a call with its answer"
            )

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonElement?.toNodes(): List<ComponentNode> =
            (this as? JsonArray)?.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val type = (o["component"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                ComponentNode(id, type, JsonObject(o.filterKeys { it != "id" && it != "component" }))
            } ?: emptyList()
    }
}
