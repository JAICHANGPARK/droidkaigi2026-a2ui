package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


// The one component both androidx screens need.
//
// A booking form and a survey have almost nothing in common, but both have to take a line of
// text, and material3-a2ui still has no TextField. So it lives here rather than in either
// screen's file, and neither screen owns it.
//
// It implements androidx.a2ui.compose.ui.A2uiComponent, the same interface every component in
// A2uiBasicCatalogV1 ultimately implements. Nothing here reaches around the library.

/**
 * A single-line text input.
 *
 * `androidx.a2ui` has no notion of validation: `A2uiCheckableSchema` exists in a2ui-model, but
 * no component reads it and no runtime evaluates it. So this component invents two properties,
 * `valid` and `error`, and lets the agent aim them at a function call:
 *
 * ```json
 * "valid": {"call": "required", "args": {"value": {"path": "/resv/name"}}},
 * "error": "Enter a name"
 * ```
 *
 * The engine already evaluates that call tree against the catalog's functions, so validation
 * works — it is just not standardised. Spec v1.0 standardises it as `checks`.
 */
object TextFieldComponent : A2uiComponent {
    private val textProp = A2uiProperty.dynamicString("text", required = true,
        description = "The current text. Bind it to a data path to make the field editable.")
    private val labelProp = A2uiProperty.dynamicString("label",
        description = "The label shown above the field.")
    private val placeholderProp = A2uiProperty.dynamicString("placeholder",
        description = "Hint text shown while the field is empty.")
    private val validProp = A2uiProperty.dynamicBoolean("valid",
        description = "False marks the field invalid. Usually a call to required, regex or email.")
    private val errorProp = A2uiProperty.dynamicString("error",
        description = "The message to show while 'valid' is false.")

    override val name = "TextField"
    override val description = "A single-line text input with optional validation."
    override val properties: List<A2uiProperty<*>> =
        listOf(textProp, labelProp, placeholderProp, validProp, errorProp)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(textProp) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val text = properties.bind(textProp).orEmpty()
        val label = properties.bind(labelProp)
        val placeholder = properties.bind(placeholderProp)
        // Null means the agent bound a literal, not a path: there is nowhere to write back to,
        // so the field degrades to read-only rather than lying about being editable.
        val onTextChange = properties.bindUpdater(textProp)

        // Only complain once the user has actually typed something, the way a real form does.
        var touched by remember { mutableStateOf(false) }
        val valid = properties.bind(validProp) ?: true
        val error = properties.bind(errorProp)
        val showError = touched && !valid && error != null

        Column(modifier) {
            OutlinedTextField(
                value = text,
                onValueChange = { touched = true; onTextChange?.invoke(it) },
                enabled = onTextChange != null,
                isError = showError,
                singleLine = true,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (showError) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
        }
    }
}
