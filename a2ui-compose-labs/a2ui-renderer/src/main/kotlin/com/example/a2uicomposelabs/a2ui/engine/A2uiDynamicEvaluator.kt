package com.example.a2uicomposelabs.a2ui.engine

import com.example.a2uicomposelabs.a2ui.model.A2uiExecutionContext
import com.example.a2uicomposelabs.a2ui.model.A2uiFunction
import com.example.a2uicomposelabs.a2ui.model.A2uiFunctionException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Resolves a property value all the way down, mirroring
 * `androidx.a2ui.engine.model.A2uiCoreDynamicEvaluator`.
 *
 * A property may be three things, and they nest:
 *  - a literal — `"Contact us"`, `42`, `true`
 *  - a binding — `{"path": "/user/name"}`
 *  - a call — `{"call": "formatCurrency", "args": {"value": {"path": "/total"}, "currency": "JPY"}}`
 *
 * Arguments are themselves evaluated before the function runs, so a call may take a binding,
 * or another call. Everything else — plain objects and arrays — is walked so nested bindings
 * inside them resolve too.
 *
 * Only functions the app registered in the catalog can be named. An unknown name resolves to
 * null rather than throwing, so one bad call does not blank the surface around it.
 */
class A2uiDynamicEvaluator(functions: List<A2uiFunction> = emptyList()) {

    private val byName: Map<String, A2uiFunction> = functions.associateBy { it.definition.name }

    val names: Set<String> get() = byName.keys

    fun evaluate(
        payload: JsonElement?,
        context: A2uiExecutionContext,
        depth: Int = 0,
    ): JsonElement? {
        if (payload == null || payload is JsonNull) return null
        // A call whose argument is a call whose argument is a call… is still bounded.
        if (depth > MAX_DEPTH) return null
        return when (payload) {
            is JsonPrimitive -> payload
            is JsonArray -> JsonArray(payload.mapNotNull { evaluate(it, context, depth + 1) })
            is JsonObject -> evaluateObject(payload, context, depth)
        }
    }

    private fun evaluateObject(
        payload: JsonObject,
        context: A2uiExecutionContext,
        depth: Int,
    ): JsonElement? {
        payload.bindingPath()?.let { return context.resolve(it) }
        payload.callName()?.let { name -> return invoke(name, payload, context, depth) }
        // A plain object: keep its shape, resolve anything bound inside it.
        return JsonObject(
            payload.mapValues { (_, value) -> evaluate(value, context, depth + 1) ?: JsonNull }
        )
    }

    private fun invoke(
        name: String,
        payload: JsonObject,
        context: A2uiExecutionContext,
        depth: Int,
    ): JsonElement? {
        val function = byName[name] ?: return null
        val rawArgs = payload["args"] as? JsonObject ?: JsonObject(emptyMap())
        val args = rawArgs.mapValues { (_, value) -> evaluate(value, context, depth + 1) ?: JsonNull }
        return try {
            function.execute(args, context)
        } catch (e: A2uiFunctionException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        /** Matches the render depth cap: agent-supplied nesting is never unbounded. */
        const val MAX_DEPTH = 24
    }
}

/** `{"path": "..."}` and nothing else — an object with any other key is not a binding. */
internal fun JsonObject.bindingPath(): String? =
    if (size == 1) (this["path"] as? JsonPrimitive)?.contentOrNull else null

internal fun JsonObject.callName(): String? = (this["call"] as? JsonPrimitive)?.contentOrNull
