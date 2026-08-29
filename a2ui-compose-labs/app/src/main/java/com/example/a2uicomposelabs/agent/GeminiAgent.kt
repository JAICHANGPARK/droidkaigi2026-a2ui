package com.example.a2uicomposelabs.agent

import android.util.Log
import com.example.a2uicomposelabs.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The other half of A2UI: the agent. Streams text out of Gemini over the REST API's
 * server-sent-events endpoint, so tokens arrive as they are produced.
 *
 * This deliberately uses nothing but `HttpURLConnection` and kotlinx-serialization — no HTTP
 * client dependency to download, so the stage build still works fully offline against a warm
 * Gradle cache.
 *
 * The API key comes from `local.properties` (see the app's build.gradle.kts). Shipping a key
 * inside an APK is fine for a conference demo and wrong for production: anyone can pull it
 * back out of the binary.
 */
/** One piece of a model turn: something to say, or a screen to draw. */
sealed interface AgentChunk {
    data class Prose(val text: String) : AgentChunk
    /** One A2UI message, already parsed and repaired by [A2uiPayloadFixer]. */
    data class Ui(val json: String) : AgentChunk
}

class GeminiAgent(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = DEFAULT_MODEL,
) : A2uiAgent {

    /** False when no key was configured — the demo then falls back to the recorded stream. */
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * Streams the model's raw output as it is generated — plain text, nothing interpreted.
     *
     * UI never comes back this way. For that, see [streamUi].
     */
    override fun stream(systemPrompt: String, userPrompt: String): Flow<String> = flow {
        require(isConfigured) { "No GEMINI_API_KEY configured" }
        lastFinishReason = null

        val connection = (URL(endpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // Header, not a query parameter: keys in URLs leak into logs and proxies.
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 120_000
        }

        try {
            connection.outputStream.use { it.write(requestBody(systemPrompt, JsonArray(listOf(userTurn(userPrompt))))) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                throw GeminiException(explain(connection.responseCode, detail))
            }

            connection.inputStream.bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    currentCoroutineContext().ensureActive()
                    if (!line.startsWith(SSE_DATA_PREFIX)) continue // keep-alives and blanks
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    finishReasonOf(payload)?.let { lastFinishReason = it }
                    textOf(payload)?.let { emit(it) }
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams a turn where the model may also build UI, and keeps the tool loop turning.
     *
     * The UI does not come back as text. It comes back as an argument to a tool call, the way
     * the official A2UI agent SDK does it with `send_a2ui_json_to_client`. That one choice
     * removes a whole class of failure: the API delivers the argument as a complete value, so
     * prose and JSON can no longer be spliced together, and no closing tag can go missing.
     *
     * A tool call is a round trip. The model calls, we answer, and only then does it carry on —
     * which is also where [applyUi] earns its place. It renders one message and returns the
     * reason it was refused, or null. That reason goes straight back to the model, so a
     * rejected screen gets rewritten instead of quietly becoming a recording.
     */
    override fun streamUi(
        systemPrompt: String,
        userPrompt: String,
        /**
         * The surface this turn must build, when the caller has one in mind.
         *
         * Given, it wins over whatever the model puts in the tool call. Surface identity is the
         * app's, for the same reason the menu is: a screen the app cannot find is a screen the
         * user never sees, and the model inventing an id is not a failure worth showing them.
         */
        surfaceId: String?,
        applyUi: suspend (json: String) -> String?,
    ): Flow<AgentChunk> = flow {
        require(isConfigured) { "No GEMINI_API_KEY configured" }
        lastFinishReason = null
        lastFailures = emptyList()

        val contents = mutableListOf<JsonElement>(userTurn(userPrompt))
        // A surface is created once; later calls in the same turn only add to it.
        val openedSurfaces = mutableSetOf<String>()
        var drawn = 0
        var nudged = false

        repeat(A2uiToolCall.MAX_TOOL_ROUNDS) {
            val calls = mutableListOf<JsonObject>()
            val results = mutableListOf<JsonObject>()

            val connection = openConnection()
            try {
                connection.outputStream.use {
                    it.write(requestBody(systemPrompt, JsonArray(contents), withTool = true))
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val detail =
                        connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    throw GeminiException(explain(connection.responseCode, detail))
                }

                connection.inputStream.bufferedReader().use { reader ->
                    for (line in reader.lineSequence()) {
                        currentCoroutineContext().ensureActive()
                        if (!line.startsWith(SSE_DATA_PREFIX)) continue
                        val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue

                        finishReasonOf(payload)?.let { lastFinishReason = it }
                        for (part in partsOf(payload)) {
                            val obj = part as? JsonObject ?: continue

                            obj["text"]?.jsonPrimitive?.contentOrNull
                                ?.takeIf(String::isNotEmpty)
                                ?.let { emit(AgentChunk.Prose(it)) }

                            val call = obj["functionCall"] as? JsonObject ?: continue
                            if (call["name"]?.jsonPrimitive?.contentOrNull != A2UI_TOOL) continue
                            calls += obj

                            val args = call["args"] as? JsonObject ?: JsonObject(emptyMap())
                            var rendered = 0
                            val failures = A2uiToolCall.apply(
                                args = args,
                                pinnedSurfaceId = surfaceId,
                                openedSurfaces = openedSurfaces,
                                applyUi = applyUi,
                                emitUi = { rendered++; drawn++; emit(AgentChunk.Ui(it)) },
                            )
                            if (failures.isNotEmpty()) {
                                lastFailures = failures.map(A2uiToolCall::describeFor)
                                // What the model actually wrote, so a turn that never recovers
                                // can be diagnosed from logcat instead of reproduced.
                                failures.forEach { failure ->
                                    Log.d(TAG, "failed[${failure.index}] ${failure.reason}" +
                                        (failure.sent?.let { "\n  sent: $it" } ?: ""))
                                }
                            }
                            // One line per tool call, so a turn that goes wrong on stage can be
                            // read back off logcat instead of guessed at.
                            Log.d(
                                TAG,
                                "tool call: surface=${A2uiToolCall.surfaceIdOf(args)} " +
                                    "sent=${A2uiToolCall.componentsOf(args).size} rendered=$rendered " +
                                    "failed=${failures.map(A2uiToolCall.Failure::index)}",
                            )
                            results += toolResult(failures, rendered)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (calls.isEmpty()) {
                Log.d(TAG, "round ended with no tool call (finishReason=$lastFinishReason)")
                // Sometimes the model just talks: a friendly paragraph, no tool call, no screen.
                // Nothing is broken and nothing is drawn, which is the worst of both. Ask once,
                // plainly, and only once — a model that ignores a direct instruction twice is
                // not going to be argued into it.
                if (drawn > 0 || nudged) return@flow
                nudged = true
                contents += userTurn(A2uiToolCall.nudge())
                return@repeat
            }

            contents += buildJsonObject {
                put("role", "model")
                put("parts", JsonArray(calls))
            }
            contents += buildJsonObject {
                put("role", "user")
                put("parts", JsonArray(results))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Gemini's envelope around the shared tool result. */
    private fun toolResult(failures: List<A2uiToolCall.Failure>, rendered: Int): JsonObject =
        buildJsonObject {
            putJsonObject("functionResponse") {
                put("name", A2uiToolCall.NAME)
                put("response", A2uiToolCall.result(failures, rendered))
            }
        }

    private fun partsOf(payload: String): List<JsonElement> = runCatching {
        Json.parseToJsonElement(payload)
            .jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.toList()
    }.getOrNull().orEmpty()

    private fun openConnection(): HttpURLConnection =
        (URL(endpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 120_000
        }

    private fun endpoint() =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"

    private fun requestBody(
        systemPrompt: String,
        contents: JsonArray,
        withTool: Boolean = false,
    ): ByteArray =
        buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemPrompt) }) }
            }
            put("contents", contents)
            if (withTool) {
                // Mirrors the official SDK's tool: one string argument carrying the A2UI JSON.
                putJsonArray("tools") {
                    add(
                        buildJsonObject {
                            putJsonArray("functionDeclarations") {
                                add(
                                    buildJsonObject {
                                        put("name", A2UI_TOOL)
                                        put("description", A2uiToolCall.DESCRIPTION)
                                        // The model never writes the envelope, and never
                                        // writes the brackets around the component list —
                                        // see A2uiToolCall for why that one choice removed a
                                        // whole class of failure.
                                        put(
                                            "parameters",
                                            A2uiToolCall.parameterSchema(upperCaseTypes = true),
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
            putJsonObject("generationConfig") {
                // Low temperature: we want schema-shaped output, not creative JSON.
                put("temperature", 0.2)
                // A catalog-sized system prompt plus a screen's worth of messages runs long.
                put("maxOutputTokens", 32_768)
            }
        }.toString().toByteArray()

    private fun userTurn(text: String): JsonObject = buildJsonObject {
        put("role", "user")
        putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
    }

    /**
     * What the last turn's tool calls got wrong, in the model's own terms.
     *
     * A demo that falls back to a recording owes the room an explanation. This is it — and
     * saying it out loud is also what stopped me guessing at the cause for two days.
     */
    override var lastFailures: List<String> = emptyList()
        private set

    /**
     * Why the last stream ended, as the API reported it.
     *
     * `MAX_TOKENS` means the answer was cut off in flight, which looks exactly like a malformed
     * message by the time it reaches the renderer. Worth being able to tell the two apart.
     */
    override var lastFinishReason: String? = null
        private set

    private fun finishReasonOf(payload: String): String? = runCatching {
        Json.parseToJsonElement(payload)
            .jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Pulls the text delta out of one SSE frame, ignoring frames that carry no text. */
    private fun textOf(payload: String): String? = runCatching {
        Json.parseToJsonElement(payload)
            .jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            ?.takeIf(String::isNotEmpty)
    }.getOrNull()

    private fun explain(code: Int, detail: String?): String {
        val apiMessage = detail
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
            ?.let { (it as? JsonObject)?.get("error") as? JsonObject }
            ?.get("message")?.jsonPrimitive?.contentOrNull
        val hint = when (code) {
            400 -> "Check the request — often an unsupported field."
            403 -> "The key is rejected. Check GEMINI_API_KEY and that the API is enabled."
            404 -> "Model '$model' was not found. Change DEFAULT_MODEL in GeminiAgent.kt."
            429 -> "Rate limited. Wait a moment and retry."
            else -> "See the response for details."
        }
        return "Gemini HTTP $code — ${apiMessage ?: "no message"}. $hint"
    }

    companion object {
        /** Used when Settings has no model chosen. Settings can list what a key actually serves. */
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        private const val TAG = "GeminiAgent"

        private const val SSE_DATA_PREFIX = "data:"

        /** The same tool name the official A2UI agent SDK uses. Defined in [A2uiToolCall]. */
        const val A2UI_TOOL = A2uiToolCall.NAME
        const val A2UI_TOOL_ARG = A2uiToolCall.COMPONENTS_ARG

        /**
         * Asks the API which models this key may call for content generation. Saves guessing
         * at model names, which change faster than talks get rehearsed.
         */
        suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "No API key" }
            val connection = (
                URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                setRequestProperty("x-goog-api-key", apiKey)
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val detail = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    val apiMessage = detail
                        ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
                        ?.let { (it as? JsonObject)?.get("error") as? JsonObject }
                        ?.get("message")?.jsonPrimitive?.contentOrNull
                    throw GeminiException(
                        "Gemini HTTP ${connection.responseCode} — ${apiMessage ?: "could not list models"}"
                    )
                }
                val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                Json.parseToJsonElement(body)
                    .jsonObject["models"]?.jsonArray.orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { model ->
                        model["supportedGenerationMethods"]?.jsonArray
                            ?.any { it.jsonPrimitive.contentOrNull == "generateContent" } == true
                    }
                    .mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
                    .sorted()
            } finally {
                connection.disconnect()
            }
        }
    }
}

class GeminiException(message: String) : Exception(message)
