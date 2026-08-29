package com.example.a2uicomposelabs.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Cleans up the JSON a model hands over, before anything else sees it.
 *
 * This is a port of `payload_fixer.py` from the official A2UI Python agent SDK, and it is worth
 * saying why it exists at all: an LLM writes JSON one token at a time, so a few well-known
 * blemishes turn up no matter how good the prompt is. The SDK fixes exactly three, and so does
 * this — no more, because guessing further would mean inventing UI.
 *
 * It lives on the **agent side**, which is the point. The renderer stays strict: it is the
 * security boundary, and a boundary that quietly patches what it receives is not a boundary.
 * Anything this cannot fix goes back to the model as an error so it can write it again.
 */
internal object A2uiPayloadFixer {

    class FixFailed(message: String) : Exception(message)

    /**
     * Parses [payload] into the list of A2UI messages it holds, applying the SDK's autofixes.
     *
     * A single message is wrapped into a list, matching the SDK: the tool is documented as
     * accepting either one message or several.
     */
    fun parseAndFix(payload: String): List<JsonObject> {
        val normalized = normalizeSmartQuotes(payload)
        return try {
            parse(normalized)
        } catch (first: Exception) {
            // Second attempt, exactly as the SDK does: the most common remaining blemish is a
            // comma before a closing bracket, which JSON does not allow but models write anyway.
            try {
                parse(removeTrailingCommas(normalized))
            } catch (second: Exception) {
                throw FixFailed(second.message ?: "could not parse the payload")
            }
        }
    }

    private fun parse(payload: String): List<JsonObject> =
        when (val element = Json.parseToJsonElement(payload)) {
            is JsonArray -> element.map { it.asMessage() }
            is JsonObject -> listOf(element)
            else -> throw FixFailed("expected an object or an array of them")
        }

    private fun JsonElement.asMessage(): JsonObject =
        this as? JsonObject ?: throw FixFailed("expected a message object in the batch")

    /** Models reach for typographic quotes surprisingly often; JSON only accepts straight ones. */
    private fun normalizeSmartQuotes(json: String): String =
        json.replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')

    /** Drops a comma that sits right before `]` or `}`, outside of strings. */
    private fun removeTrailingCommas(json: String): String {
        val out = StringBuilder(json.length)
        var inString = false
        var escaped = false
        var pendingComma = -1

        for (c in json) {
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
            }
            if (!inString && pendingComma >= 0) {
                if (c.isWhitespace()) {
                    out.append(c)
                    continue
                }
                // Keep the comma only if something other than a closer follows it.
                if (c != ']' && c != '}') out.insert(pendingComma, ',')
                pendingComma = -1
            }
            if (!inString && c == ',') {
                pendingComma = out.length
                continue
            }
            out.append(c)
        }
        if (pendingComma >= 0) out.insert(pendingComma, ',')
        return out.toString()
    }
}
