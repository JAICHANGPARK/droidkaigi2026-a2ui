package com.example.a2uicomposelabs.a2ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class A2uiSchemaValidatorTest {

    private val validator = A2uiSchemaValidator(BasicCatalogSchema)

    private fun props(json: String) = Json.parseToJsonElement(json) as JsonObject

    private fun validate(component: String, json: String) {
        val definition = requireNotNull(BasicCatalogSchema.components[component])
        validator.validate(props(json), definition.propertySchema, "/x")
    }

    private fun expectRejection(component: String, json: String): String {
        try {
            validate(component, json)
        } catch (e: A2uiValidationException) {
            return e.message.orEmpty()
        }
        fail("expected $component $json to be rejected")
        error("unreachable")
    }

    // --- dynamic properties: literal OR binding, nothing else ------------------

    @Test
    fun `text accepts a literal`() {
        validate("Text", """{"text":"hello"}""")
    }

    @Test
    fun `text accepts a data binding`() {
        validate("Text", """{"text":{"path":"/greeting"}}""")
    }

    @Test
    fun `text rejects a number`() {
        assertTrue(expectRejection("Text", """{"text":42}""").contains("exactly one subschema"))
    }

    @Test
    fun `text rejects a binding with no path`() {
        assertTrue(expectRejection("Text", """{"text":{"pointer":"/a"}}""").contains("subschema"))
    }

    @Test
    fun `missing required property is rejected`() {
        assertTrue(expectRejection("Text", """{}""").contains("Missing required property 'text'"))
    }

    // --- the allowlist is exact ------------------------------------------------

    @Test
    fun `invented property is rejected`() {
        val message = expectRejection("Text", """{"text":"hi","onClick":"fetch('/x')"}""")
        assertTrue(message.contains("Additional property 'onClick' not allowed"))
    }

    @Test
    fun `common properties are allowed on every component`() {
        validate("Text", """{"text":"hi","weight":1,"accessibility":{"label":"greeting"}}""")
    }

    // --- two-way properties must be bindings -----------------------------------

    @Test
    fun `text field accepts a bound path`() {
        validate("TextField", """{"label":"Name","text":{"path":"/name"}}""")
    }

    @Test
    fun `text field rejects a literal it could never write back to`() {
        val message = expectRejection("TextField", """{"label":"Name","text":"Jane"}""")
        assertTrue(message.contains("Expected an object"))
    }

    // --- enums ------------------------------------------------------------------

    @Test
    fun `image fit accepts a spec value`() {
        validate("Image", """{"url":"https://example.com/a.png","fit":"cover"}""")
    }

    @Test
    fun `image fit rejects an invented value`() {
        val message = expectRejection("Image", """{"url":"https://x/a.png","fit":"stretch"}""")
        assertTrue(message.contains("is not one of"))
    }

    // --- error paths point at the offending value -------------------------------

    @Test
    fun `error path names the component and property`() {
        val definition = requireNotNull(BasicCatalogSchema.components["Slider"])
        try {
            validator.validate(props("""{"value":"nope"}"""), definition.propertySchema, "/tempo")
            fail("expected rejection")
        } catch (e: A2uiValidationException) {
            assertEquals("/tempo/value", e.path)
        }
    }

    // --- lists: fixed children or a repeat template ------------------------------

    @Test
    fun `list accepts a fixed child array`() {
        validate("List", """{"children":["a","b"]}""")
    }

    @Test
    fun `list accepts a repeat template`() {
        validate("List", """{"children":{"componentId":"row","path":"/items"}}""")
    }

    @Test
    fun `list rejects a half written template`() {
        assertTrue(expectRejection("List", """{"children":{"componentId":"row"}}""").isNotEmpty())
    }

    // --- catalog serialization ----------------------------------------------------

    @Test
    fun `catalog serializes to a json schema document naming every component`() {
        val schema = BasicCatalogSchema.toJsonSchema()
        BasicCatalogSchema.components.keys.forEach { name ->
            assertTrue("$name missing from schema", schema.contains("\"$name\""))
        }
        assertTrue(schema.contains("\"anyComponent\""))
        assertTrue(schema.contains(BASIC_CATALOG_ID))
    }

    @Test
    fun `basic catalog covers all eighteen spec components`() {
        assertEquals(18, BasicCatalogSchema.components.size)
    }

    @Test
    fun `catalog can be extended with the app's own components`() {
        val extended = BasicCatalogSchema + listOf(
            A2uiComponentDefinition(
                name = "SongRow",
                description = "A song with a checkbox.",
                propertySchema = componentSchema(
                    properties = mapOf("title" to dynamicString()),
                    required = setOf("title"),
                ),
            )
        )
        assertTrue("SongRow" in extended.components)
        assertTrue("Text" in extended.components)
        A2uiSchemaValidator(extended).validate(
            props("""{"title":"Titanium"}"""),
            extended.components.getValue("SongRow").propertySchema,
        )
    }
}
