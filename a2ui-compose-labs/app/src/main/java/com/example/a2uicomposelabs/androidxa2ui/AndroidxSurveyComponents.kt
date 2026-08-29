package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


// What a survey needs and material3-a2ui does not have.
//
// Three of them now, and they are different in kind. StarRating and ChoicePicker are controls:
// they take an answer. Question is a container: it holds the controls that answer it, which is
// the more interesting one to write, because a child can be named by the agent before it has
// been sent.
//
// Two screens read this file — the dining survey and the support CSAT form — and neither gets
// all three. AndroidxSurveyCatalog lists Question and StarRating; AndroidxSupportCatalog lists
// all three. The file is where a component lives; the catalog is what an agent may draw.

/** A row of stars. The survey's rating questions bind to it. */
object StarRatingComponent : A2uiComponent {
    private val valueProp = A2uiProperty.dynamicNumber("value", required = true,
        description = "How many stars are filled.")
    private val maxProp = A2uiProperty.number("max", description = "How many stars, default 5.")

    override val name = "StarRating"
    override val description = "A row of stars for rating something."
    override val properties: List<A2uiProperty<*>> = listOf(valueProp, maxProp)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(valueProp) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val value = properties.bind(valueProp)?.toInt() ?: return
        val onValueChange = properties.bindUpdater(valueProp)
        val max = properties[maxProp]?.toInt() ?: 5

        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 1..max) {
                IconButton(
                    onClick = { onValueChange?.invoke(star) },
                    enabled = onValueChange != null,
                ) {
                    Icon(
                        imageVector = if (star <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "$star",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

/** A survey question: prompt on top, its answer components underneath. */
object QuestionComponent : A2uiComponent {
    private val textProp = A2uiProperty.dynamicString("text", required = true,
        description = "The question being asked.")
    private val requiredProp = A2uiProperty.boolean("required",
        description = "Marks the question with an asterisk.")
    private val childrenProp = A2uiProperty.childList("children", required = true,
        description = "The components that answer this question, referenced by id.")

    override val name = "Question"
    override val description = "A survey question and the controls that answer it."
    override val properties: List<A2uiProperty<*>> = listOf(textProp, requiredProp, childrenProp)

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val text = properties.bind(textProp).orEmpty()
        val isRequired = properties[requiredProp] ?: false
        val children = properties.bindChildReferences(childrenProp)

        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.titleSmall)
                if (isRequired) {
                    Text(" *", color = MaterialTheme.colorScheme.error)
                }
            }
            children?.forEach { reference ->
                key(reference.id, reference.baseDataPath) { AnswerItem(reference) }
            }
        }
    }
}

@Composable
private fun A2uiComponentScope.AnswerItem(reference: A2uiComponentReference) {
    when (val state = observeA2uiComponentState(reference)) {
        // The engine hands out Loading for a child the agent has named but not sent yet: the
        // form draws itself in the order the messages arrive, not all at once at the end.
        is A2uiComponentState.Loading -> Text("…", style = MaterialTheme.typography.bodySmall)
        is A2uiComponentState.Error ->
            Text(
                state.exception.message ?: "error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        is A2uiComponentState.Success -> A2uiComponent(component = state.component)
    }
}

/**
 * A set of alternatives: radio buttons for one answer, checkboxes for several.
 *
 * The spec has this in the basic catalog and this app's own renderer ships it. material3-a2ui
 * does not, and a satisfaction survey without it is stuck asking every question as a star
 * rating or a sentence — which is exactly how you get a form nobody finishes.
 *
 * Which of the two it draws is not a mode the agent sets, it is which property the agent bound:
 *
 * ```json
 * {"id":"a3","component":"ChoicePicker","value":{"path":"/answers/condition"},"options":[…]}
 * {"id":"a4","component":"ChoicePicker","values":{"path":"/answers/wanted"},"options":[…]}
 * ```
 *
 * One property to bind means the agent cannot contradict itself by asking for a single choice
 * and binding an array.
 */
object ChoicePickerComponent : A2uiComponent {
    // The option's own two properties. They are nested inside `options`, so their keys are free
    // to collide with the component's own — this `value` is what one option stores, and the
    // `value` below is where the answer goes.
    private val optionLabelProp = A2uiProperty.dynamicString("label", required = true,
        description = "The option as the respondent reads it.")
    private val optionValueProp = A2uiProperty.string("value", required = true,
        description = "What choosing this option writes into the data model.")
    private val optionsProp = A2uiProperty.nestedList("options",
        properties = listOf(optionLabelProp, optionValueProp),
        required = true,
        minItems = 2,
        description = "The alternatives, in the order they should be read.")

    private val valueProp = A2uiProperty.dynamicString("value",
        description =
            "Single choice. The path holding the chosen option's value; \"\" means unanswered. " +
                "Bind this OR values, never both.")
    private val valuesProp = A2uiProperty.dynamicStringList("values",
        description =
            "Multiple choice. The path holding the chosen values as an array; [] means " +
                "unanswered. Bind this OR value, never both.")

    override val name = "ChoicePicker"
    override val description =
        "A list of options. Draws radio buttons when 'value' is bound and checkboxes when " +
            "'values' is."
    override val properties: List<A2uiProperty<*>> = listOf(optionsProp, valueProp, valuesProp)

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val options = properties[optionsProp].orEmpty()
        val chosen = properties.bind(valuesProp)
        val onChosenChange = properties.bindUpdater(valuesProp)
        val picked = properties.bind(valueProp)
        val onPickedChange = properties.bindUpdater(valueProp)
        // Which control to draw is decided by what the agent bound, not by a flag it could get
        // wrong. `values` present at all — even as an empty array — means multiple choice.
        val multiple = chosen != null || onChosenChange != null

        Column(modifier.fillMaxWidth()) {
            options.forEach { option ->
                val label = option.bind(optionLabelProp)
                val value = option[optionValueProp]
                if (label != null && value != null) {
                    key(value) {
                        if (multiple) {
                            val ticked = chosen?.contains(value) == true
                            OptionRow(
                                selected = ticked,
                                label = label,
                                enabled = onChosenChange != null,
                                onSelect = {
                                    val now = chosen.orEmpty()
                                    onChosenChange?.invoke(
                                        if (ticked) now - value else now + value
                                    )
                                },
                            ) { Checkbox(checked = ticked, onCheckedChange = null) }
                        } else {
                            OptionRow(
                                selected = picked == value,
                                label = label,
                                enabled = onPickedChange != null,
                                onSelect = { onPickedChange?.invoke(value) },
                            ) { RadioButton(selected = picked == value, onClick = null) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One option, with the whole row as the target.
 *
 * The control itself takes no callback: the row owns the click and carries the semantics, so a
 * screen reader announces the label and the state together instead of an unlabelled checkbox
 * next to some text.
 */
@Composable
private fun OptionRow(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onSelect: () -> Unit,
    control: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = selected, enabled = enabled, onValueChange = { onSelect() })
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        control()
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
