package com.example.a2uicomposelabs.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** How hard a failing check is. The spec defaults an unstated severity to `error`. */
enum class A2uiCheckSeverity {
    ERROR,
    WARNING;

    companion object {
        fun of(raw: String?): A2uiCheckSeverity =
            if (raw.equals("warning", ignoreCase = true)) WARNING else ERROR
    }
}

/** One check that came back false, and the message the agent wrote for it. */
data class A2uiCheckFailure(
    val componentId: String,
    val message: String,
    val severity: A2uiCheckSeverity,
)

/**
 * Resolves component properties. The spec gives a property three forms, and they nest:
 * a literal, a `{"path": "/..."}` binding into the surface data model, or a
 * `{"call": "..."}` invocation of a catalog function. [A2uiDynamicEvaluator] walks all three.
 *
 * Reads go through snapshot state, so Compose tracks them and recomposes only the components
 * bound to a changed path.
 */
class BindingScope(
    private val surface: SurfaceState,
    private val onAction: (A2uiAction) -> Unit,
    /** Non-null while rendering one item of a template list: relative paths resolve under it. */
    private val itemBase: String? = null,
    /** The catalog's functions. The default resolves literals and bindings only. */
    private val evaluator: A2uiDynamicEvaluator = A2uiDynamicEvaluator(),
) {

    private val context: A2uiExecutionContext
        get() = A2uiExecutionContext(surface, itemBase, evaluator)

    /**
     * Child Scope for template lists (spec: "a relative path `firstName` inside a template
     * iterating over `/users` resolves to `/users/0/firstName`, `/users/1/firstName`, …").
     */
    fun forItem(basePath: String, index: Int): BindingScope =
        BindingScope(surface, onAction, "$basePath/$index", evaluator)

    fun arraySize(path: String): Int = (surface.read(path) as? JsonArray)?.size ?: 0

    /** Resolves a raw path string against the current scope (absolute paths pass through). */
    fun resolve(raw: String): String =
        if (raw.startsWith("/") || itemBase == null) raw else "$itemBase/$raw"

    /** Fully resolves a property, whichever of the three forms it takes. */
    fun read(prop: JsonElement?): JsonElement? = evaluator.evaluate(prop, context)

    fun readString(prop: JsonElement?): String =
        (read(prop) as? JsonPrimitive)?.contentOrNull.orEmpty()

    fun readBoolean(prop: JsonElement?): Boolean = read(prop)?.asBoolean ?: false

    fun readFloat(prop: JsonElement?, default: Float = 0f): Float =
        read(prop)?.asNumber?.toFloat() ?: default

    /** Numbers, for components that plot an array rather than display one value. */
    fun readFloatList(prop: JsonElement?): List<Float> =
        (read(prop) as? JsonArray)?.mapNotNull { it.asNumber?.toFloat() } ?: emptyList()

    fun readStringList(prop: JsonElement?): List<String> =
        (read(prop) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

    /** Two-way binding: input components write back to their bound path. Local only — nothing goes to the network per keystroke. */
    fun write(prop: JsonElement?, value: JsonElement) {
        path(prop)?.let { surface.updateData(it, value) }
    }

    fun children(props: JsonObject): List<String> =
        (props["children"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

    /**
     * Runs the component's `checks` and returns the messages of the ones that failed.
     *
     * Validation stays declarative: the agent says *what* must hold ("this must be an email
     * address, at least 3 characters"), and the app's own functions decide whether it does.
     * The agent never runs code, and never gets to declare the form valid itself.
     */
    fun checkFailures(node: ComponentNode): List<A2uiCheckFailure> {
        val checks = node.props["checks"] as? JsonArray ?: return emptyList()
        return checks.mapNotNull { entry ->
            val rule = entry as? JsonObject ?: return@mapNotNull null
            // A condition that cannot be evaluated is treated as passing, so a broken rule
            // never silently locks the user out of their own form.
            val passed = read(rule["condition"])?.asBoolean ?: true
            if (passed) return@mapNotNull null
            val message = (rule["message"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            A2uiCheckFailure(
                componentId = node.id,
                message = message,
                severity = A2uiCheckSeverity.of((rule["severity"] as? JsonPrimitive)?.contentOrNull),
            )
        }
    }

    /** True when every check on the node passes, or there are none. */
    fun isValid(node: ComponentNode): Boolean =
        checkFailures(node).none { it.severity == A2uiCheckSeverity.ERROR }

    /**
     * Every failing check anywhere on this surface.
     *
     * A Button reads this to decide whether it may be pressed, which is why the check lives on
     * the input and the consequence lands on the button: the agent describes what a good answer
     * looks like, and the renderer works out whether the form as a whole is submittable.
     */
    fun surfaceCheckFailures(): List<A2uiCheckFailure> =
        surface.components.values.flatMap(::checkFailures)

    /** False while any error-severity check on the surface is failing. */
    fun surfaceIsSubmittable(): Boolean =
        surfaceCheckFailures().none { it.severity == A2uiCheckSeverity.ERROR }

    /**
     * Builds and dispatches an action; every `{"path"}` and `{"call"}` in `context` is
     * resolved before sending.
     *
     * [extra] is what the component itself contributes — which slice was tapped, which row was
     * swiped. The agent cannot express that in the message, because it does not know yet.
     * Mirrors `A2uiComponentScope.dispatchAction(actionPayload)` in androidx.a2ui.
     */
    fun dispatchAction(node: ComponentNode, extra: Map<String, JsonElement> = emptyMap()) {
        val spec = node.props["action"] as? JsonObject ?: return

        // The v1.0 functionCall form: the button is asking the agent something rather than
        // telling it something. Arguments are resolved here, exactly as an event's context is,
        // so what leaves the device is values the user actually saw and never a path.
        (spec["functionCall"] as? JsonObject)?.let { call ->
            val name = (call["call"] as? JsonPrimitive)?.contentOrNull ?: return
            val args = (call["args"] as? JsonObject ?: JsonObject(emptyMap()))
            val resolvedArgs = buildJsonObject {
                args.forEach { (key, value) -> put(key, read(value) ?: value) }
                extra.forEach { (key, value) -> put(key, value) }
            }
            val resolvedCall = buildJsonObject {
                call.forEach { (key, value) -> if (key != "args") put(key, value) }
                put("args", resolvedArgs)
            }
            onAction(
                A2uiAction(
                    name = name,
                    surfaceId = surface.surfaceId,
                    sourceComponentId = node.id,
                    timestamp = isoNow(),
                    context = resolvedArgs,
                    functionCall = resolvedCall,
                )
            )
            return
        }

        val name = (spec["name"] as? JsonPrimitive)?.contentOrNull ?: "action"
        val contextSpec = spec["context"] as? JsonObject ?: JsonObject(emptyMap())
        val resolved = buildJsonObject {
            contextSpec.forEach { (key, value) -> put(key, read(value) ?: value) }
            // The component has the last word: it knows what the user actually touched.
            extra.forEach { (key, value) -> put(key, value) }
        }
        onAction(A2uiAction(name, surface.surfaceId, node.id, isoNow(), resolved))
    }

    private fun path(prop: JsonElement?): String? {
        val raw = ((prop as? JsonObject)?.get("path") as? JsonPrimitive)?.contentOrNull ?: return null
        // Absolute paths escape to the Root Scope; relative paths bind to the current item.
        if (!raw.startsWith("/") || itemBase == null) {
            return if (itemBase == null) raw else "$itemBase/$raw"
        }
        // The same recovery the reader does, and it matters more here: a stepper that writes to
        // `/quantity` instead of this row's `quantity` makes every row share one number. Only
        // when the root has no such field and this row does.
        if (surface.read(raw) == null && surface.read("$itemBase$raw") != null) {
            surface.scopeRecoveries++
            return "$itemBase$raw"
        }
        return raw
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
