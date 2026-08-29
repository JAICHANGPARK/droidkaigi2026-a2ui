package com.example.a2uicomposelabs.androidxa2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

/** The only protocol version androidx.a2ui will read from a live agent in this app. */
const val ANDROIDX_PROTOCOL_VERSION: String = "v0.9.1"

/**
 * Rewrites one message from the shared agent tool into the messages androidx.a2ui accepts.
 *
 * Every demo in this app shares one tool ([com.example.a2uicomposelabs.agent.A2uiToolCall]):
 * the model sends components, the tool writes the envelopes around them. Those envelopes say
 * `"version":"v1.0"`, and v1.0 folds the data model and the first components into
 * `createSurface`. androidx.a2ui's parser reads the version field first and throws before it
 * looks at anything else, so that string never reaches a component.
 *
 * This is the whole adapter. It splits the opening message back into the three v0.9.1 messages
 * and stamps the older version on the rest — thirty lines, no component touched. The components
 * themselves are already right, because [csatSystemPrompt] taught the model this dialect rather
 * than the one the other nine screens use. That split is the point worth showing: what a
 * protocol version costs you is an envelope, and what it costs the agent is a prompt.
 *
 * Anything this cannot recognise is passed through untouched, so the engine gets to refuse it
 * and say why, rather than being handed something quietly invented here.
 */
fun toAndroidxDialect(line: String): List<String> {
    val message =
        runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
            ?: return listOf(line)

    val create = message["createSurface"] as? JsonObject ?: return listOf(reversioned(message))
    val surfaceId = (create["surfaceId"] as? JsonPrimitive)?.contentOrNull ?: return listOf(line)

    val messages = mutableListOf<String>()
    messages +=
        buildJsonObject {
                put("version", ANDROIDX_PROTOCOL_VERSION)
                put(
                    "createSurface",
                    buildJsonObject {
                        put("surfaceId", surfaceId)
                        create["catalogId"]?.let { put("catalogId", it) }
                        // Both v0.9.1 and v1.0 default sendDataModel to false, so it has to
                        // be asked for either way, and the events carry the surface's data
                        // model back only when it was.
                        put("sendDataModel", true)
                    },
                )
            }
            .toString()

    (create["dataModel"] as? JsonObject)?.let { model ->
        messages +=
            buildJsonObject {
                    put("version", ANDROIDX_PROTOCOL_VERSION)
                    put(
                        "updateDataModel",
                        buildJsonObject {
                            put("surfaceId", surfaceId)
                            put("path", "/")
                            put("value", model)
                        },
                    )
                }
                .toString()
    }

    // v1.0 lets the opening message carry components. The tool sends an empty list, but a
    // hand-written v1.0 line might not, and dropping them would lose the root component.
    (create["components"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { components ->
        messages +=
            buildJsonObject {
                    put("version", ANDROIDX_PROTOCOL_VERSION)
                    put(
                        "updateComponents",
                        buildJsonObject {
                            put("surfaceId", surfaceId)
                            put("components", components)
                        },
                    )
                }
                .toString()
    }

    return messages
}

/** The same message, said in the older version. Nothing else about it changes. */
private fun reversioned(message: JsonObject): String =
    buildJsonObject {
            put("version", ANDROIDX_PROTOCOL_VERSION)
            message.forEach { (key, value) -> if (key != "version") put(key, value) }
        }
        .toString()
