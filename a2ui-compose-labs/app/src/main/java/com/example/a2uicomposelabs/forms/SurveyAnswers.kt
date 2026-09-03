package com.example.a2uicomposelabs.forms

import com.example.a2uicomposelabs.a2ui.model.ComponentNode
import com.example.a2uicomposelabs.a2ui.runtime.SurfaceState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** One question and whatever the respondent put into it. */
data class AnsweredQuestion(
    val question: String,
    val required: Boolean,
    val answer: String,
) {
    val answered: Boolean get() = answer.isNotBlank()
}

/**
 * Reads the filled-in form back out of the surface.
 *
 * The agent decided what the questions were, so the app cannot have a data class for them.
 * It walks the components instead: every `Question` in root's order, paired with the value
 * its answer control is bound to. This is also what makes required-field validation possible
 * without the app knowing anything about this particular survey.
 */
fun SurfaceState.collectAnswers(): List<AnsweredQuestion> {
    val root = components["root"] ?: return emptyList()
    return root.childIds().mapNotNull { id ->
        val node = components[id]?.takeIf { it.component == "Question" } ?: return@mapNotNull null
        AnsweredQuestion(
            question = resolveString(node.props["text"]),
            required = resolveBoolean(node.props["required"]),
            answer = node.childIds()
                .mapNotNull { childId -> components[childId]?.let(::answerOf) }
                .filter(String::isNotBlank)
                .joinToString(", "),
        )
    }
}

/** The value an answer control is bound to, rendered for a human to read. */
private fun SurfaceState.answerOf(node: ComponentNode): String {
    val bound = ANSWER_PROPS.firstNotNullOfOrNull { key -> node.props[key]?.let(::pathOf) }
        ?: return ""
    return when (val value = read(bound)) {
        null -> ""
        is JsonArray -> value.mapNotNull { it.asContent() }
            .map { stored -> node.optionLabel(stored) ?: stored }
            .joinToString(", ")
        is JsonPrimitive -> when {
            value.booleanOrNull != null -> if (value.booleanOrNull == true) "Yes" else ""
            !value.isString -> value.doubleOrNull
                // Ratings and sliders arrive as numbers; 0 means the question is untouched.
                ?.takeIf { it != 0.0 }
                ?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                .orEmpty()
            else -> node.optionLabel(value.content) ?: value.content
        }
        else -> value.toString()
    }
}

/** ChoicePicker stores option values; the respondent read the labels. */
private fun ComponentNode.optionLabel(storedValue: String): String? =
    (props["options"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { (it["value"] as? JsonPrimitive)?.contentOrNull == storedValue }
        ?.let { (it["label"] as? JsonPrimitive)?.contentOrNull }

private fun ComponentNode.childIds(): List<String> =
    (props["children"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: emptyList()

private fun pathOf(prop: JsonElement): String? =
    ((prop as? JsonObject)?.get("path") as? JsonPrimitive)?.contentOrNull

private fun JsonElement.asContent(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun SurfaceState.resolveString(prop: JsonElement?): String = when (prop) {
    is JsonPrimitive -> prop.contentOrNull.orEmpty()
    is JsonObject -> pathOf(prop)?.let { read(it) }?.asContent().orEmpty()
    else -> ""
}

private fun SurfaceState.resolveBoolean(prop: JsonElement?): Boolean = when (prop) {
    is JsonPrimitive -> prop.booleanOrNull ?: false
    is JsonObject -> pathOf(prop)?.let { read(it) }
        ?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
    else -> false
}

/** The two-way properties across the catalog; whichever one a control uses is its answer. */
private val ANSWER_PROPS = listOf("value", "text")
