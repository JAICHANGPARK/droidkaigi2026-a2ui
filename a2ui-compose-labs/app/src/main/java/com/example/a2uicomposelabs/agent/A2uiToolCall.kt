package com.example.a2uicomposelabs.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Everything about the A2UI tool that has nothing to do with which model you are talking to.
 *
 * Three providers speak three different wires — Gemini's `functionCall`, OpenAI's `tool_calls`,
 * Anthropic's `tool_use` — but the moment the arguments are in hand, the work is identical:
 * take a surface id and a list of components, and build A2UI messages around them. That work
 * lives here so all three producers cannot drift apart. If they could, the demo would be
 * comparing renderers rather than models.
 *
 * The tool takes the components as an ARRAY OF STRINGS, one component each. That is the whole
 * trick. Every failure we measured was in the characters around a component — closing
 * `components` with `}`, dropping the last `}` of the message — and shortening the strings did
 * not help, because the mistake tracks nesting depth, not length. So the depth is removed: the
 * model writes one component at a time and this file assembles the messages. What reaches the
 * renderer is ordinary A2UI, validated exactly as anything off the network would be.
 */
object A2uiToolCall {

    /** The same tool name the official A2UI agent SDK uses. */
    const val NAME = "send_a2ui_json_to_client"
    const val COMPONENTS_ARG = "a2ui_json"

    /** The protocol version every assembled message carries. */
    const val PROTOCOL_VERSION = "v1.0"

    const val DESCRIPTION =
        "Sends A2UI JSON to the client to render rich UI for the user. Call it as many times " +
            "as you need. The A2UI JSON Schema is in the system instructions."

    /**
     * One component the model sent that did not make it, why, and what it actually wrote.
     *
     * Handing the text back matters. A model shown "Expected EOF at offset 218" rewrites the
     * same string again; a model shown its own line next to the complaint fixes it.
     */
    data class Failure(val index: Int, val reason: String, val sent: String? = null)

    fun describeFor(failure: Failure): String =
        if (failure.index < 0) failure.reason else "component #${failure.index}: ${failure.reason}"

    /**
     * The tool's parameter schema.
     *
     * @param upperCaseTypes Gemini's `functionDeclarations` want `"OBJECT"` / `"STRING"` /
     *   `"ARRAY"`; OpenAI and Anthropic want ordinary JSON Schema lower case. Only the casing
     *   differs, so the schema is written once and cased on the way out.
     */
    fun parameterSchema(upperCaseTypes: Boolean): JsonObject {
        fun t(name: String) = if (upperCaseTypes) name.uppercase() else name
        return buildJsonObject {
            put("type", t("object"))
            putJsonObject("properties") {
                putJsonObject("surfaceId") {
                    put("type", t("string"))
                    put("description", "The surface to build.")
                }
                putJsonObject("catalogId") {
                    put("type", t("string"))
                    put("description", "The catalog in use.")
                }
                putJsonObject("dataModel") {
                    put("type", t("string"))
                    put(
                        "description",
                        "One JSON object: the values the components bind to. Omit if there " +
                            "are none.",
                    )
                }
                putJsonObject(COMPONENTS_ARG) {
                    put("type", t("array"))
                    put(
                        "description",
                        "The components of the screen, in the order they should appear. Put " +
                            "the one with id \"root\" first.",
                    )
                    putJsonObject("items") {
                        put("type", t("string"))
                        put(
                            "description",
                            "ONE component object, and nothing else: " +
                                "{\"id\":\"...\",\"component\":\"...\", ...its properties}. " +
                                "No \"version\", no \"updateComponents\", no surrounding array.",
                        )
                    }
                }
            }
            putJsonArray("required") {
                add(kotlinx.serialization.json.JsonPrimitive("surfaceId"))
                add(kotlinx.serialization.json.JsonPrimitive(COMPONENTS_ARG))
            }
        }
    }

    /** The component strings out of a tool call, accepting a single string as well as a list. */
    fun componentsOf(args: JsonObject): List<String> {
        val arg = args[COMPONENTS_ARG] ?: return emptyList()
        val raw = when (arg) {
            is JsonArray -> arg.map(::asText)
            else -> listOf(asText(arg))
        }
        return raw.map(String::trim).filter(String::isNotEmpty)
    }

    /**
     * One component as text, however the model chose to send it.
     *
     * The schema asks for strings and most turns oblige, but a model that sends the component
     * *object* instead is wrong about the wrapper and right about the component. Taking it
     * either way costs one line; not taking it cost a whole turn, because the cast threw before
     * anything could be reported back and the screen died with a Kotlin type name on it.
     */
    private fun asText(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }

    /** A tool argument that is supposed to be a string, and is ignored when it is not. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    /** The surface a tool call names, or null when it named nothing usable. */
    fun surfaceIdOf(args: JsonObject): String? = args.string("surfaceId")

    /**
     * Turns one tool call into A2UI messages, and renders them.
     *
     * The model hands over a surface id and a list of components. Everything around them —
     * `createSurface`, each `updateComponents`, the `version` field, the brackets — is written
     * here, deterministically.
     */
    suspend fun apply(
        args: JsonObject,
        pinnedSurfaceId: String?,
        openedSurfaces: MutableSet<String>,
        applyUi: suspend (json: String) -> String?,
        emitUi: suspend (String) -> Unit,
    ): List<Failure> {
        // A retry often carries only the components that failed. Falling back to the surface
        // this turn already opened is not a guess: there is exactly one.
        val surfaceId = pinnedSurfaceId
            ?: args.string("surfaceId")
            ?: openedSurfaces.singleOrNull()
            ?: return listOf(Failure(-1, "missing required argument surfaceId"))
        val components = componentsOf(args)
        if (components.isEmpty()) {
            return listOf(Failure(-1, "missing required argument $COMPONENTS_ARG"))
        }

        val failures = mutableListOf<Failure>()

        // The shell first, so every component after it has somewhere to land.
        if (openedSurfaces.add(surfaceId)) {
            // Asked for as a string, and sent as an object about as often. Both are usable.
            val dataModel = when (val supplied = args["dataModel"]) {
                is JsonObject -> supplied
                is JsonPrimitive -> supplied.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?.let { text ->
                        runCatching { A2uiPayloadFixer.parseAndFix(text).firstOrNull() }
                            .onFailure {
                                failures += Failure(-1, "dataModel is not valid JSON: ${it.message}")
                            }
                            .getOrNull()
                    }
                else -> null
            }
            val create = buildJsonObject {
                put("version", PROTOCOL_VERSION)
                putJsonObject("createSurface") {
                    put("surfaceId", surfaceId)
                    args.string("catalogId")?.let { put("catalogId", it) }
                    // No `components` key at all. It is optional here — only surfaceId is
                    // required — and the spec's ComponentsList is `minItems: 1`, so an empty
                    // array is not "no components", it is an invalid message. The tree arrives
                    // in the updateComponents that follow, which is what createSurface's own
                    // description says to expect.
                    dataModel?.let { put("dataModel", it) }
                }
            }.toString()
            emitUi(create)
            applyUi(create)?.let { failures += Failure(-1, it) }
        }

        // Then one message per component, which is also what makes the screen grow on stage.
        components.forEachIndexed { index, text ->
            val component = try {
                A2uiPayloadFixer.parseAndFix(text).firstOrNull()
                    ?: throw IllegalArgumentException("empty component")
            } catch (e: Exception) {
                failures += Failure(index, describe(e.message), sent = text)
                return@forEachIndexed
            }
            val message = buildJsonObject {
                put("version", PROTOCOL_VERSION)
                putJsonObject("updateComponents") {
                    put("surfaceId", surfaceId)
                    put("components", JsonArray(listOf(component)))
                }
            }.toString()
            emitUi(message)
            applyUi(message)?.let { failures += Failure(index, it) }
        }
        return failures
    }

    /**
     * What goes back to the model as the tool's result, before any provider wraps it.
     *
     * Failures come back itemised, each next to the text that produced it, so the model
     * rewrites only what broke instead of the whole screen.
     */
    fun result(failures: List<Failure>, rendered: Int): JsonObject = buildJsonObject {
        put("componentsRendered", rendered)
        if (failures.isEmpty()) {
            // Saying what landed is what lets the model tell "done" from "keep going" —
            // without it, it tends to send the shell and stop.
            put("status", "rendered")
            put("note", "Call again for any component still missing from the screen.")
        } else {
            put("status", "partial")
            putJsonArray("failed") {
                failures.forEach { failure ->
                    add(
                        buildJsonObject {
                            put("index", failure.index)
                            put("error", failure.reason)
                            failure.sent?.let { put("youSent", it) }
                        }
                    )
                }
            }
            put(
                "note",
                "Compare each \"youSent\" against the complaint, then send those components " +
                    "again, each as ONE component object. Do not resend the ones that already " +
                    "rendered.",
            )
        }
    }

    /** The line to send when a round produced words but no tool call. */
    fun nudge(): String =
        "You answered in words only. Call the $NAME tool now with the components for that " +
            "answer. Do not reply again without calling it."

    /**
     * Turns a parser complaint into something a model can act on.
     *
     * Offsets and token names are for people with a debugger. What the model needs to hear is
     * which way it miscounted, because that is the only mistake it actually makes here.
     */
    fun describe(message: String?): String {
        val detail = message?.substringBefore('\n').orEmpty()
        val hint = when {
            "EOF" in detail ->
                "the object closed too early — you wrote one `}` too many. Count the closers " +
                    "against the openers before you send it."
            "Expected end of the input" in detail || "Unexpected end" in detail ->
                "the object never closed — you are one `}` short at the end."
            else -> "check that every `{` and `[` has its own closer, in the right order."
        }
        return "not valid JSON: $hint ($detail)"
    }

    /** How many rounds one turn may take, shared so every provider behaves the same. */
    const val MAX_TOOL_ROUNDS = 5
}
