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
 * The same A2UI turn, over OpenAI's chat completions API.
 *
 * Only the envelope differs from [GeminiAgent]. The tool is the same tool, the components come
 * back as the same array of strings, and [A2uiToolCall] assembles the same A2UI messages — so a
 * screen drawn here and a screen drawn by Gemini are the same screen, and the renderer cannot
 * tell which one it got.
 *
 * This one does not stream. Gemini streams because its demo shows prose arriving word by word;
 * here the whole turn arrives, then the components are applied one at a time, which is what
 * actually makes the screen grow. Nothing about A2UI needs a streaming transport.
 */
class OpenAiAgent(
    private val apiKey: String,
    private val model: String = AgentProvider.OPENAI.defaultModel,
) : A2uiAgent {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override var lastFailures: List<String> = emptyList()
        private set

    override var lastFinishReason: String? = null
        private set

    override fun stream(systemPrompt: String, userPrompt: String): Flow<String> = flow {
        require(isConfigured) { "No OpenAI API key configured" }
        lastFinishReason = null
        val body = requestBody(systemPrompt, JsonArray(listOf(userTurn(userPrompt))), withTool = false)
        val choice = post(body).firstChoice()
        lastFinishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotEmpty)
            ?.let { emit(it) }
    }.flowOn(Dispatchers.IO)

    override fun streamUi(
        systemPrompt: String,
        userPrompt: String,
        surfaceId: String?,
        applyUi: suspend (json: String) -> String?,
    ): Flow<AgentChunk> = flow {
        require(isConfigured) { "No OpenAI API key configured" }
        lastFinishReason = null
        lastFailures = emptyList()

        val messages = mutableListOf<JsonElement>(userTurn(userPrompt))
        val openedSurfaces = mutableSetOf<String>()
        var drawn = 0
        var nudged = false

        repeat(A2uiToolCall.MAX_TOOL_ROUNDS) {
            currentCoroutineContext().ensureActive()
            val choice = post(requestBody(systemPrompt, JsonArray(messages), withTool = true))
                .firstChoice()
            lastFinishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
            val assistant = choice["message"]?.jsonObject ?: JsonObject(emptyMap())

            assistant["content"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotEmpty)
                ?.let { emit(AgentChunk.Prose(it)) }

            val calls = assistant["tool_calls"]?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.functionName() == A2uiToolCall.NAME }

            if (calls.isEmpty()) {
                Log.d(TAG, "round ended with no tool call (finish_reason=$lastFinishReason)")
                // Same as Gemini: a model that answers in words only gets asked once, plainly.
                if (drawn > 0 || nudged) return@flow
                nudged = true
                messages += userTurn(A2uiToolCall.nudge())
                return@repeat
            }

            // OpenAI wants the assistant turn echoed back verbatim before its tool results.
            messages += assistant

            for (call in calls) {
                // `arguments` arrives as a JSON *string*, not an object.
                val args = runCatching {
                    Json.parseToJsonElement(
                        call["function"]?.jsonObject?.get("arguments")?.jsonPrimitive?.contentOrNull
                            .orEmpty()
                    ).jsonObject
                }.getOrElse { JsonObject(emptyMap()) }

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
                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", call["id"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("content", A2uiToolCall.result(failures, rendered).toString())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun JsonObject.functionName(): String? =
        this["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull

    private fun JsonObject.firstChoice(): JsonObject =
        this["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: JsonObject(emptyMap())

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
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
            messages.forEach { add(it) }
        })
        if (withTool) {
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", A2uiToolCall.NAME)
                            put("description", A2uiToolCall.DESCRIPTION)
                            put("parameters", A2uiToolCall.parameterSchema(upperCaseTypes = false))
                        }
                    }
                )
            }
        }
        // Deliberately no temperature and no token cap. Newer OpenAI models reject a
        // non-default temperature and renamed the cap to max_completion_tokens, and the model
        // name here is whatever the user typed in Settings — so send neither and let the
        // server's defaults apply. A catalog-sized prompt still fits.
    }.toString().toByteArray()

    private fun post(body: ByteArray): JsonObject {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 180_000
        }
        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                throw OpenAiException(explain(connection.responseCode, detail))
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
            400 -> "Check the request — often an unsupported field for this model."
            401 -> "The key is rejected. Check the OpenAI key in Settings."
            404 -> "Model '$model' was not found. Pick one from Settings."
            429 -> "Rate limited, or out of quota. Wait a moment and retry."
            else -> "See the response for details."
        }
        return "OpenAI HTTP $code — ${apiMessage ?: "no message"}. $hint"
    }

    companion object {
        private const val TAG = "OpenAiAgent"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

        /** Asks the API which models this key may call, so nobody has to guess a model name. */
        suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "No API key" }
            val connection = (
                URL("https://api.openai.com/v1/models").openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw OpenAiException("OpenAI HTTP ${connection.responseCode} — could not list models")
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

class OpenAiException(message: String) : Exception(message)
