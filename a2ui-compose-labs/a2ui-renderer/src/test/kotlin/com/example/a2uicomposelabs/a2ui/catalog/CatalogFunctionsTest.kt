package com.example.a2uicomposelabs.a2ui.catalog

import com.example.a2uicomposelabs.a2ui.engine.A2uiDynamicEvaluator
import com.example.a2uicomposelabs.a2ui.engine.A2uiSchemaValidator
import com.example.a2uicomposelabs.a2ui.engine.asBoolean
import com.example.a2uicomposelabs.a2ui.model.A2uiExecutionContext
import com.example.a2uicomposelabs.a2ui.model.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.model.ComponentNode
import com.example.a2uicomposelabs.a2ui.runtime.A2uiCheckSeverity
import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import com.example.a2uicomposelabs.a2ui.runtime.BindingScope
import com.example.a2uicomposelabs.a2ui.runtime.SurfaceState
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog function engine: the third form a property can take, and the only computation
 * an A2UI message is allowed to trigger.
 */
class CatalogFunctionsTest {

    private val opened = mutableListOf<String>()

    private val functions = basicCatalogFunctions(
        urlOpener = { url -> opened += url; true },
        localeProvider = { Locale.US },
    )

    private val evaluator = A2uiDynamicEvaluator(functions)

    private val surface = SurfaceState("s").apply {
        setDataModel(
            Json.parseToJsonElement(
                """{"name":"Jane","email":"jane@example.com","total":12345.5,
                    "count":3,"agreed":true,"when":1786973889,"tags":["a","b"],"blank":""}"""
            ) as JsonObject
        )
    }

    private val context get() = A2uiExecutionContext(surface, null, evaluator)

    private fun call(json: String) = evaluator.evaluate(Json.parseToJsonElement(json), context)

    private fun bool(json: String) = call(json)?.asBoolean
    private fun text(json: String) = (call(json) as? JsonPrimitive)?.content

    // --- logic ----------------------------------------------------------------

    @Test
    fun `and is true only when every value is`() {
        assertEquals(true, bool("""{"call":"and","args":{"values":[true,true]}}"""))
        assertEquals(false, bool("""{"call":"and","args":{"values":[true,false]}}"""))
    }

    @Test
    fun `or is true when any value is`() {
        assertEquals(true, bool("""{"call":"or","args":{"values":[false,true]}}"""))
        assertEquals(false, bool("""{"call":"or","args":{"values":[false,false]}}"""))
    }

    @Test
    fun `not negates`() {
        assertEquals(false, bool("""{"call":"not","args":{"value":true}}"""))
    }

    // --- validation -----------------------------------------------------------

    @Test
    fun `required rejects empty and accepts filled`() {
        assertEquals(false, bool("""{"call":"required","args":{"value":{"path":"/blank"}}}"""))
        assertEquals(true, bool("""{"call":"required","args":{"value":{"path":"/name"}}}"""))
        assertEquals(true, bool("""{"call":"required","args":{"value":{"path":"/tags"}}}"""))
    }

    @Test
    fun `required rejects an empty array`() {
        assertEquals(false, bool("""{"call":"required","args":{"value":[]}}"""))
    }

    @Test
    fun `length honours min and max`() {
        assertEquals(true, bool("""{"call":"length","args":{"value":"Jane","min":2,"max":10}}"""))
        assertEquals(false, bool("""{"call":"length","args":{"value":"Jane","min":5}}"""))
        assertEquals(false, bool("""{"call":"length","args":{"value":"Jane","max":3}}"""))
    }

    @Test
    fun `numeric honours min and max`() {
        assertEquals(true, bool("""{"call":"numeric","args":{"value":3,"min":1,"max":5}}"""))
        assertEquals(false, bool("""{"call":"numeric","args":{"value":9,"max":5}}"""))
    }

    @Test
    fun `email accepts an address and rejects a word`() {
        assertEquals(true, bool("""{"call":"email","args":{"value":{"path":"/email"}}}"""))
        assertEquals(false, bool("""{"call":"email","args":{"value":"jane"}}"""))
    }

    @Test
    fun `regex matches a pattern`() {
        assertEquals(true, bool("""{"call":"regex","args":{"value":"AB-12","pattern":"[A-Z]{2}-\\d{2}"}}"""))
        assertEquals(false, bool("""{"call":"regex","args":{"value":"nope","pattern":"[A-Z]{2}-\\d{2}"}}"""))
    }

    @Test
    fun `an invalid regex pattern does not crash the surface`() {
        assertNull(call("""{"call":"regex","args":{"value":"x","pattern":"([unclosed"}}"""))
    }

    // --- formatting -----------------------------------------------------------

    @Test
    fun `formatNumber applies decimals and grouping`() {
        assertEquals("12,346", text("""{"call":"formatNumber","args":{"value":12345.5}}"""))
        assertEquals("12345.50", text("""{"call":"formatNumber","args":{"value":12345.5,"decimals":2,"grouping":false}}"""))
    }

    @Test
    fun `formatCurrency uses the requested currency`() {
        val yen = text("""{"call":"formatCurrency","args":{"value":{"path":"/total"},"currency":"JPY"}}""")
        assertTrue(yen.orEmpty(), yen.orEmpty().contains("12,3"))
        assertTrue(yen.orEmpty(), yen.orEmpty().contains("¥"))
    }

    @Test
    fun `formatDate renders iso from a seconds timestamp`() {
        assertEquals(
            "2026-08-17T13:38:09Z",
            text("""{"call":"formatDate","args":{"value":{"path":"/when"},"format":"iso"}}"""),
        )
    }

    @Test
    fun `formatDate renders a pattern`() {
        assertEquals(
            "2026-08-17",
            text("""{"call":"formatDate","args":{"value":1786973889000,"format":"yyyy-MM-dd"}}"""),
        )
    }

    @Test
    fun `pluralize picks one or other`() {
        val template = """{"call":"pluralize","args":{"value":%s,"one":"1 item","other":"many items"}}"""
        assertEquals("1 item", text(template.format("1")))
        assertEquals("many items", text(template.format("4")))
    }

    // --- formatString: the little expression language --------------------------

    @Test
    fun `formatString interpolates a path`() {
        assertEquals(
            "Hi Jane",
            text("""{"call":"formatString","args":{"value":"Hi ${'$'}{/name}"}}"""),
        )
    }

    @Test
    fun `formatString renders whole numbers without a decimal tail`() {
        assertEquals(
            "count 3",
            text("""{"call":"formatString","args":{"value":"count ${'$'}{/count}"}}"""),
        )
    }

    @Test
    fun `formatString nests a call with named arguments`() {
        val rendered = text(
            """{"call":"formatString","args":{"value":
               "Total: ${'$'}{formatCurrency(value:${'$'}{/total}, currency:'JPY')}"}}"""
        )
        assertTrue(rendered.orEmpty(), rendered.orEmpty().startsWith("Total: ¥"))
    }

    @Test
    fun `formatString leaves an escaped hole alone`() {
        assertEquals(
            "literal \${/name}",
            text("""{"call":"formatString","args":{"value":"literal \\${'$'}{/name}"}}"""),
        )
    }

    @Test
    fun `formatString renders a missing path as nothing`() {
        assertEquals(
            "[]",
            text("""{"call":"formatString","args":{"value":"[${'$'}{/nope}]"}}"""),
        )
    }

    // --- the action function ---------------------------------------------------

    @Test
    fun `openUrl passes an https url to the opener`() {
        call("""{"call":"openUrl","args":{"url":"https://example.com/a"}}""")
        assertEquals(listOf("https://example.com/a"), opened)
    }

    @Test
    fun `openUrl refuses anything the url policy rejects`() {
        call("""{"call":"openUrl","args":{"url":"http://example.com/a"}}""")
        assertTrue(opened.isEmpty())
    }

    // --- the evaluator itself ---------------------------------------------------

    @Test
    fun `arguments are themselves evaluated, so calls nest`() {
        val nested = """
            {"call":"and","args":{"values":[
              {"call":"required","args":{"value":{"path":"/name"}}},
              {"call":"email","args":{"value":{"path":"/email"}}}
            ]}}
        """
        assertEquals(true, bool(nested))
    }

    @Test
    fun `an unknown function resolves to nothing rather than throwing`() {
        assertNull(call("""{"call":"wipeDatabase","args":{}}"""))
    }

    @Test
    fun `a plain object keeps its shape while its bindings resolve`() {
        val resolved = call("""{"a":{"path":"/name"},"b":"literal"}""") as JsonObject
        assertEquals("Jane", (resolved["a"] as JsonPrimitive).content)
        assertEquals("literal", (resolved["b"] as JsonPrimitive).content)
    }

    @Test
    fun `an object with extra keys beside path is not a binding`() {
        val resolved = call("""{"path":"/name","other":1}""") as JsonObject
        assertTrue(resolved.containsKey("other"))
    }

    // --- checks ------------------------------------------------------------------

    @Test
    fun `a failing check reports its message and a passing one stays quiet`() {
        val scope = BindingScope(surface, {}, evaluator = evaluator)
        val node = ComponentNode(
            id = "email_field",
            component = "TextField",
            props = Json.parseToJsonElement(
                """{"text":{"path":"/email"},"checks":[
                     {"condition":{"call":"required","args":{"value":{"path":"/blank"}}},
                      "message":"Please fill this in"},
                     {"condition":{"call":"email","args":{"value":{"path":"/email"}}},
                      "message":"Not an email"}]}"""
            ) as JsonObject,
        )
        assertEquals(listOf("Please fill this in"), scope.checkFailures(node).map { it.message })
        assertFalse(scope.isValid(node))
    }

    @Test
    fun `an unstated severity is an error, as the spec defaults it`() {
        val scope = BindingScope(surface, {}, evaluator = evaluator)
        val node = checkedField("""{"condition":false,"message":"nope"}""")
        assertEquals(A2uiCheckSeverity.ERROR, scope.checkFailures(node).single().severity)
    }

    @Test
    fun `a warning is reported but does not make the node invalid`() {
        val scope = BindingScope(surface, {}, evaluator = evaluator)
        val node = checkedField("""{"condition":false,"message":"heads up","severity":"warning"}""")
        assertEquals(1, scope.checkFailures(node).size)
        assertTrue(scope.isValid(node))
    }

    // --- checks disable the surface's buttons ------------------------------------

    @Test
    fun `a failing error check makes the surface unsubmittable`() {
        val client = A2uiClient(BasicCatalogSchema)
        client.apply(SURFACE_WITH_CHECK)
        val surface = client.surfaces.getValue("form")
        val scope = BindingScope(surface, {}, evaluator = BasicCatalogSchema.evaluator)
        // "/blank" is empty, so the required check fails and the button must stay disabled.
        assertFalse(scope.surfaceIsSubmittable())
        assertEquals(listOf("Name is required"), scope.surfaceCheckFailures().map { it.message })
    }

    @Test
    fun `filling the field in makes the surface submittable again`() {
        val client = A2uiClient(BasicCatalogSchema)
        client.apply(SURFACE_WITH_CHECK)
        val surface = client.surfaces.getValue("form")
        val scope = BindingScope(surface, {}, evaluator = BasicCatalogSchema.evaluator)
        surface.updateData("/who", JsonPrimitive("Jane"))
        assertTrue(scope.surfaceIsSubmittable())
        assertTrue(scope.surfaceCheckFailures().isEmpty())
    }

    @Test
    fun `a warning never blocks submission`() {
        val client = A2uiClient(BasicCatalogSchema)
        client.apply(SURFACE_WITH_CHECK.replace("\"message\":\"Name is required\"", "\"message\":\"Short\",\"severity\":\"warning\""))
        val surface = client.surfaces.getValue("form")
        val scope = BindingScope(surface, {}, evaluator = BasicCatalogSchema.evaluator)
        assertEquals(1, scope.surfaceCheckFailures().size)
        assertTrue(scope.surfaceIsSubmittable())
    }

    private fun checkedField(rule: String) = ComponentNode(
        id = "field",
        component = "TextField",
        props = Json.parseToJsonElement("""{"text":{"path":"/blank"},"checks":[$rule]}""") as JsonObject,
    )

    @Test
    fun `a component with no checks is valid`() {
        val scope = BindingScope(surface, {}, evaluator = evaluator)
        val node = ComponentNode("t", "Text", Json.parseToJsonElement("""{"text":"hi"}""") as JsonObject)
        assertTrue(scope.isValid(node))
    }

    // --- schema and catalog -------------------------------------------------------

    @Test
    fun `the basic catalog publishes all fourteen functions`() {
        assertEquals(14, BasicCatalogSchema.functions.size)
        assertEquals(
            listOf(
                "and", "email", "formatCurrency", "formatDate", "formatNumber", "formatString",
                "length", "not", "numeric", "openUrl", "or", "pluralize", "regex", "required",
            ),
            BasicCatalogSchema.functions.map { it.definition.name }.sorted(),
        )
    }

    @Test
    fun `the catalog schema document describes the functions to the agent`() {
        val schema = BasicCatalogSchema.toJsonSchema()
        assertTrue(schema.contains("\"functions\""))
        assertTrue(schema.contains("\"formatCurrency\""))
        assertTrue(schema.contains("\"anyFunction\""))
    }

    @Test
    fun `a string property accepts a call that returns a string`() {
        val validator = A2uiSchemaValidator(BasicCatalogSchema)
        val definition = BasicCatalogSchema.components.getValue("Text")
        validator.validate(
            Json.parseToJsonElement(
                """{"text":{"call":"formatString","args":{"value":"hi"},"returnType":"string"}}"""
            ),
            definition.propertySchema,
        )
    }

    @Test
    fun `a string property rejects a call declared to return a boolean`() {
        val validator = A2uiSchemaValidator(BasicCatalogSchema)
        val definition = BasicCatalogSchema.components.getValue("Text")
        val failed = runCatching {
            validator.validate(
                Json.parseToJsonElement("""{"text":{"call":"required","returnType":"boolean"}}"""),
                definition.propertySchema,
            )
        }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `the client rejects a component that calls a function nobody published`() {
        val client = A2uiClient(BasicCatalogSchema)
        client.apply(
            """{"version":"v1.0","createSurface":{"surfaceId":"s","components":[
                 {"id":"root","component":"Text","text":{"call":"exfiltrate","args":{}}}]}}"""
        )
        assertTrue(client.errors.any { it.reason.contains("unknown function 'exfiltrate'") })
        assertEquals(0, client.surfaces.getValue("s").components.size)
    }

    private companion object {
        /** A name field that must be filled in, and a submit button gated on it. */
        const val SURFACE_WITH_CHECK = """
            {"version":"v1.0","createSurface":{"surfaceId":"form","components":[
              {"id":"root","component":"Card","children":["name","submit"]},
              {"id":"name","component":"TextField","text":{"path":"/who"},
               "checks":[{"condition":{"call":"required","args":{"value":{"path":"/who"}}},
                          "message":"Name is required"}]},
              {"id":"submit","component":"Button","label":"Send",
               "action":{"name":"send","context":{"who":{"path":"/who"}}}}
            ],"dataModel":{"who":""}}}
        """
    }
}
