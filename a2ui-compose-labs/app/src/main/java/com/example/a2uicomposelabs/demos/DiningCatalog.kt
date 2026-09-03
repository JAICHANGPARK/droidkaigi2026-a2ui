package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.a2uicomposelabs.a2ui.model.A2uiAgentFunction
import com.example.a2uicomposelabs.a2ui.model.A2uiAnySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiArraySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.model.A2uiComponentDefinition
import com.example.a2uicomposelabs.a2ui.ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.model.A2uiExecutionContext
import com.example.a2uicomposelabs.a2ui.model.A2uiFunction
import com.example.a2uicomposelabs.a2ui.model.A2uiFunctionArgs
import com.example.a2uicomposelabs.a2ui.model.A2uiFunctionCaller
import com.example.a2uicomposelabs.a2ui.model.A2uiFunctionDefinition
import com.example.a2uicomposelabs.a2ui.model.A2uiFunctionException
import com.example.a2uicomposelabs.a2ui.model.A2uiFunctionReturnType
import com.example.a2uicomposelabs.a2ui.model.A2uiNumberSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiObjectSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiStringSchema
import com.example.a2uicomposelabs.a2ui.model.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.model.componentSchema
import com.example.a2uicomposelabs.a2ui.model.dynamicNumber
import com.example.a2uicomposelabs.a2ui.model.dynamicString
import com.example.a2uicomposelabs.a2ui.model.twoWay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The catalog behind demo 8 — the eighteen basic components, one component the app owns, and
 * two functions the protocol cannot express.
 *
 * The interesting one is [MenuItemRow]. A quantity stepper is a *stateful* control: tapping `+`
 * has to change a number that something else — the total, the submit button's check — reads
 * back immediately. A2UI has no place to put that state, so the row does what every input
 * component in the spec does: it writes through `twoWay` into the surface data model and lets
 * the components bound to the same path recompose. Nothing goes to the network per tap.
 *
 * The two functions exist because **A2UI has no arithmetic.** There is no `+`, no `*`, no
 * comparison beyond the validation helpers. The delivery trace hits this wall the moment two
 * dishes are selected and the total stays at zero. There are exactly two ways out: ask the
 * agent (a round trip per tap) or register a function the renderer runs locally. This app takes
 * the second, so a stepper tap re-prices the basket in the same frame.
 */

private const val DINING_CATALOG_ID = "app.dining.catalog/v1"

// ---------------------------------------------------------------------------
// Schema: what the agent may ask for.
// ---------------------------------------------------------------------------

internal val DiningComponentDefinitions: List<A2uiComponentDefinition> = listOf(
    A2uiComponentDefinition(
        name = "MenuItemRow",
        description =
            "One orderable dish: name, unit price, and a −/+ stepper setting how many the " +
                "customer wants. Put it inside a List that repeats over the menu array and bind " +
                "quantity to the item's own RELATIVE path, so each row counts separately. Use " +
                "this instead of CheckBox whenever the customer may want more than one of " +
                "something.",
        propertySchema = componentSchema(
            properties = mapOf(
                "name" to dynamicString("Dish name."),
                "note" to dynamicString("Optional second line — portion, spice level, allergens."),
                "priceLabel" to dynamicString(
                    "Unit price already formatted for display, e.g. \"\u20a914,000\". Format it with " +
                        "formatNumber or formatCurrency; do not do arithmetic on it."
                ),
                "quantity" to twoWay(
                    "Bound path holding how many of this dish are ordered. 0 means not ordered."
                ),
                "max" to dynamicNumber("Largest quantity allowed for one dish. Defaults to 20."),
            ),
            required = setOf("name", "quantity"),
        ),
    ),
    A2uiComponentDefinition(
        name = "DeliveryProgress",
        description =
            "The live state of an order on its way: a vertical list of stages with the one it " +
                "has reached picked out. Bind steps to an array of stage names and current to " +
                "the index of the stage in progress. The app moves `current` on its own with " +
                "updateDataModel — send this component once and never send it again.",
        propertySchema = componentSchema(
            properties = mapOf(
                "steps" to dynamicString("Bound path to the array of stage names, in order."),
                "current" to dynamicNumber("Index of the stage in progress. 0 is the first."),
                "note" to dynamicString("A line under the stages — the rider, the ETA."),
            ),
            required = setOf("steps", "current"),
        ),
    ),
    A2uiComponentDefinition(
        name = "OrderTotalRow",
        description =
            "The emphasised total line under a menu. Bind value to a calcOrderTotal call wrapped " +
                "in formatNumber or formatCurrency — A2UI itself cannot add prices up.",
        propertySchema = componentSchema(
            properties = mapOf(
                "label" to dynamicString("Left-hand label, e.g. \"Total\" or \"Amount paid\"."),
                "value" to dynamicString("Right-hand amount, already formatted."),
                "note" to dynamicString("Optional small print, e.g. a delivery fee or an ETA."),
            ),
            required = setOf("value"),
        ),
    ),
)

// ---------------------------------------------------------------------------
// Functions: the arithmetic the protocol does not have.
// ---------------------------------------------------------------------------

/** Reads a number out of a JSON field, tolerating a numeric string. Missing counts as zero. */
private fun numberAt(item: JsonObject, key: String): Double {
    val primitive = item[key] as? JsonPrimitive ?: return 0.0
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
}

private fun itemsArgument(args: Map<String, JsonElement>, function: String): JsonArray =
    args["items"] as? JsonArray
        ?: throw A2uiFunctionException("$function needs an array in 'items'")

private val basketArgumentSchema = A2uiObjectSchema(
    properties = mapOf(
        "items" to A2uiArraySchema(
            items = A2uiAnySchema(),
            description = "The menu array. Every entry carries a unit price and a quantity.",
        ),
        "priceKey" to A2uiStringSchema("Field holding the unit price. Defaults to \"price\"."),
        "quantityKey" to A2uiStringSchema("Field holding the count. Defaults to \"quantity\"."),
    ),
    required = setOf("items"),
    isAdditionalPropertiesAllowed = false,
)

/**
 * `price × quantity`, summed. The one computation a food order cannot do without, and the one
 * the protocol deliberately refuses to provide.
 */
private object CalcOrderTotalFunction : A2uiFunction {
    override val definition = A2uiFunctionDefinition(
        name = "calcOrderTotal",
        description =
            "Adds up unit price × quantity across an array of menu items and returns the total " +
                "as a number. A2UI has no arithmetic operators, so this is the only way to price " +
                "a basket. Wrap the result in formatNumber or formatCurrency to display it.",
        argumentSchema = basketArgumentSchema,
        returnType = A2uiFunctionReturnType.NUMBER,
    )

    override fun execute(
        args: Map<String, JsonElement>,
        context: A2uiExecutionContext,
    ): JsonElement {
        val items = itemsArgument(args, "calcOrderTotal")
        val priceKey = A2uiFunctionArgs.optionalString(args, "priceKey") ?: "price"
        val quantityKey = A2uiFunctionArgs.optionalString(args, "quantityKey") ?: "quantity"
        val total = items.sumOf { entry ->
            val item = entry as? JsonObject ?: return@sumOf 0.0
            numberAt(item, priceKey) * numberAt(item, quantityKey)
        }
        return JsonPrimitive(total)
    }
}

/**
 * How many dishes are in the basket. Separate from the total because a button that must be
 * disabled on an empty basket should not care what anything costs.
 */
private object CountOrderItemsFunction : A2uiFunction {
    override val definition = A2uiFunctionDefinition(
        name = "countOrderItems",
        description =
            "Adds up the quantities across an array of menu items and returns how many dishes " +
                "are in the basket. Use it with numeric(min: 1) to keep a submit button locked " +
                "until something is chosen, and with pluralize for the label.",
        argumentSchema = basketArgumentSchema,
        returnType = A2uiFunctionReturnType.NUMBER,
    )

    override fun execute(
        args: Map<String, JsonElement>,
        context: A2uiExecutionContext,
    ): JsonElement {
        val items = itemsArgument(args, "countOrderItems")
        val quantityKey = A2uiFunctionArgs.optionalString(args, "quantityKey") ?: "quantity"
        val count = items.sumOf { entry ->
            val item = entry as? JsonObject ?: return@sumOf 0.0
            numberAt(item, quantityKey)
        }
        return JsonPrimitive(count)
    }
}

/**
 * The one function in this catalog the renderer cannot run, because the house owns the answer.
 *
 * How many teams are ahead of you is not a fact any amount of local computation reaches. Until
 * v1.0 a screen had one move here: send an event and wait to be told. Now it can ask, and this
 * declaration is what makes the asking legal — `allowedCallers: agentOnly` says the code lives
 * on the other side, so the renderer refuses to evaluate it in a binding and reaches it with
 * `callAgentFunction` instead.
 *
 * There is no implementation on this side, and that is the point: [A2uiAgentFunction] publishes
 * the name and the argument shape and nothing else.
 */
private val JoinQueueFunction = A2uiAgentFunction(
    A2uiFunctionDefinition(
        name = "join_queue",
        description =
            "Puts a party in tonight's queue and returns {ticket, ahead, holdMinutes}. Only the " +
                "restaurant knows how long the line is, so call this instead of writing a " +
                "position or a wait time yourself.",
        argumentSchema = A2uiObjectSchema(
            properties = mapOf(
                "party" to A2uiNumberSchema("How many people are waiting."),
                "name" to A2uiStringSchema("Who the table is under."),
                "phone" to A2uiStringSchema("Where to text them when it is ready."),
            ),
            required = setOf("party", "name"),
            isAdditionalPropertiesAllowed = false,
        ),
        returnType = A2uiFunctionReturnType.OBJECT,
        allowedCallers = A2uiFunctionCaller.AGENT_ONLY,
    )
)

/**
 * Whether this kitchen's couriers actually reach an address.
 *
 * Every restaurant has a delivery range, and no amount of local computation finds its edge. The
 * renderer has the address the customer typed and nothing else; the house has the map. Until
 * v1.0 a screen's only move here was to send the order and be told afterwards — which means
 * taking payment for a delivery you cannot make.
 *
 * `allowedCallers: agentOnly` is what makes asking legal: the code lives on the other side, so
 * the renderer refuses to evaluate it in a binding and reaches it with `callAgentFunction`.
 */
private val CheckDeliveryAreaFunction = A2uiAgentFunction(
    A2uiFunctionDefinition(
        name = "check_delivery_area",
        description =
            "Asks the restaurant whether it delivers to an address, and how long it takes. " +
                "Returns {deliverable, etaMinutes, note}. Only the kitchen knows its own range, " +
                "so never guess one or write a delivery time yourself.",
        argumentSchema = A2uiObjectSchema(
            properties = mapOf("address" to A2uiStringSchema("Where the order is going.")),
            required = setOf("address"),
            isAdditionalPropertiesAllowed = false,
        ),
        returnType = A2uiFunctionReturnType.OBJECT,
        allowedCallers = A2uiFunctionCaller.AGENT_ONLY,
    )
)

internal val DiningFunctions: List<A2uiFunction> =
    listOf(
        CalcOrderTotalFunction,
        CountOrderItemsFunction,
        JoinQueueFunction,
        CheckDeliveryAreaFunction,
    )

/**
 * The whole catalog the dining agent is shown. Derived with [A2uiCatalog.withId] so the basic
 * fourteen functions survive — rebuilding from `components` alone loses them, and the first
 * symptom is `required` being rejected as unknown.
 */
/**
 * The review at the end of a delivery needs stars, and the survey demo already owns a good one.
 * Borrowing the definition and its factory keeps one implementation rather than two that drift.
 */
internal val BorrowedStarRating: List<A2uiComponentDefinition> =
    listOfNotNull(SurveyCatalogSchema.components["StarRating"])

internal val DiningCatalogSchema: A2uiCatalog =
    (BasicCatalogSchema.withId(DINING_CATALOG_ID) + DiningComponentDefinitions + BorrowedStarRating)
        .withFunctions(DiningFunctions)

// ---------------------------------------------------------------------------
// The other half: code the app wrote, so the agent may only ask for it.
// ---------------------------------------------------------------------------

internal val DiningCatalog: Map<String, A2uiComponentFactory> =
    mapOf("StarRating" to SurveyCatalog.getValue("StarRating")) + mapOf(

    "MenuItemRow" to { node, scope, _ ->
        // Agent-controlled numbers, so clamp before they bound anything.
        val max = scope.readFloat(node.props["max"], 20f).toInt().coerceIn(1, 99)
        val quantity = scope.readFloat(node.props["quantity"], 0f).toInt().coerceIn(0, max)

        fun setQuantity(next: Int) {
            scope.write(node.props["quantity"], JsonPrimitive(next.coerceIn(0, max)))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    scope.readString(node.props["name"]),
                    style = MaterialTheme.typography.bodyLarge,
                    // An ordered dish should read as ordered even before you reach the stepper.
                    color =
                        if (quantity > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                )
                val note = scope.readString(node.props["note"])
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                val priceLabel = scope.readString(node.props["priceLabel"])
                if (priceLabel.isNotEmpty()) {
                    Text(priceLabel, style = MaterialTheme.typography.labelMedium)
                }
            }

            FilledTonalIconButton(
                onClick = { setQuantity(quantity - 1) },
                enabled = quantity > 0,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = "Remove one ${scope.readString(node.props["name"])}",
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                quantity.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                // Fixed width so the rows do not jitter as the count crosses 9.
                modifier = Modifier.widthIn(min = 24.dp),
            )
            FilledTonalIconButton(
                onClick = { setQuantity(quantity + 1) },
                enabled = quantity < max,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add one ${scope.readString(node.props["name"])}",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    },

    "DeliveryProgress" to { node, scope, _ ->
        val steps = scope.readStringList(node.props["steps"])
        val current = scope.readFloat(node.props["current"], 0f).toInt()

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            steps.forEachIndexed { index, label ->
                val done = index < current
                val active = index == current
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when {
                            done -> Icons.Filled.CheckCircle
                            active -> Icons.Filled.RadioButtonChecked
                            else -> Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = when {
                            done -> "done"
                            active -> "in progress"
                            else -> "not started yet"
                        },
                        modifier = Modifier.size(18.dp),
                        tint = when {
                            active -> MaterialTheme.colorScheme.primary
                            done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    )
                    Text(
                        label,
                        style =
                            if (active) MaterialTheme.typography.bodyMedium
                            else MaterialTheme.typography.bodySmall,
                        color = when {
                            active -> MaterialTheme.colorScheme.onSurface
                            done -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.outline
                        },
                    )
                    // A quiet pulse on the stage in progress, so a screen that changes once a
                    // few seconds still reads as live.
                    if (active) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            val note = scope.readString(node.props["note"])
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 6.dp, start = 28.dp),
                )
            }
        }
    },

    "OrderTotalRow" to { node, scope, _ ->
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    scope.readString(node.props["label"]).ifEmpty { "Total" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    scope.readString(node.props["value"]),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val note = scope.readString(node.props["note"])
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    },
)
