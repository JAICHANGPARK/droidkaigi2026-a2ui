package com.example.a2uicomposelabs.a2ui.engine

import com.example.a2uicomposelabs.a2ui.model.A2uiAnySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiArraySchema
import com.example.a2uicomposelabs.a2ui.model.A2uiBooleanSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.model.A2uiNamedSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiNumberSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiObjectSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiRefSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiSchema
import com.example.a2uicomposelabs.a2ui.model.A2uiSchemaKeyword
import com.example.a2uicomposelabs.a2ui.model.A2uiStringSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Thrown when a payload violates the catalog schema. [path] points at the offending value. */
class A2uiValidationException(message: String, val path: String) :
    IllegalArgumentException("$message (at $path)")

/**
 * Validates raw agent JSON against [A2uiSchema] before it is allowed to touch surface state.
 * Mirrors `androidx.a2ui.engine.schema.A2uiCoreSchemaValidator`, but walks [JsonElement]
 * directly instead of `Map`/`List`, because that is what our parser already produces.
 *
 * This is the "reject at the door" layer with teeth: a component whose properties do not
 * match its declared schema never reaches [SurfaceState].
 */
class A2uiSchemaValidator(private val catalog: A2uiCatalog? = null) {

    @Throws(A2uiValidationException::class)
    fun validate(payload: JsonElement?, schema: A2uiSchema, basePath: String = "/") {
        for (keyword in schema.keywords) {
            when (keyword) {
                is A2uiSchemaKeyword.OneOf -> validateOneOf(payload, keyword.schemas, basePath)
                is A2uiSchemaKeyword.Enum -> validateEnum(payload, keyword.values, basePath)
                is A2uiSchemaKeyword.Const -> validateConst(payload, keyword.value, basePath)
                is A2uiSchemaKeyword.Default -> Unit // annotation keyword
            }
        }
        when (schema) {
            is A2uiObjectSchema -> validateObject(payload, schema, basePath)
            is A2uiArraySchema -> validateArray(payload, schema, basePath)
            is A2uiStringSchema -> requireType(payload?.asString != null, "a string", payload, basePath)
            is A2uiNumberSchema -> requireType(payload?.asNumber != null, "a number", payload, basePath)
            is A2uiBooleanSchema ->
                requireType(payload?.asBoolean != null, "a boolean", payload, basePath)
            is A2uiAnySchema -> Unit // any valid JSON is allowed
            // The definition is right here in the object graph, so a named schema needs no
            // lookup: only the serialized document uses $defs.
            is A2uiNamedSchema -> validate(payload, schema.definition, basePath)
            is A2uiRefSchema -> validateRef(payload, schema, basePath)
        }
    }

    private fun validateObject(payload: JsonElement?, schema: A2uiObjectSchema, path: String) {
        val obj = payload as? JsonObject
            ?: throw A2uiValidationException("Expected an object, but got: ${render(payload)}", path)
        for (required in schema.required) {
            if (required !in obj) {
                throw A2uiValidationException("Missing required property '$required'", path)
            }
        }
        if (schema.exactlyOneOf.isNotEmpty()) {
            val present = schema.exactlyOneOf.filter { it in obj }
            if (present.size != 1) {
                throw A2uiValidationException(
                    "Expected exactly one of ${schema.exactlyOneOf.joinToString(", ")}, but " +
                        if (present.isEmpty()) "found none" else "found ${present.joinToString(", ")}",
                    path,
                )
            }
        }
        for ((key, value) in obj) {
            val propertySchema = schema.properties[key]
            when {
                propertySchema != null -> validate(value, propertySchema, concat(path, key))
                !schema.isAdditionalPropertiesAllowed ->
                    throw A2uiValidationException("Additional property '$key' not allowed", path)
                else -> schema.additionalPropertiesSchema?.let { validate(value, it, concat(path, key)) }
            }
        }
    }

    private fun validateArray(payload: JsonElement?, schema: A2uiArraySchema, path: String) {
        val array = payload as? JsonArray
            ?: throw A2uiValidationException("Expected an array, but got: ${render(payload)}", path)
        if (schema.minItems >= 0 && array.size < schema.minItems) {
            throw A2uiValidationException(
                "Array has ${array.size} items, but minimum required is ${schema.minItems}",
                path,
            )
        }
        if (schema.maxItems >= 0 && array.size > schema.maxItems) {
            throw A2uiValidationException(
                "Array has ${array.size} items, but maximum allowed is ${schema.maxItems}",
                path,
            )
        }
        schema.items?.let { items ->
            array.forEachIndexed { index, item -> validate(item, items, concat(path, index.toString())) }
        }
    }

    /** Exactly one subschema must match — the same rule the official validator applies. */
    private fun validateOneOf(payload: JsonElement?, schemas: List<A2uiSchema>, path: String) {
        val matches = schemas.count { schema ->
            runCatching { validate(payload, schema, path) }.isSuccess
        }
        if (matches != 1) {
            throw A2uiValidationException(
                "Value must match exactly one subschema, but matched $matches: ${render(payload)}",
                path,
            )
        }
    }

    private fun validateEnum(payload: JsonElement?, values: List<JsonElement>, path: String) {
        if (values.none { areEqual(payload, it) }) {
            val allowed = values.mapNotNull { it.asString }.joinToString("|")
            throw A2uiValidationException(
                "Value ${render(payload)} is not one of [$allowed]",
                path,
            )
        }
    }

    private fun validateConst(payload: JsonElement?, value: JsonElement, path: String) {
        if (!areEqual(payload, value)) {
            throw A2uiValidationException(
                "Expected const ${render(value)}, but got: ${render(payload)}",
                path,
            )
        }
    }

    private fun validateRef(payload: JsonElement?, schema: A2uiRefSchema, path: String) {
        val fragment = schema.ref.substringAfter('#').removePrefix("/")
        if (!fragment.startsWith(COMPONENTS_PREFIX)) {
            throw A2uiValidationException("Unsupported reference '${schema.ref}'", path)
        }
        val name = fragment.substringAfter(COMPONENTS_PREFIX)
        val definition = catalog?.components?.get(name)
            ?: throw A2uiValidationException(
                "Component '$name' not found in catalog for reference '${schema.ref}'",
                path,
            )
        validate(payload, definition.propertySchema, path)
    }

    private fun requireType(ok: Boolean, expected: String, payload: JsonElement?, path: String) {
        if (!ok) throw A2uiValidationException("Expected $expected, but got: ${render(payload)}", path)
    }

    private fun concat(path: String, segment: String): String =
        if (path.endsWith("/")) "$path$segment" else "$path/$segment"

    /** JSON-aware equality: `1` and `1.0` are the same number, unlike JsonPrimitive equality. */
    private fun areEqual(a: JsonElement?, b: JsonElement?): Boolean {
        val an = a?.asNumber
        val bn = b?.asNumber
        return if (an != null && bn != null) an == bn else a == b
    }

    private fun render(payload: JsonElement?): String = when (payload) {
        null -> "nothing"
        else -> payload.toString().take(60)
    }

    private companion object {
        const val COMPONENTS_PREFIX = "components/"
    }
}

// JSON type tests, written once so every call site agrees on what "a string" means.
// JsonNull is a JsonPrimitive, so each of these correctly returns null for it.
internal val JsonElement.asString: String?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

internal val JsonElement.asNumber: Double?
    get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

internal val JsonElement.asBoolean: Boolean?
    get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
