package com.example.a2uicomposelabs.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a function hands back, so the schema can say where the call is allowed. */
enum class A2uiFunctionReturnType(val value: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object"),
    ANY("any"),
    VOID("void"),
}

/**
 * Which side of the wire actually runs a function. v1.0's `allowedCallers`.
 *
 * The spec says it "specifies which roles are authorized to invoke this function", and this
 * reads invoke as execute — the side that owns the code. So a `rendererOnly` function is one
 * the renderer runs, whether it reached it from a binding or from the agent asking with
 * `callRendererFunction`; an `agentOnly` function is one the renderer cannot run at all and
 * must ask for with `callAgentFunction`.
 *
 * Worth flagging on stage: this whole RPC layer is candidate-stage, and the field was called
 * `callableFrom` in the proposal that introduced it. The schema says `allowedCallers`.
 */
enum class A2uiFunctionCaller(val value: String) {
    /** The default. The renderer owns the code; a binding may call it. */
    RENDERER_ONLY("rendererOnly"),

    /** The agent owns the code. Evaluating it locally yields nothing — ask over the wire. */
    AGENT_ONLY("agentOnly"),

    /** Both sides have an implementation. */
    RENDERER_OR_AGENT("rendererOrAgent");

    /** True when this renderer may run the function itself. */
    val rendererRuns: Boolean get() = this != AGENT_ONLY

    /** True when the function is the agent's to run, reachable with `callAgentFunction`. */
    val agentRuns: Boolean get() = this != RENDERER_ONLY
}

/**
 * What the agent is told about a function, mirroring
 * `androidx.a2ui.model.catalog.A2uiFunctionDefinition`. Like a component definition, it goes
 * into the system prompt and is enforced on the way back in.
 */
data class A2uiFunctionDefinition(
    val name: String,
    val description: String,
    val argumentSchema: A2uiSchema,
    val returnType: A2uiFunctionReturnType,
    /** Defaulted so every function written before v1.0's RPC layer keeps its old meaning. */
    val allowedCallers: A2uiFunctionCaller = A2uiFunctionCaller.RENDERER_ONLY,
)

/**
 * A function the app is willing to run on the agent's behalf.
 *
 * These are the only computations an A2UI message can trigger. There is no expression
 * evaluator and no scripting: a message may name a function from the catalog and pass it
 * arguments, and nothing else. That is the whole reason a declarative protocol can afford to
 * compute anything at all.
 */
interface A2uiFunction {
    val definition: A2uiFunctionDefinition
    fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement?
}

/** Thrown when a function is called with arguments it cannot work with. */
class A2uiFunctionException(message: String) : IllegalArgumentException(message)

/**
 * A function this renderer publishes but does not implement, because the agent implements it.
 *
 * It still belongs in the catalog: that is where the agent reads what may be called, and where
 * the renderer checks that a name is one it published rather than one a model invented. What it
 * has no business doing is running here, so [execute] refuses. The evaluator turns that refusal
 * into null, which is the right answer for a binding — an agent-side value is not something a
 * frame can wait for. Reach it with `A2uiClient.callAgentFunction` instead.
 */
class A2uiAgentFunction(override val definition: A2uiFunctionDefinition) : A2uiFunction {
    init {
        require(definition.allowedCallers.agentRuns) {
            "${definition.name} is declared ${definition.allowedCallers.value}, so the agent " +
                "never runs it — implement it as an ordinary A2uiFunction"
        }
    }

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement? =
        throw A2uiFunctionException(
            "${definition.name} runs on the agent — send callAgentFunction, do not evaluate it here"
        )
}

/**
 * What a function can see while it runs: the surface's data model, and the evaluator, so a
 * function such as `formatString` can resolve bindings and call other functions itself.
 */
class A2uiExecutionContext(
    private val surface: SurfaceState,
    /** Non-null inside a template list item, so relative paths resolve under it. */
    val basePath: String?,
    val evaluator: A2uiDynamicEvaluator,
) {
    fun resolve(rawPath: String): JsonElement? {
        val path = when {
            rawPath.startsWith("/") -> rawPath
            basePath != null -> "$basePath/$rawPath"
            else -> "/$rawPath"
        }
        val value = surface.read(path)
        if (value != null || basePath == null || !rawPath.startsWith("/")) return value

        // Inside a repeated row, an absolute path that finds nothing at the root but does find
        // something on the row itself can only have meant the row: `/name` where `name` was
        // intended. Recovering it is bounded — it never overrides a real root value, because
        // this only runs when the root lookup came back null.
        val onThisRow = surface.read(scopedPath(rawPath)) ?: return null
        surface.scopeRecoveries++
        return onThisRow
    }

    /** `/name` with a base of `/menu/0` is `/menu/0/name`. */
    internal fun scopedPath(absolutePath: String): String = "$basePath$absolutePath"

    fun forBase(basePath: String?): A2uiExecutionContext =
        A2uiExecutionContext(surface, basePath, evaluator)
}

// ---------------------------------------------------------------------------
// Argument access. Functions receive arguments already evaluated, so these only
// have to deal with types, never with bindings.
// ---------------------------------------------------------------------------

object A2uiFunctionArgs {

    fun require(args: Map<String, JsonElement>, key: String): JsonElement =
        args[key]?.takeIf { it !is JsonNull }
            ?: throw A2uiFunctionException("missing required argument '$key'")

    fun string(args: Map<String, JsonElement>, key: String): String {
        val value = require(args, key)
        return value.asString
            ?: (value as? JsonPrimitive)?.content
            ?: throw A2uiFunctionException("argument '$key' must be a string, got $value")
    }

    fun double(args: Map<String, JsonElement>, key: String): Double =
        require(args, key).let { value ->
            value.asNumber
                ?: value.asString?.toDoubleOrNull()
                ?: throw A2uiFunctionException("argument '$key' must be a number, got $value")
        }

    fun int(args: Map<String, JsonElement>, key: String): Int = double(args, key).toInt()

    fun long(args: Map<String, JsonElement>, key: String): Long = double(args, key).toLong()

    fun boolean(args: Map<String, JsonElement>, key: String): Boolean {
        val value = require(args, key)
        return value.asBoolean
            ?: value.asString?.toBooleanStrictOrNull()
            ?: throw A2uiFunctionException("argument '$key' must be a boolean, got $value")
    }

    fun booleanList(args: Map<String, JsonElement>, key: String): List<Boolean> {
        val array = require(args, key) as? JsonArray
            ?: throw A2uiFunctionException("argument '$key' must be an array")
        return array.map { element ->
            element.asBoolean
                ?: throw A2uiFunctionException("argument '$key' must hold booleans, got $element")
        }
    }

    fun optionalString(args: Map<String, JsonElement>, key: String): String? =
        if (args[key] == null || args[key] is JsonNull) null else string(args, key)

    fun optionalDouble(args: Map<String, JsonElement>, key: String): Double? =
        if (args[key] == null || args[key] is JsonNull) null else double(args, key)

    fun optionalInt(args: Map<String, JsonElement>, key: String): Int? =
        optionalDouble(args, key)?.toInt()

    fun optionalBoolean(args: Map<String, JsonElement>, key: String): Boolean? =
        if (args[key] == null || args[key] is JsonNull) null else boolean(args, key)

    /** True when a value counts as "present" for the `required` check. */
    fun isPresent(value: JsonElement?): Boolean = when (value) {
        null, is JsonNull -> false
        is JsonArray -> value.isNotEmpty()
        is JsonObject -> value.isNotEmpty()
        is JsonPrimitive -> if (value.isString) value.content.isNotEmpty() else true
    }
}
