package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import com.example.a2uicomposelabs.a2ui.model.A2uiBooleanSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiComponentDefinition
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.model.componentSchema
import com.example.a2uicomposelabs.a2ui.model.dynamicString
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.hypot

/**
 * Charts, drawn with nothing but `Canvas`.
 *
 * There is no charting library here on purpose, and the reason is the same one the renderer
 * itself has for being nine small files: a component in an A2UI catalog is *your* code. A chart
 * is the most app-specific component there is — its colours, its rounding, its empty state are
 * all design-system decisions — so it is also the clearest possible demonstration that the agent
 * can only draw what the app already wrote.
 *
 * Canvas covers the shapes easily: bars are rectangles, a line is a `Path`, a pie is `drawArc`.
 * What a library would buy you is the interaction layer — tap-a-slice tooltips, pinch to zoom,
 * animated transitions, "nice" axis ticks, label collision avoidance. None of that is needed to
 * make the point, and all of it would obscure it.
 *
 * Every colour comes from `MaterialTheme.colorScheme`, so the agent picks the *shape* of the
 * answer and the app picks every pixel of it — dark mode and dynamic colour included.
 */

// ---------------------------------------------------------------------------
// Schema: what the agent may ask for.
// ---------------------------------------------------------------------------

/**
 * Charts take two parallel arrays rather than an array of objects. It is a deliberately dull
 * shape: models get it right far more often than they get nested objects right, and the renderer
 * can read it with the ordinary list bindings.
 */
/**
 * Charts can be tapped. The renderer adds `label`, `value` and `index` of whatever the user
 * actually touched to the action payload, which the agent could not have known in advance.
 */
private val chartActionSchema: com.example.a2uicomposelabs.a2ui.model.A2uiSchema =
    com.example.a2uicomposelabs.a2ui.model.A2uiObjectSchema(
        properties = mapOf(
            "name" to com.example.a2uicomposelabs.a2ui.model.A2uiStringSchema(
                "Action name sent when a bar or slice is tapped."
            ),
            "context" to com.example.a2uicomposelabs.a2ui.model.A2uiObjectSchema(
                additionalPropertiesSchema = com.example.a2uicomposelabs.a2ui.model.A2uiAnySchema(),
                description = "Extra values to send; the tapped label and value are added for you.",
            ),
        ),
        required = setOf("name"),
        isAdditionalPropertiesAllowed = false,
    )

private fun chartSchema(extra: Map<String, com.example.a2uicomposelabs.a2ui.model.A2uiSchema> = emptyMap()) =
    componentSchema(
        properties = mapOf(
            "title" to dynamicString("Heading shown above the chart."),
            "labels" to dynamicString("Bound path to the array of category labels."),
            "values" to dynamicString("Bound path to the array of numbers, same length as labels."),
            "valueLabels" to dynamicString("Optional bound path to pre-formatted value strings."),
            "action" to chartActionSchema,
        ) + extra,
        required = setOf("labels", "values"),
    )

internal val ChartCatalogSchema: List<A2uiComponentDefinition> = listOf(
    A2uiComponentDefinition(
        name = "BarChart",
        description =
            "A vertical bar chart. Use it to compare a handful of named categories — sales by " +
                    "region, orders by product. Bind labels and values to two arrays the app fills.",
        propertySchema = chartSchema(),
    ),
    A2uiComponentDefinition(
        name = "LineChart",
        description =
            "A line chart over an ordered series. Use it for anything across time — a monthly " +
                    "trend, a running total. Bind labels and values to two arrays the app fills.",
        propertySchema = chartSchema(),
    ),
    A2uiComponentDefinition(
        name = "PieChart",
        description =
            "A pie or donut chart showing how a total splits between categories. Use it for share " +
                    "and composition questions, not for trends, and keep it under about six slices.",
        propertySchema = chartSchema(
            mapOf("donut" to A2uiBooleanSchema("Draw it as a donut with a hollow centre."))
        ),
    ),
    A2uiComponentDefinition(
        name = "StatTile",
        description =
            "One headline number with a caption, for the figures a chart cannot show at a glance. " +
                    "Put two or three in a Row above a chart.",
        propertySchema = componentSchema(
            properties = mapOf(
                "label" to dynamicString("Caption under the number."),
                "value" to dynamicString("The number, already formatted for display."),
                "delta" to dynamicString("Optional change, such as \"+12%\". Shown in a muted style."),
            ),
            required = setOf("value"),
        ),
    ),
)

// ---------------------------------------------------------------------------
// Factories: the app's own drawing code.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
internal val ChartCatalog: Map<String, A2uiComponentFactory> = mapOf(

    "BarChart" to { node, scope, _ ->
        val labels = scope.readStringList(node.props["labels"])
        val values = scope.readFloatList(node.props["values"])
        val onTap: ((Int) -> Unit)? =
            if (node.props["action"] == null) null
            else { index -> scope.dispatchAction(node, tapPayload(labels, values, index)) }
        ChartFrame(
            title = scope.readString(node.props["title"]),
            labels = labels,
            values = values,
            // A bar is whichever vertical slot the finger landed in.
            hitTest = onTap?.let { tap ->
                { offset, size -> tap(((offset.x / (size.width / values.size)).toInt()).coerceIn(0, values.size - 1)) }
            },
        ) { palette, measurer ->
            val maxValue = max(values.maxOrNull() ?: 0f, 1f)
            val slot = size.width / values.size
            val barWidth = slot * 0.6f
            val plotHeight = size.height - LabelBand
            values.forEachIndexed { index, value ->
                val barHeight = (value / maxValue) * plotHeight
                drawRoundedBar(
                    color = palette[index % palette.size],
                    left = index * slot + (slot - barWidth) / 2f,
                    width = barWidth,
                    top = plotHeight - barHeight,
                    height = barHeight,
                )
                drawCentredLabel(
                    measurer,
                    labels.getOrElse(index) { "" },
                    index * slot + slot / 2f,
                    plotHeight
                )
            }
            drawLine(
                color = palette.last().copy(alpha = 0.25f),
                start = Offset(0f, plotHeight),
                end = Offset(size.width, plotHeight),
                strokeWidth = 1.dp.toPx(),
            )
        }
    },

    "LineChart" to { node, scope, _ ->
        val labels = scope.readStringList(node.props["labels"])
        val values = scope.readFloatList(node.props["values"])
        ChartFrame(scope.readString(node.props["title"]), labels, values) { palette, measurer ->
            val maxValue = max(values.maxOrNull() ?: 0f, 1f)
            val minValue = values.minOrNull() ?: 0f
            val span = max(maxValue - minValue, 1f)
            val plotHeight = size.height - LabelBand
            val step = if (values.size > 1) size.width / (values.size - 1) else size.width
            fun pointAt(index: Int) = Offset(
                x = index * step,
                // A little headroom so the peak is not welded to the top edge.
                y = plotHeight - ((values[index] - minValue) / span) * plotHeight * 0.9f - plotHeight * 0.05f,
            )

            val path = Path().apply {
                values.indices.forEach { index ->
                    val point = pointAt(index)
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(path, color = palette.first(), style = Stroke(width = 2.5f.dp.toPx()))
            values.indices.forEach { index ->
                drawCircle(palette.first(), radius = 3.5f.dp.toPx(), center = pointAt(index))
            }
            // Only every other label when the series is long, so they do not collide.
            val stride = if (values.size > 7) 2 else 1
            labels.forEachIndexed { index, label ->
                if (index % stride == 0) drawCentredLabel(measurer, label, index * step, plotHeight)
            }
        }
    },

    "PieChart" to { node, scope, _ ->
        val labels = scope.readStringList(node.props["labels"])
        val values = scope.readFloatList(node.props["values"])
        val donut = scope.readBoolean(node.props["donut"])
        val title = scope.readString(node.props["title"])
        val palette = chartPalette()
        val tappable = node.props["action"] != null

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleSmall)
            if (values.isEmpty()) {
                EmptyChart()
            } else {
                val total = max(values.sum(), 0.0001f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(
                        Modifier
                            .size(140.dp)
                            .padding(4.dp)
                            .then(
                                if (!tappable) Modifier
                                else Modifier.pointerInput(labels, values) {
                                    detectTapGestures { offset ->
                                        sliceAt(offset, size.toSize(), values, total, donut)
                                            ?.let { index ->
                                                scope.dispatchAction(
                                                    node,
                                                    tapPayload(labels, values, index),
                                                )
                                            }
                                    }
                                }
                            )
                    ) {
                        var startAngle = -90f
                        values.forEachIndexed { index, value ->
                            val sweep = value / total * 360f
                            val stroke = 26.dp.toPx()
                            drawArc(
                                color = palette[index % palette.size],
                                startAngle = startAngle,
                                sweepAngle = sweep - 1f, // a hairline gap reads as separate slices
                                useCenter = !donut,
                                topLeft = if (donut) Offset(
                                    stroke / 2f,
                                    stroke / 2f
                                ) else Offset.Zero,
                                size = if (donut) Size(
                                    size.width - stroke,
                                    size.height - stroke
                                ) else size,
                                style = if (donut) Stroke(width = stroke) else androidx.compose.ui.graphics.drawscope.Fill,
                            )
                            startAngle += sweep
                        }
                    }
                    FlowRow(
                        modifier = Modifier.padding(start = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        labels.forEachIndexed { index, label ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(10.dp)) {
                                    drawCircle(palette[index % palette.size])
                                }
                                Text(
                                    "  $label  ${(values.getOrElse(index) { 0f } / total * 100f).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    },

    "StatTile" to { node, scope, _ ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    scope.readString(node.props["value"]),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    scope.readString(node.props["label"]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                val delta = scope.readString(node.props["delta"])
                if (delta.isNotEmpty()) {
                    Text(delta, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    },
)

// ---------------------------------------------------------------------------
// Shared drawing helpers.
// ---------------------------------------------------------------------------

/**
 * What a tapped point contributes to the action. The agent wrote `{"name":"drillDown"}`; the
 * renderer fills in which slice the finger landed on.
 */
private fun tapPayload(labels: List<String>, values: List<Float>, index: Int) = mapOf(
    "label" to JsonPrimitive(labels.getOrElse(index) { "" }),
    "value" to JsonPrimitive(values.getOrElse(index) { 0f }),
    "index" to JsonPrimitive(index),
)

/**
 * Which slice is under [offset]? Angles run clockwise from twelve o'clock, matching the order
 * the arcs are drawn in. A donut also has to miss the hole in the middle.
 */
private fun sliceAt(
    offset: Offset,
    size: Size,
    values: List<Float>,
    total: Float,
    donut: Boolean,
): Int? {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val dx = offset.x - centre.x
    val dy = offset.y - centre.y
    val radius = hypot(dx, dy)
    val outer = minOf(size.width, size.height) / 2f
    if (radius > outer) return null
    // The stroke width used when drawing the donut, in the same units.
    if (donut && radius < outer - DonutStrokeDp * 2.5f) return null

    val degrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
    var sweptSoFar = 0f
    values.forEachIndexed { index, value ->
        val sweep = value / total * 360f
        if (degrees >= sweptSoFar && degrees < sweptSoFar + sweep) return index
        sweptSoFar += sweep
    }
    return values.lastIndex.takeIf { it >= 0 }
}

/** Kept in one place so the drawing and the hit test cannot drift apart. */
private const val DonutStrokeDp = 26f

/** Height reserved under the plot for the category labels. */
private val LabelBand = 22.dp.value * 2.6f

@Composable
private fun chartPalette(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(primary, tertiary, secondary, error, primaryContainer.copy(alpha = 1f), outline)
}

/** Title, empty state, and the canvas every plot shares. */
@Composable
private fun ChartFrame(
    title: String,
    labels: List<String>,
    values: List<Float>,
    /** Null when the chart carries no action, so an undecorated chart stays undecorated. */
    hitTest: ((Offset, Size) -> Unit)? = null,
    draw: DrawScope.(palette: List<Color>, measurer: TextMeasurer) -> Unit,
) {
    val palette = chartPalette()
    val measurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleSmall)
        if (values.isEmpty() || labels.isEmpty()) {
            EmptyChart()
        } else {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.9f)
                    .padding(top = 4.dp)
                    .then(
                        if (hitTest == null) Modifier
                        else Modifier.pointerInput(labels, values) {
                            detectTapGestures { offset -> hitTest(offset, size.toSize()) }
                        }
                    ),
            ) {
                draw(palette, measurer)
            }
        }
    }
}

@Composable
private fun EmptyChart() {
    Surface(
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("waiting for data…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun DrawScope.drawRoundedBar(
    color: Color,
    left: Float,
    width: Float,
    top: Float,
    height: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, max(height, 1f)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
    )
}

private fun DrawScope.drawCentredLabel(
    measurer: TextMeasurer,
    text: String,
    centreX: Float,
    plotBottom: Float,
) {
    if (text.isEmpty()) return
    val style = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val layout = measurer.measure(text, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(centreX - layout.size.width / 2f, plotBottom + 6f),
    )
}
