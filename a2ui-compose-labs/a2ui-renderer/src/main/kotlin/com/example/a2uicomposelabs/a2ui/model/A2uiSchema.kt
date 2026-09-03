package com.example.a2uicomposelabs.a2ui.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON Schema keywords A2UI uses, mirroring `androidx.a2ui.model.schema.A2uiSchemaKeyword`.
 * The official set also has `allOf` / `anyOf` / `not`; the Basic Catalog needs none of them,
 * so they are left out here.
 */
sealed interface A2uiSchemaKeyword {

    fun writeTo(builder: JsonObjectBuilder)

    /** Exactly one subschema must match — how "literal OR data binding" is expressed. */
    data class OneOf(val schemas: List<A2uiSchema>) : A2uiSchemaKeyword {
        override fun writeTo(builder: JsonObjectBuilder) {
            builder.put("oneOf", JsonArray(schemas.map { it.toJsonElement() }))
        }
    }

    data class Enum(val values: List<JsonElement>) : A2uiSchemaKeyword {
        override fun writeTo(builder: JsonObjectBuilder) {
            builder.put("enum", JsonArray(values))
        }
    }

    data class Const(val value: JsonElement) : A2uiSchemaKeyword {
        override fun writeTo(builder: JsonObjectBuilder) {
            builder.put("const", value)
        }
    }

    /** Annotation only — documented to the agent, never enforced on input. */
    data class Default(val value: JsonElement) : A2uiSchemaKeyword {
        override fun writeTo(builder: JsonObjectBuilder) {
            builder.put("default", value)
        }
    }
}

/**
 * Base node for every A2UI schema, mirroring `androidx.a2ui.model.schema.A2uiSchema`.
 *
 * One schema tree serves two masters: [toJsonSchema] describes the catalog to the agent
 * (it goes into the system prompt), and [A2uiSchemaValidator] enforces the same tree on
 * every message that comes back. Prompt and validator cannot drift, because they are the
 * same object.
 */
sealed class A2uiSchema {

    abstract val description: String?

    open val keywords: List<A2uiSchemaKeyword> get() = emptyList()

    abstract fun toJsonElement(): JsonElement

    fun toJsonSchema(): String = toJsonElement().toString()
}

data class A2uiStringSchema(
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("type", "string")
        putCommon(this@A2uiStringSchema)
    }
}

data class A2uiNumberSchema(
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("type", "number")
        putCommon(this@A2uiNumberSchema)
    }
}

data class A2uiBooleanSchema(
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("type", "boolean")
        putCommon(this@A2uiBooleanSchema)
    }
}

data class A2uiArraySchema(
    val items: A2uiSchema? = null,
    val minItems: Int = -1,
    val maxItems: Int = -1,
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("type", "array")
        items?.let { put("items", it.toJsonElement()) }
        if (minItems >= 0) put("minItems", minItems)
        if (maxItems >= 0) put("maxItems", maxItems)
        putCommon(this@A2uiArraySchema)
    }
}

data class A2uiObjectSchema(
    val properties: Map<String, A2uiSchema> = emptyMap(),
    val required: Set<String> = emptySet(),
    val isAdditionalPropertiesAllowed: Boolean = true,
    val additionalPropertiesSchema: A2uiSchema? = null,
    /**
     * Keys of which the payload must carry exactly one — JSON Schema's `oneOf` over `required`,
     * which is how the spec says an `action` is either an event or a function call and never
     * both. Empty means the rule does not apply.
     */
    val exactlyOneOf: Set<String> = emptySet(),
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {

    init {
        val missing = (required + exactlyOneOf).filterNot(properties::containsKey)
        require(missing.isEmpty()) { "required keys missing from properties: $missing" }
    }

    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties.mapValues { it.value.toJsonElement() }))
        if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
        if (exactlyOneOf.isNotEmpty()) {
            put(
                "oneOf",
                JsonArray(
                    exactlyOneOf.map { key ->
                        buildJsonObject { put("required", JsonArray(listOf(JsonPrimitive(key)))) }
                    }
                ),
            )
        }
        if (!isAdditionalPropertiesAllowed) {
            put("additionalProperties", false)
        } else {
            additionalPropertiesSchema?.let { put("additionalProperties", it.toJsonElement()) }
        }
        putCommon(this@A2uiObjectSchema)
    }
}

/** Any JSON value. Carries [keywords] — that is how `oneOf` gets a node to hang on. */
data class A2uiAnySchema(
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject { putCommon(this@A2uiAnySchema) }
}

/**
 * A schema with a name, written once into `$defs` and referenced everywhere it is used.
 * Mirrors `androidx.a2ui.model.schema.A2uiCompositeSchema`.
 *
 * Without this every dynamic property would inline the whole literal/binding/call union, and
 * the catalog document handed to the agent would be several times its size — most of it the
 * same paragraph repeated. [description] stays at the use site, where it means something.
 */
data class A2uiNamedSchema(
    val definitionName: String,
    val definition: A2uiSchema,
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("\$ref", "#/\$defs/$definitionName")
        putCommon(this@A2uiNamedSchema)
    }
}

/** `{"$ref": "#/components/Text"}` — resolved against the catalog at validation time. */
data class A2uiRefSchema(
    val ref: String,
    override val description: String? = null,
    override val keywords: List<A2uiSchemaKeyword> = emptyList(),
) : A2uiSchema() {
    override fun toJsonElement(): JsonElement = buildJsonObject {
        put("\$ref", ref)
        putCommon(this@A2uiRefSchema)
    }
}

private fun JsonObjectBuilder.putCommon(schema: A2uiSchema) {
    schema.description?.let { put("description", it) }
    schema.keywords.forEach { it.writeTo(this) }
}
