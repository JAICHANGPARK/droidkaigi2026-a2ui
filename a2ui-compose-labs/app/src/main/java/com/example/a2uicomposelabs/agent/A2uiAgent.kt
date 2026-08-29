package com.example.a2uicomposelabs.agent

import kotlinx.coroutines.flow.Flow

/**
 * What the demos need from a model, and nothing more.
 *
 * The renderer never learns which provider drew the screen — it only ever sees a string going
 * into `A2uiClient.apply`. That is the point worth making on stage: swapping Gemini for Claude
 * or GPT changes one file in this package and not one line of Compose.
 */
interface A2uiAgent {

    /** False when no key was configured — the demo then falls back to the recorded stream. */
    val isConfigured: Boolean

    /**
     * What the last turn's tool calls got wrong, in the model's own terms.
     *
     * A demo that falls back to a recording owes the room an explanation. This is it.
     */
    val lastFailures: List<String>

    /** Why the last turn ended, as the provider reported it. Null when it ended normally. */
    val lastFinishReason: String?

    /** Streams the model's raw output as it is generated — plain text, nothing interpreted. */
    fun stream(systemPrompt: String, userPrompt: String): Flow<String>

    /**
     * Streams a turn where the model may also build UI, and keeps the tool loop turning.
     *
     * @param surfaceId the surface this turn must build, when the caller has one in mind. Given,
     *   it wins over whatever the model puts in the tool call: a screen the app cannot find is a
     *   screen the user never sees.
     * @param applyUi renders one message and returns the reason it was refused, or null. That
     *   reason goes straight back to the model, so a rejected screen gets rewritten instead of
     *   quietly becoming a recording.
     */
    fun streamUi(
        systemPrompt: String,
        userPrompt: String,
        surfaceId: String? = null,
        applyUi: suspend (json: String) -> String?,
    ): Flow<AgentChunk>
}

/** Which model API a turn goes to. */
enum class AgentProvider(val label: String, val defaultModel: String, val keyHint: String) {
    /**
     * The default, and the one every demo in this repo was rehearsed against. Its transport is
     * the only one here that streams prose token by token.
     */
    GEMINI("Gemini", "gemini-2.5-flash", "AIza…"),
    OPENAI("OpenAI", "gpt-5", "sk-…"),
    ANTHROPIC("Claude", "claude-opus-5", "sk-ant-…");

    companion object {
        fun from(name: String?): AgentProvider =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GEMINI
    }
}
