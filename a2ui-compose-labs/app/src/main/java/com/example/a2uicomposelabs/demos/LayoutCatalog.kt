package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.model.A2uiComponentDefinition
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.model.A2uiAnySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiArraySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiObjectSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiSchemaKeyword
import com.example.a2uicomposelabs.a2ui.model.A2uiStringSchema
import com.example.a2uicomposelabs.a2ui.ui.LocalA2uiItemScope
import com.example.a2uicomposelabs.a2ui.model.componentSchema
import com.example.a2uicomposelabs.a2ui.model.dynamicNumber
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A grid, because "show me the same thing differently" needs somewhere to go.
 *
 * The Basic Catalog has Row, Column and a virtualised List, and that covers most answers. It has
 * no grid — so if an agent should be able to lay four numbers out two-by-two, the app has to say
 * so. Adding it costs one schema entry and one factory, which is the entire argument for the
 * catalog being yours: the vocabulary grows when you decide it should.
 *
 * It repeats over an array exactly like `List` does, using the same per-item scope, so relative
 * paths inside the template resolve per cell.
 */
internal val LayoutCatalogSchema: List<A2uiComponentDefinition> = listOf(
    A2uiComponentDefinition(
        name = "Grid",
        description =
            "Lays its children out in a fixed number of columns, filling row by row. Use it when " +
                "several small items should be seen at once rather than scrolled through — four " +
                "or six stat tiles, a set of options. Children may be a list of ids or a repeat " +
                "template {\"componentId\", \"path\"} like List takes.",
        propertySchema = componentSchema(
            properties = mapOf(
                "columns" to dynamicNumber("How many columns. Two or three read best on a phone."),
                "children" to A2uiAnySchema(
                    description = "Either an array of component IDs, or a repeat template.",
                    keywords = listOf(
                        A2uiSchemaKeyword.OneOf(
                            listOf(
                                A2uiArraySchema(items = A2uiStringSchema()),
                                A2uiObjectSchema(
                                    properties = mapOf(
                                        "componentId" to A2uiStringSchema("Component to repeat."),
                                        "path" to A2uiStringSchema("Pointer to the array."),
                                    ),
                                    required = setOf("componentId", "path"),
                                    isAdditionalPropertiesAllowed = false,
                                ),
                            )
                        )
                    ),
                ),
            ),
        ),
    ),
)

internal val LayoutCatalog: Map<String, A2uiComponentFactory> = mapOf(
    "Grid" to { node, scope, renderChild ->
        val columns = scope.readFloat(node.props["columns"], 2f).toInt().coerceIn(1, 4)
        val template = node.props["children"] as? JsonObject
        val templateId = (template?.get("componentId") as? JsonPrimitive)?.contentOrNull
        val basePath = (template?.get("path") as? JsonPrimitive)?.contentOrNull?.let(scope::resolve)

        // Either form: a repeat template over an array, or a plain list of ids.
        val cells: List<@Composable () -> Unit> =
            if (templateId != null && basePath != null) {
                List(scope.arraySize(basePath)) { index ->
                    {
                        val itemScope = remember(scope, basePath, index) {
                            scope.forItem(basePath, index)
                        }
                        CompositionLocalProvider(LocalA2uiItemScope provides itemScope) {
                            renderChild(templateId)
                        }
                    }
                }
            } else {
                scope.children(node.props).map { id -> { renderChild(id) } }
            }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cells.chunked(columns).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCells.forEach { cell ->
                        Column(Modifier.weight(1f)) { cell() }
                    }
                    // Keep the last row's columns the same width as every other row's.
                    repeat(columns - rowCells.size) { Column(Modifier.weight(1f)) {} }
                }
            }
        }
    },
)
