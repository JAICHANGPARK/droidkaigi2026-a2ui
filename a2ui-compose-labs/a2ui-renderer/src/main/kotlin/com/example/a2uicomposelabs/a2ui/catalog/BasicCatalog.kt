package com.example.a2uicomposelabs.a2ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.runtime.A2uiCheckSeverity
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.ui.LocalA2uiItemScope
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The A2UI Basic Catalog (spec v1.0, all 18 components), mapped to Material 3
 * composables. The ten core components live here; the remaining eight
 * (Image, Icon, Tabs, Modal, ChoicePicker, DateTimeInput, AudioPlayer, Video)
 * are in [ExtraCatalog] and merged below.
 * The agent picks WHAT to show; MaterialTheme decides HOW it looks.
 */
val BasicCatalog: Map<String, A2uiComponentFactory> get() = coreCatalog + ExtraCatalog

private val coreCatalog: Map<String, A2uiComponentFactory> = mapOf(

    "Text" to { node, scope, _ ->
        Text(
            text = scope.readString(node.props["text"]),
            style = MaterialTheme.typography.bodyLarge,
        )
    },

    "Column" to { node, scope, renderChild ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            scope.children(node.props).forEach { renderChild(it) }
        }
    },

    "Row" to { node, scope, renderChild ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scope.children(node.props).forEach { renderChild(it) }
        }
    },

    "Card" to { node, scope, renderChild ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scope.children(node.props).forEach { renderChild(it) }
            }
        }
    },

    // children: fixed ID array, or {"componentId","path"} template over a data-model list
    // (relative paths inside the template resolve per item; see BindingScope.forItem)
    "List" to { node, scope, renderChild ->
        val horizontal = scope.readString(node.props["direction"]) == "horizontal"
        val template = node.props["children"] as? JsonObject
        val templateId = (template?.get("componentId") as? JsonPrimitive)?.contentOrNull
        val basePath = (template?.get("path") as? JsonPrimitive)?.contentOrNull?.let(scope::resolve)
        if (templateId != null && basePath != null) {
            val count = scope.arraySize(basePath)
            val itemContent: @Composable (Int) -> Unit = { i ->
                // Stable per-item scope: a fresh instance every recomposition would
                // invalidate every reader in the item subtree.
                val itemScope = remember(scope, basePath, i) { scope.forItem(basePath, i) }
                CompositionLocalProvider(LocalA2uiItemScope provides itemScope) {
                    renderChild(templateId)
                }
            }
            if (horizontal) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count) { i -> itemContent(i) }
                }
            } else {
                // Virtualized; bounded height so it stays safe inside unbounded parents.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(count) { i -> itemContent(i) }
                }
            }
        } else if (horizontal) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scope.children(node.props)) { renderChild(it) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                scope.children(node.props).forEach { renderChild(it) }
            }
        }
    },

    "Divider" to { _, _, _ -> HorizontalDivider() },

    // Spec: a failing check disables the buttons on its surface. The agent never says
    // "disable the button" — it says what a valid answer is, and this falls out of that.
    "Button" to { node, scope, _ ->
        Button(
            onClick = { scope.dispatchAction(node) },
            enabled = scope.surfaceIsSubmittable(),
        ) {
            Text(scope.readString(node.props["label"]))
        }
    },

    "TextField" to { node, scope, _ ->
        // The label is optional. Inside a survey the question is already asked above the
        // field, and a floating label there would only repeat it, then vanish on first
        // keystroke — taking the question with it.
        val label = scope.readString(node.props["label"])
        // The field says what is wrong with it; the button says the form cannot be sent yet.
        val failures = scope.checkFailures(node)
        OutlinedTextField(
            value = scope.readString(node.props["text"]),
            onValueChange = { scope.write(node.props["text"], JsonPrimitive(it)) },
            label = if (label.isEmpty()) null else ({ Text(label) }),
            isError = failures.any { it.severity == A2uiCheckSeverity.ERROR },
            supportingText = failures.firstOrNull()?.let { failure -> { Text(failure.message) } },
            modifier = Modifier.fillMaxWidth(),
        )
    },

    "CheckBox" to { node, scope, _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = scope.readBoolean(node.props["value"]),
                onCheckedChange = { scope.write(node.props["value"], JsonPrimitive(it)) },
            )
            Text(scope.readString(node.props["label"]))
        }
    },

    "Slider" to { node, scope, _ ->
        val min = scope.readFloat(node.props["min"], 0f)
        // Agent-controlled values: never build an empty range (min > max crashes coerceIn).
        val max = maxOf(min, scope.readFloat(node.props["max"], 1f))
        val current = scope.readFloat(node.props["value"], min).coerceIn(min, max)

        // A whole-number range is nearly always a count — guests, portions, nights. Snapping to
        // integers keeps "2.4713 guests" out of the data model and out of the action payload,
        // and it is the renderer's call to make: the catalog has no `steps` to ask for.
        val counting = min % 1f == 0f && max % 1f == 0f && (max - min) >= 1f
        val readout =
            if (counting) current.toInt().toString()
            else "%.2f".format(current).trimEnd('0').trimEnd('.')

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = scope.readString(node.props["label"])
                if (label.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
                // Without this the user drags blind. A slider is the one basic input whose
                // state is invisible unless the renderer says it out loud.
                Text(
                    readout,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = current,
                onValueChange = { picked ->
                    scope.write(
                        node.props["value"],
                        if (counting) JsonPrimitive(picked.roundToInt()) else JsonPrimitive(picked),
                    )
                },
                valueRange = min..max,
                // Compose counts the stops *between* the ends, so a 1..8 range wants 6.
                steps = if (counting) (max - min).toInt() - 1 else 0,
            )
        }
    },
)
