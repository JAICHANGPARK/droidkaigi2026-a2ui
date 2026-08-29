package com.example.a2uicomposelabs.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One component the app is willing to render, and the exact shape of its properties.
 * Mirrors `androidx.a2ui.engine.catalog.A2uiCoreComponentDefinition`.
 *
 * [description] is not decoration — it is what the agent reads to decide when to use
 * the component, so it ships inside the system prompt.
 */
data class A2uiComponentDefinition(
    val name: String,
    val description: String,
    val propertySchema: A2uiSchema,
)

/**
 * The allowlist, as data. The app declares what exists exactly once, and that one
 * declaration is used twice:
 *  - [toJsonSchema] tells the agent what it may emit (system prompt),
 *  - [A2uiSchemaValidator] enforces the same thing on what comes back.
 *
 * Mirrors `androidx.a2ui.engine.catalog.A2uiCoreCatalog` and its serializer.
 */
class A2uiCatalog(
    val id: String,
    val components: Map<String, A2uiComponentDefinition>,
    /** The computations the agent may ask for. Empty means "data in, no computation". */
    val functions: List<A2uiFunction> = emptyList(),
) {

    constructor(
        id: String,
        definitions: List<A2uiComponentDefinition>,
        functions: List<A2uiFunction> = emptyList(),
    ) : this(id, definitions.associateBy(A2uiComponentDefinition::name), functions)

    /** Resolves and runs `{"call": ...}` payloads against [functions]. */
    val evaluator: A2uiDynamicEvaluator by lazy { A2uiDynamicEvaluator(functions) }

    /** Extends the catalog with the app's own components (see the playlist case study). */
    operator fun plus(extra: List<A2uiComponentDefinition>): A2uiCatalog =
        A2uiCatalog(id, components + extra.associateBy(A2uiComponentDefinition::name), functions)

    /** Extends the catalog with the app's own functions. */
    fun withFunctions(extra: List<A2uiFunction>): A2uiCatalog =
        A2uiCatalog(id, components, functions + extra)

    /**
     * The same catalog under the app's own id — components and functions both.
     *
     * Use this to derive an app catalog from the basic one. Rebuilding it by hand from
     * `BasicCatalogSchema.components` silently drops the functions, and the first symptom is
     * the renderer rejecting `required` as an unknown function.
     */
    fun withId(newId: String): A2uiCatalog = A2uiCatalog(newId, components, functions)

    /**
     * Serializes the whole catalog as one JSON Schema document, the way
     * `serializeCatalogToJsonSchema` does in a2ui-engine. This is the text the agent sees.
     */
    fun toJsonSchema(): String = buildJsonObject {
        put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        put("\$id", id)
        put("catalogId", id)
        put(
            "components",
            JsonObject(
                components.mapValues { (_, definition) ->
                    buildJsonObject {
                        put("description", definition.description)
                        definition.propertySchema.toJsonElement().let { schema ->
                            (schema as JsonObject).forEach { (key, value) -> put(key, value) }
                        }
                    }
                }
            ),
        )
        put(
            "functions",
            JsonObject(
                functions.associate { function ->
                    function.definition.name to buildJsonObject {
                        put("description", function.definition.description)
                        put("returnType", function.definition.returnType.value)
                        // Only written when it is not the default, so the catalog the agent
                        // reads says nothing about RPC until a function actually uses it.
                        if (function.definition.allowedCallers != A2uiFunctionCaller.RENDERER_ONLY) {
                            put("allowedCallers", function.definition.allowedCallers.value)
                        }
                        put("args", function.definition.argumentSchema.toJsonElement())
                    }
                }
            ),
        )
        put(
            "\$defs",
            buildJsonObject {
                // Every named schema, written out once. Mirrors
                // `collectLocalDefinitionsFromCatalog` in A2uiCoreCatalogSerializer.
                collectDefinitions().forEach { (name, schema) ->
                    put(name, schema.toJsonElement())
                }
                put(
                    "anyComponent",
                    buildJsonObject {
                        put(
                            "oneOf",
                            JsonArray(
                                components.keys.map { name ->
                                    buildJsonObject { put("\$ref", "#/components/$name") }
                                }
                            ),
                        )
                        put("discriminator", buildJsonObject { put("propertyName", "component") })
                    },
                )
                put(
                    "anyFunction",
                    buildJsonObject {
                        put(
                            "oneOf",
                            JsonArray(
                                functions.map { function ->
                                    buildJsonObject {
                                        put("\$ref", "#/functions/${function.definition.name}")
                                    }
                                }
                            ),
                        )
                    },
                )
            },
        )
    }.toString()
}

/** Walks every schema in the catalog and gathers the named ones, definitions included. */
private fun A2uiCatalog.collectDefinitions(): Map<String, A2uiSchema> {
    val found = linkedMapOf<String, A2uiSchema>()
    val seen = mutableSetOf<A2uiSchema>()

    fun walk(schema: A2uiSchema) {
        if (!seen.add(schema)) return
        when (schema) {
            is A2uiNamedSchema -> {
                found.putIfAbsent(schema.definitionName, schema.definition)
                walk(schema.definition)
            }
            is A2uiObjectSchema -> {
                schema.properties.values.forEach(::walk)
                schema.additionalPropertiesSchema?.let(::walk)
            }
            is A2uiArraySchema -> schema.items?.let(::walk)
            else -> Unit
        }
        schema.keywords.filterIsInstance<A2uiSchemaKeyword.OneOf>()
            .forEach { keyword -> keyword.schemas.forEach(::walk) }
    }

    components.values.forEach { walk(it.propertySchema) }
    functions.forEach { walk(it.definition.argumentSchema) }
    return found
}

// ---------------------------------------------------------------------------
// Building blocks — the A2UI "common types", small enough to keep in one place.
// ---------------------------------------------------------------------------

/** `{"path": "/json/pointer"}` — a reference into the surface data model. */
val A2uiDataBinding: A2uiSchema = A2uiNamedSchema(
    definitionName = "DataBinding",
    definition = A2uiObjectSchema(
        properties = mapOf("path" to A2uiStringSchema("A JSON Pointer into the surface data model.")),
        required = setOf("path"),
        isAdditionalPropertiesAllowed = false,
    ),
)

/**
 * `{"call": "...", "args": {...}}` — an invocation of a catalog function. Constrained to the
 * return type the property expects, so a string property cannot be filled by a function that
 * returns a boolean.
 */
fun functionCall(returnType: A2uiFunctionReturnType): A2uiSchema = A2uiNamedSchema(
    definitionName = "FunctionCall" + returnType.value.replaceFirstChar(Char::uppercase),
    definition = A2uiObjectSchema(
        properties = mapOf(
            "call" to A2uiStringSchema("Name of a function in this catalog."),
            "args" to A2uiObjectSchema(
                additionalPropertiesSchema = A2uiAnySchema(),
                description = "Named arguments. Each may itself be a literal, a binding, or a call.",
            ),
            "returnType" to A2uiStringSchema(
                description = "What the call returns.",
                keywords = listOf(A2uiSchemaKeyword.Const(JsonPrimitive(returnType.value))),
            ),
        ),
        required = setOf("call"),
        isAdditionalPropertiesAllowed = false,
    ),
)

/**
 * The core A2UI property shape: a literal, a binding that resolves to one, or a function call
 * that returns one. Every dynamic property below is one of these three.
 */
fun dynamicString(description: String? = null): A2uiSchema =
    A2uiNamedSchema(
        definitionName = "DynamicString",
        description = description,
        definition = A2uiAnySchema(
            keywords = listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        A2uiStringSchema(),
                        A2uiDataBinding,
                        functionCall(A2uiFunctionReturnType.STRING),
                    )
                )
            )
        ),
    )

fun dynamicNumber(description: String? = null): A2uiSchema =
    A2uiNamedSchema(
        definitionName = "DynamicNumber",
        description = description,
        definition = A2uiAnySchema(
            keywords = listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        A2uiNumberSchema(),
                        A2uiDataBinding,
                        functionCall(A2uiFunctionReturnType.NUMBER),
                    )
                )
            )
        ),
    )

fun dynamicBoolean(description: String? = null): A2uiSchema =
    A2uiNamedSchema(
        definitionName = "DynamicBoolean",
        description = description,
        definition = A2uiAnySchema(
            keywords = listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        A2uiBooleanSchema(),
                        A2uiDataBinding,
                        functionCall(A2uiFunctionReturnType.BOOLEAN),
                    )
                )
            )
        ),
    )

/**
 * A property the user can edit. It must be a binding, never a literal: the renderer writes
 * user input back to this path, and there is nowhere to write a literal.
 */
fun twoWay(description: String): A2uiSchema =
    A2uiNamedSchema(
        definitionName = "TwoWayBinding",
        description = description,
        definition = A2uiObjectSchema(
            properties = mapOf(
                "path" to A2uiStringSchema("JSON Pointer written back on user input.")
            ),
            required = setOf("path"),
            isAdditionalPropertiesAllowed = false,
        ),
    )

/** `children: ["id", ...]` — an adjacency list of component IDs, not nested components. */
fun childList(description: String): A2uiSchema =
    A2uiArraySchema(items = A2uiStringSchema(), description = description)

private fun enumOf(description: String, vararg values: String): A2uiSchema =
    A2uiStringSchema(
        description = description,
        keywords = listOf(A2uiSchemaKeyword.Enum(values.map(::JsonPrimitive))),
    )

/** `{"name": "...", "context": {...}}` — what the renderer sends back when a Button is tapped. */
/**
 * What a button does. v1.0 gives this two forms and insists on exactly one of them.
 *
 * `name` + `context` announces something and expects no answer — there is no id on it, and
 * nothing to correlate an answer with, because v1.0 deleted `actionResponse`. `functionCall`
 * asks something and does expect an answer, which the renderer gets back over
 * `callAgentFunction` / `agentFunctionResponse`.
 *
 * The spec nests the first form under an `event` key; this renderer has always written it flat,
 * and every recorded stream and every rehearsed prompt in this repo depends on that. Changing it
 * now would be a rename with no lesson in it, so the flat form stays and the new form arrives
 * spelled exactly as the spec spells it.
 */
private val actionSchema: A2uiSchema = A2uiObjectSchema(
    properties = mapOf(
        "name" to A2uiStringSchema("Action name the agent will receive."),
        "context" to A2uiObjectSchema(
            additionalPropertiesSchema = A2uiAnySchema(),
            description = "Values to send with the action. Each may be a literal or {\"path\"}.",
        ),
        "functionCall" to A2uiObjectSchema(
            properties = mapOf(
                "call" to A2uiStringSchema("Name of the function to run, from this catalog."),
                "args" to A2uiObjectSchema(
                    additionalPropertiesSchema = A2uiAnySchema(),
                    description = "Arguments. Each may be a literal or {\"path\"}.",
                ),
            ),
            required = setOf("call"),
            isAdditionalPropertiesAllowed = false,
            description =
                "Use INSTEAD of name/context when the button asks the agent for a value it " +
                    "will wait on — a booking reference, a ticket number. The function must be " +
                    "one this catalog declares with allowedCallers agentOnly or rendererOrAgent.",
        ),
    ),
    exactlyOneOf = setOf("name", "functionCall"),
    isAdditionalPropertiesAllowed = false,
)

/**
 * Builds a component property schema with the common properties merged in and
 * `additionalProperties: false` — an invented property is a rejected message, not a
 * silently ignored one.
 */
fun componentSchema(
    properties: Map<String, A2uiSchema> = emptyMap(),
    required: Set<String> = emptySet(),
): A2uiSchema = A2uiObjectSchema(
    properties = properties + commonProperties,
    required = required,
    isAdditionalPropertiesAllowed = false,
)

/**
 * `{"condition": <boolean>, "message": "..."}` — one client-side validation rule. The
 * condition is normally a call to `required`, `length`, `email`, `regex` or `numeric`, and
 * the message is what the user reads when it comes back false.
 */
val A2uiCheckRule: A2uiSchema = A2uiNamedSchema(
    definitionName = "CheckRule",
    definition = A2uiObjectSchema(
    properties = mapOf(
        "condition" to dynamicBoolean("Must evaluate to true for the input to be valid."),
        "message" to A2uiStringSchema("Shown to the user when the condition fails."),
        "severity" to enumOf(
            "How hard the rule is. 'error' blocks submission; 'warning' only advises. " +
                "Defaults to 'error'.",
            "error", "warning",
        ),
    ),
    required = setOf("condition", "message"),
    isAdditionalPropertiesAllowed = false,
    description = "A single validation rule applied to an input component.",
    ),
)

private val commonProperties: Map<String, A2uiSchema> = mapOf(
    "accessibility" to A2uiObjectSchema(description = "Accessibility attributes."),
    "weight" to A2uiNumberSchema("Relative size inside a Row or Column."),
    "checks" to A2uiArraySchema(
        items = A2uiCheckRule,
        description = "Validation rules evaluated on the client as the user types.",
    ),
)

// ---------------------------------------------------------------------------
// The Basic Catalog (spec v1.0) — all 18 components, described for the agent.
// Keep in lockstep with the factories in BasicCatalog.kt / ExtraCatalog.kt;
// A2uiCatalogSchemaTest fails the build if the two ever drift apart.
// ---------------------------------------------------------------------------

const val BASIC_CATALOG_ID = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"

val BasicCatalogSchema: A2uiCatalog = A2uiCatalog(
    id = BASIC_CATALOG_ID,
    functions = BasicCatalogFunctions,
    definitions = listOf(

        A2uiComponentDefinition(
            name = "Text",
            description = "A run of text. The primary way to show information.",
            propertySchema = componentSchema(
                properties = mapOf("text" to dynamicString("The text to display.")),
                required = setOf("text"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Column",
            description = "Stacks its children vertically.",
            propertySchema = componentSchema(
                properties = mapOf("children" to childList("IDs of the children, top to bottom.")),
            ),
        ),

        A2uiComponentDefinition(
            name = "Row",
            description = "Places its children side by side, horizontally.",
            propertySchema = componentSchema(
                properties = mapOf("children" to childList("IDs of the children, left to right.")),
            ),
        ),

        A2uiComponentDefinition(
            name = "Card",
            description = "A padded, elevated container. Use it as the outer shell of a surface.",
            propertySchema = componentSchema(
                properties = mapOf("children" to childList("IDs of the children inside the card.")),
            ),
        ),

        A2uiComponentDefinition(
            name = "List",
            description =
                "Repeats a child over an array in the data model, or lays out fixed children. " +
                    "For the repeating form pass children as {\"componentId\", \"path\"}; inside " +
                    "that template, relative paths resolve per item.",
            propertySchema = componentSchema(
                properties = mapOf(
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
                    "direction" to enumOf("Layout axis.", "vertical", "horizontal"),
                ),
            ),
        ),

        A2uiComponentDefinition(
            name = "Divider",
            description = "A thin horizontal rule separating sections.",
            propertySchema = componentSchema(),
        ),

        A2uiComponentDefinition(
            name = "Button",
            description =
                "A tappable button. On tap the renderer sends an action back to the agent, " +
                    "with every {\"path\"} in context resolved against the data model.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "label" to dynamicString("Text on the button."),
                    "action" to actionSchema,
                ),
                required = setOf("label"),
            ),
        ),

        A2uiComponentDefinition(
            name = "TextField",
            description = "A single-line text input. Edits are written back to the bound path.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "label" to dynamicString("Field label."),
                    "text" to twoWay("Bound path holding the field's text."),
                ),
                required = setOf("text"),
            ),
        ),

        A2uiComponentDefinition(
            name = "CheckBox",
            description = "A labelled checkbox bound to a boolean in the data model.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "label" to dynamicString("Text beside the box."),
                    "value" to twoWay("Bound path holding the boolean."),
                ),
                required = setOf("value"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Slider",
            description = "Picks a number in a range. The value is written back to the bound path.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "label" to dynamicString("Label above the slider."),
                    "min" to dynamicNumber("Lowest selectable value."),
                    "max" to dynamicNumber("Highest selectable value."),
                    "value" to twoWay("Bound path holding the number."),
                ),
                required = setOf("value"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Image",
            description = "A remote image. The URL must be https; other schemes are dropped.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "url" to dynamicString("https URL of the image."),
                    "description" to dynamicString("Alt text for screen readers."),
                    "fit" to enumOf(
                        "How the image fills its box.",
                        "contain", "cover", "fill", "none", "scaleDown",
                    ),
                    "variant" to enumOf(
                        "Preset size.",
                        "icon", "avatar", "smallFeature", "mediumFeature", "largeFeature", "header",
                    ),
                ),
                required = setOf("url"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Icon",
            description = "A Material symbol, chosen by name.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "name" to A2uiAnySchema(
                        description = "A Material icon name, or {\"svgPath\"} for a custom 24dp path.",
                        keywords = listOf(
                            A2uiSchemaKeyword.OneOf(
                                listOf(
                                    A2uiStringSchema(),
                                    A2uiDataBinding,
                                    A2uiObjectSchema(
                                        properties = mapOf("svgPath" to A2uiStringSchema()),
                                        required = setOf("svgPath"),
                                        isAdditionalPropertiesAllowed = false,
                                    ),
                                )
                            )
                        ),
                    )
                ),
                required = setOf("name"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Tabs",
            description = "A tab bar; each tab shows one child component.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "tabs" to A2uiArraySchema(
                        items = A2uiObjectSchema(
                            properties = mapOf(
                                "title" to dynamicString("Tab label."),
                                "child" to A2uiStringSchema("Component ID shown under this tab."),
                            ),
                            required = setOf("title", "child"),
                            isAdditionalPropertiesAllowed = false,
                        ),
                        description = "The tabs, in order.",
                    )
                ),
                required = setOf("tabs"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Modal",
            description = "Shows a trigger component; tapping it opens a dialog with the content.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "trigger" to A2uiStringSchema("Component ID that opens the dialog."),
                    "content" to A2uiStringSchema("Component ID rendered inside the dialog."),
                ),
                required = setOf("trigger", "content"),
            ),
        ),

        A2uiComponentDefinition(
            name = "ChoicePicker",
            description = "Single or multiple choice over a fixed set of options.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "label" to dynamicString("Label above the options."),
                    "variant" to enumOf(
                        "How many options may be selected.",
                        "mutuallyExclusive", "multipleSelection",
                    ),
                    "displayStyle" to enumOf("How options are drawn.", "checkbox", "chips"),
                    "filterable" to A2uiBooleanSchema("Adds a filter box above the options."),
                    "options" to A2uiArraySchema(
                        items = A2uiObjectSchema(
                            properties = mapOf(
                                "label" to dynamicString("Text shown for this option."),
                                "value" to A2uiStringSchema("Value stored when selected."),
                            ),
                            required = setOf("label", "value"),
                            isAdditionalPropertiesAllowed = false,
                        ),
                        description = "The selectable options.",
                    ),
                    "value" to twoWay("Bound path holding the array of selected values."),
                ),
                required = setOf("options", "value"),
            ),
        ),

        A2uiComponentDefinition(
            name = "DateTimeInput",
            description = "Picks a date and/or a time. The value is an ISO 8601 string.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "label" to dynamicString("Field label."),
                    "value" to twoWay("Bound path holding the ISO 8601 value."),
                    "enableDate" to A2uiBooleanSchema("Offer a date picker."),
                    "enableTime" to A2uiBooleanSchema("Offer a time picker."),
                    "min" to dynamicString("Earliest allowed value, ISO 8601."),
                    "max" to dynamicString("Latest allowed value, ISO 8601."),
                ),
                required = setOf("value"),
            ),
        ),

        A2uiComponentDefinition(
            name = "AudioPlayer",
            description = "Plays an audio track. The URL must be https.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "url" to dynamicString("https URL of the audio."),
                    "description" to dynamicString("Track name shown beside the play button."),
                ),
                required = setOf("url"),
            ),
        ),

        A2uiComponentDefinition(
            name = "Video",
            description = "Plays a video, with an optional poster frame. Both URLs must be https.",
            propertySchema = componentSchema(
                properties = mapOf(
                    "url" to dynamicString("https URL of the video."),
                    "posterUrl" to dynamicString("https URL of the poster image."),
                    "description" to dynamicString("Alt text for screen readers."),
                ),
                required = setOf("url"),
            ),
        ),
    ),
)
