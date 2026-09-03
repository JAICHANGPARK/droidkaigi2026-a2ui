package com.example.a2uicomposelabs.a2ui.engine

import com.example.a2uicomposelabs.a2ui.model.A2uiExecutionContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The little expression language inside `formatString`.
 *
 * A template is ordinary text with `${...}` holes in it. A hole is either a JSON Pointer into
 * the data model, or a call to a catalog function with named arguments:
 *
 * ```
 * "Hi ${/user/name}, your total is ${formatCurrency(value:${/total}, currency:'JPY')}"
 * ```
 *
 * Write `\${` for a literal dollar-brace. Anything that fails to parse or resolve becomes an
 * empty string rather than an error: a broken label should not take the screen with it.
 *
 * This is deliberately not a general expression evaluator — there is no arithmetic, no
 * comparison, and no way to name anything the catalog did not already publish.
 */
internal object A2uiStringTemplate {

    fun render(
        template: String,
        context: A2uiExecutionContext,
        evaluator: A2uiDynamicEvaluator,
    ): String = buildString {
        var index = 0
        while (index < template.length) {
            val open = template.indexOf(OPEN, index)
            if (open < 0) {
                append(template, index, template.length)
                break
            }
            // A backslash immediately before "${" escapes it; the backslash itself is dropped.
            if (open > 0 && template[open - 1] == '\\') {
                append(template, index, open - 1)
                append(OPEN)
                index = open + OPEN.length
                continue
            }
            append(template, index, open)
            val close = matchingBrace(template, open + OPEN.length)
            if (close < 0) {
                // Unbalanced: treat the rest as literal text rather than guessing.
                append(template, open, template.length)
                break
            }
            val expression = template.substring(open + OPEN.length, close)
            append(renderValue(evaluate(expression, context, evaluator)))
            index = close + 1
        }
    }

    /** Finds the `}` that closes the hole opened at [from], skipping nested holes and quotes. */
    private fun matchingBrace(text: String, from: Int): Int {
        var depth = 1
        var index = from
        var quote: Char? = null
        while (index < text.length) {
            val c = text[index]
            when {
                quote != null -> if (c == quote) quote = null
                c == '\'' || c == '"' -> quote = c
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }

    /** A hole holds either `name(args)` or a data-model path. */
    private fun evaluate(
        expression: String,
        context: A2uiExecutionContext,
        evaluator: A2uiDynamicEvaluator,
    ): JsonElement? {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return null
        val open = trimmed.indexOf('(')
        if (open > 0 && trimmed.endsWith(")")) {
            val name = trimmed.substring(0, open).trim()
            if (name.isValidIdentifier()) {
                val call = buildJsonObject {
                    put("call", name)
                    put("args", parseArgs(trimmed.substring(open + 1, trimmed.length - 1)))
                }
                return evaluator.evaluate(call, context)
            }
        }
        return context.resolve(trimmed)
    }

    /** `value:${/total}, currency:'JPY'` → a JSON object of unevaluated argument payloads. */
    private fun parseArgs(source: String): JsonObject = buildJsonObject {
        for (part in splitTopLevel(source)) {
            val colon = part.indexOf(':')
            if (colon <= 0) continue
            val name = part.substring(0, colon).trim()
            if (!name.isValidIdentifier()) continue
            put(name, parseValue(part.substring(colon + 1).trim()))
        }
    }

    /** Splits on commas that are not inside a hole or a quoted string. */
    private fun splitTopLevel(source: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (c in source) {
            when {
                quote != null -> {
                    if (c == quote) quote = null
                    current.append(c)
                }
                c == '\'' || c == '"' -> { quote = c; current.append(c) }
                c == '{' -> { depth++; current.append(c) }
                c == '}' -> { depth--; current.append(c) }
                c == ',' && depth == 0 -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        if (current.isNotBlank()) parts += current.toString()
        return parts.map(String::trim).filter(String::isNotEmpty)
    }

    /** An argument is a nested hole, a quoted string, a number, a boolean, or a path. */
    private fun parseValue(raw: String): JsonElement = when {
        raw.startsWith(OPEN) && raw.endsWith("}") ->
            // Hand the inner expression back as a payload the evaluator understands.
            parseHole(raw.substring(OPEN.length, raw.length - 1).trim())
        raw.length >= 2 && (raw.first() == '\'' || raw.first() == '"') && raw.last() == raw.first() ->
            JsonPrimitive(raw.substring(1, raw.length - 1))
        raw == "true" -> JsonPrimitive(true)
        raw == "false" -> JsonPrimitive(false)
        raw.toDoubleOrNull() != null -> JsonPrimitive(raw.toDouble())
        else -> buildJsonObject { put("path", raw) }
    }

    private fun parseHole(expression: String): JsonElement {
        val open = expression.indexOf('(')
        if (open > 0 && expression.endsWith(")")) {
            val name = expression.substring(0, open).trim()
            if (name.isValidIdentifier()) {
                return buildJsonObject {
                    put("call", name)
                    put("args", parseArgs(expression.substring(open + 1, expression.length - 1)))
                }
            }
        }
        return buildJsonObject { put("path", expression) }
    }

    private fun renderValue(value: JsonElement?): String = when (value) {
        null, is JsonNull -> ""
        is JsonPrimitive ->
            if (value.isString) value.content
            // Whole numbers read better without the .0 a Double round-trip leaves behind.
            else value.asNumber?.takeIf { it % 1.0 == 0.0 }?.toLong()?.toString() ?: value.content
        else -> value.toString()
    }

    private fun String.isValidIdentifier(): Boolean =
        isNotEmpty() && first().isLetter() && all { it.isLetterOrDigit() || it == '_' }

    private const val OPEN = "\${"
}
