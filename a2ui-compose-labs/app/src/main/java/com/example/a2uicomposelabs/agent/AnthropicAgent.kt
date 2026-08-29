package com.example.a2uicomposelabs.agent

import android.util.Log
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

/**
 * The same A2UI turn, over the Anthropic Messages API.
 *
 * As with [OpenAiAgent], only the envelope is different. The tool, the component strings and
 * the assembly in [A2uiToolCall] are shared, so what reaches the renderer is byte-identical in
 * shape to what Gemini produces.
 *
 * Two things here are Anthropic-specific and worth knowing:
 *
 * - The assistant turn is echoed back **verbatim**, including any `thinking` blocks. Stripping
 *   them and keeping only the text breaks the next request on thinking-enabled models.
 * - A turn can come back with `stop_reason: "refusal"` and HTTP 200. That is not an error to
 *   throw on; it is an answer, and the demo says so rather than pretending the network failed.
 */
class AnthropicAgent(
    private val apiKey: String,
    private val model: String = AgentProvider.ANTHROPIC.defaultModel,
) : A2uiAgent {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override var lastFailures: List<String> = emptyList()
        private set

    override var lastFinishReason: String? = null
        private set

    override fun stream(systemPrompt: String, userPrompt: String): Flow<String> = flow {
        require(isConfigured) { "No Anthropic API key configured" }
        lastFinishReason = null
        val response = post(requestBody(systemPrompt, JsonArray(listOf(userTurn(userPrompt))), withTool = false))
        lastFinishReason = response["stop_reason"]?.jsonPrimitive?.contentOrNull
        refusalOf(response)?.let { emit(it); return@flow }
        textOf(response).takeIf(String::isNotEmpty)?.let { emit(it) }
    }.flowOn(Dispatchers.IO)

    override fun streamUi(
        systemPrompt: String,
        userPrompt: String,
        surfaceId: String?,
        applyUi: suspend (json: String) -> String?,
    ): Flow<AgentChunk> = flow {
        require(isConfigured) { "No Anthropic API key configured" }
        lastFinishReason = null
        lastFailures = emptyList()

        val messages = mutableListOf<JsonElement>(userTurn(userPrompt))
        val openedSurfaces = mutableSetOf<String>()
        var drawn = 0
        var nudged = false

        repeat(A2uiToolCall.MAX_TOOL_ROUNDS) {
            currentCoroutineContext().ensureActive()
            val response = post(requestBody(systemPrompt, JsonArray(messages), withTool = true))
            lastFinishReason = response["stop_reason"]?.jsonPrimitive?.contentOrNull

            // A refusal arrives as HTTP 200. Say it and stop; retrying will not change it.
            refusalOf(response)?.let { emit(AgentChunk.Prose(it)); return@flow }

            val content = response["content"]?.jsonArray.orEmpty()
            textOf(response).takeIf(String::isNotEmpty)?.let { emit(AgentChunk.Prose(it)) }

            val calls = content.mapNotNull { it as? JsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
                .filter { it["name"]?.jsonPrimitive?.contentOrNull == A2uiToolCall.NAME }

            if (calls.isEmpty()) {
                Log.d(TAG, "round ended with no tool call (stop_reason=$lastFinishReason)")
                if (drawn > 0 || nudged) return@flow
                nudged = true
                messages += userTurn(A2uiToolCall.nudge())
                return@repeat
            }

            // Echoed whole: thinking blocks included, or the next request is rejected.
            messages += buildJsonObject {
                put("role", "assistant")
                put("content", JsonArray(content))
            }

            val results = mutableListOf<JsonElement>()
            for (call in calls) {
                // Unlike OpenAI, `input` is already an object — no second parse.
                val args = call["input"]?.jsonObject ?: JsonObject(emptyMap())

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
                    failures.forEach { failure ->
                        Log.d(TAG, "failed[${failure.index}] ${failure.reason}" +
                            (failure.sent?.let { "\n  sent: $it" } ?: ""))
                    }
                }
                Log.d(
                    TAG,
                    "tool call: surface=${A2uiToolCall.surfaceIdOf(args)} " +
                        "sent=${A2uiToolCall.componentsOf(args).size} rendered=$rendered " +
                        "failed=${failures.map(A2uiToolCall.Failure::index)}",
                )
                results += buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", call["id"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("content", A2uiToolCall.result(failures, rendered).toString())
                }
            }
            // Every tool_result for one assistant turn goes back in a single user message.
            messages += buildJsonObject {
                put("role", "user")
                put("content", JsonArray(results))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** The refusal explanation, or null when the turn was answered normally. */
    private fun refusalOf(response: JsonObject): String? {
        if (response["stop_reason"]?.jsonPrimitive?.contentOrNull != "refusal") return null
        val details = response["stop_details"] as? JsonObject
        val category = details?.get("category")?.jsonPrimitive?.contentOrNull
        val explanation = details?.get("explanation")?.jsonPrimitive?.contentOrNull
        return "The model declined this request" +
            (category?.let { " ($it)" } ?: "") +
            (explanation?.let { ": $it" } ?: ".")
    }

    private fun textOf(response: JsonObject): String =
        response["content"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")

    private fun userTurn(text: String): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", text)
    }

    private fun requestBody(
        systemPrompt: String,
        messages: JsonArray,
        withTool: Boolean,
    ): ByteArray = buildJsonObject {
        put("model", model)
        // Non-streaming, so keep this under the SDK-recommended ceiling for a single response.
        put("max_tokens", 16_000)
        put("system", systemPrompt)
        put("messages", messages)
        if (withTool) {
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        put("name", A2uiToolCall.NAME)
                        put("description", A2uiToolCall.DESCRIPTION)
                        put("input_schema", A2uiToolCall.parameterSchema(upperCaseTypes = false))
                    }
                )
            }
        }
        if (SERVER_SIDE_FALLBACK) {
            // Routes a refusal to a fallback model instead of returning one. Requires the beta
            // header below; if an account rejects it with a 400, set the flag to false.
            put("fallbacks", "default")
        }
        // No `thinking`: on the current Opus models omitting it runs adaptive thinking, which
        // is what we want, and older models simply run without it.
    }.toString().toByteArray()

    private fun post(body: ByteArray): JsonObject {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", API_VERSION)
            if (SERVER_SIDE_FALLBACK) setRequestProperty("anthropic-beta", FALLBACK_BETA)
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 180_000
        }
        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                throw AnthropicException(explain(connection.responseCode, detail))
            }
            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            return Json.parseToJsonElement(text).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun explain(code: Int, detail: String?): String {
        val apiMessage = detail
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
            ?.let { (it as? JsonObject)?.get("error") as? JsonObject }
            ?.get("message")?.jsonPrimitive?.contentOrNull
        val hint = when (code) {
            400 -> "Check the request. If it names the fallback beta, set SERVER_SIDE_FALLBACK " +
                "to false in AnthropicAgent.kt."
            401 -> "The key is rejected. Check the Anthropic key in Settings."
            404 -> "Model '$model' was not found. Pick one from Settings."
            429 -> "Rate limited. Wait a moment and retry."
            else -> "See the response for details."
        }
        return "Anthropic HTTP $code — ${apiMessage ?: "no message"}. $hint"
    }

    companion object {
        private const val TAG = "AnthropicAgent"
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"

        /**
         * Ask the server to route a refusal to a fallback model rather than return one.
         *
         * On by default because a live demo that answers "I can't help with that" in front of a
         * room is worse than a slightly slower answer. Turn it off if an account rejects the
         * beta header with a 400.
         */
        private const val SERVER_SIDE_FALLBACK = true
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Asks the API which models this key may call. */
        suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "No API key" }
            val connection = (
                URL("https://api.anthropic.com/v1/models?limit=100")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", API_VERSION)
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw AnthropicException(
                        "Anthropic HTTP ${connection.responseCode} — could not list models"
                    )
                }
                val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                Json.parseToJsonElement(body)
                    .jsonObject["data"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    .sorted()
            } finally {
                connection.disconnect()
            }
        }
    }
}

class AnthropicException(message: String) : Exception(message)
