package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.androidxa2ui.AndroidxSupportCatalog
import com.example.a2uicomposelabs.androidxa2ui.AndroidxSurveyCatalog
import com.example.a2uicomposelabs.androidxa2ui.ANDROIDX_PROTOCOL_VERSION
import com.example.a2uicomposelabs.androidxa2ui.CSAT_EVENT
import com.example.a2uicomposelabs.androidxa2ui.CSAT_SURFACE_ID
import com.example.a2uicomposelabs.androidxa2ui.RecordedCsat
import com.example.a2uicomposelabs.androidxa2ui.SUPPORT_CATALOG_ID
import com.example.a2uicomposelabs.androidxa2ui.SupportTickets
import com.example.a2uicomposelabs.androidxa2ui.csatSystemPrompt
import com.example.a2uicomposelabs.androidxa2ui.toAndroidxDialect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CSAT demo's claim is that the questions are different per ticket, that the catalog is the
 * allowlist, and that a v1.0 tool call can be spoken to a v0.9.1 engine. All three are cheap to
 * check and expensive to discover on stage.
 */
class AndroidxCsatTest {

    // org.json is a stub in JVM unit tests, so the wire is read with regexes where it can be.
    private val componentName = Regex("\"component\"\\s*:\\s*\"([^\"]+)\"")
    private val questionText = Regex("\"component\":\"Question\",\"text\":\"([^\"]+)\"")

    private val everyLine =
        RecordedCsat.values.flatMap { script -> script.form + script.followUp }

    @Test
    fun `there is a recorded generation for every ticket, and no orphans`() {
        assertEquals(SupportTickets.map { it.id }.sorted(), RecordedCsat.keys.sorted())
    }

    @Test
    fun `the catalog builds and names every component exactly once`() {
        val names = AndroidxSupportCatalog.components.map { it.name }
        assertTrue("duplicate component names: $names", names.size == names.toSet().size)
        // Four of the ten are written in this app; the demo's honesty depends on saying which.
        assertTrue(names.containsAll(listOf("Question", "StarRating", "ChoicePicker", "TextField")))
        assertTrue(names.containsAll(listOf("Card", "Column", "Text", "Button", "CheckBox", "Slider")))
    }

    @Test
    fun `every component the scripts use is in the support catalog`() {
        val known = AndroidxSupportCatalog.components.map { it.name }.toSet()
        val used =
            everyLine.flatMap { line -> componentName.findAll(line).map { it.groupValues[1] } }.toSet()
        assertTrue("no components found in the scripts", used.isNotEmpty())
        assertTrue("scripts name components the catalog refuses: ${used - known}", (used - known).isEmpty())
    }

    @Test
    fun `the cafe survey cannot draw the support form's controls`() {
        // A catalog is an allowlist, not an inventory. ChoicePicker and Slider live in the same
        // files as the survey's own components; only this catalog lets an agent ask for them.
        val surveyComponents = AndroidxSurveyCatalog.components.map { it.name }
        assertTrue("ChoicePicker" !in surveyComponents)
        assertTrue("Slider" !in surveyComponents)
    }

    @Test
    fun `every message quotes this demo's version, surface and catalog`() {
        everyLine.forEach { line ->
            assertTrue(line, line.contains("\"version\":\"$ANDROIDX_PROTOCOL_VERSION\""))
            assertTrue(line, line.contains("\"surfaceId\":\"$CSAT_SURFACE_ID\""))
        }
        RecordedCsat.values.forEach { script ->
            assertTrue(script.form.first().contains("\"catalogId\":\"$SUPPORT_CATALOG_ID\""))
        }
    }

    @Test
    fun `no two tickets ask the same question`() {
        // The whole point of the screen. A shared question would mean a template was possible
        // after all, and four scripts would be four copies of one form.
        val questions = RecordedCsat.values.flatMap { script ->
            script.form.flatMap { line -> questionText.findAll(line).map { it.groupValues[1] } }
        }
        assertEquals(4 * 5, questions.size)
        assertEquals("a question is reused across tickets", questions.size, questions.toSet().size)
    }

    @Test
    fun `every form rates overall satisfaction on the path the app reads`() {
        // The follow-up hangs off this one path; a form that binds it elsewhere silently loses
        // the low-rating branch.
        RecordedCsat.forEach { (ticketId, script) ->
            val overall = script.form.filter { it.contains("\"path\":\"/answers/overall\"") }
            assertEquals("ticket $ticketId", 1, overall.size)
            assertTrue(ticketId, overall.single().contains("\"component\":\"StarRating\""))
        }
    }

    @Test
    fun `every form leaves the app an empty container, and every follow-up fills it`() {
        RecordedCsat.forEach { (ticketId, script) ->
            assertTrue(
                ticketId,
                script.form.any { it.contains("{\"id\":\"more\",\"component\":\"Column\",\"children\":[]}") },
            )
            // Filled last, so the follow-up arrives in one beat instead of three spinners.
            assertTrue(ticketId, script.followUp.last().contains("\"id\":\"more\""))
            assertTrue(ticketId, script.followUp.last().contains("\"fq1\""))
        }
    }

    @Test
    fun `every submit button hands back the whole answers subtree`() {
        // What lets the app collect follow-up answers it never re-sent the button for.
        RecordedCsat.forEach { (ticketId, script) ->
            val submit = script.form.single { it.contains("\"component\":\"Button\"") }
            assertTrue(ticketId, submit.contains("\"event\":{\"name\":\"$CSAT_EVENT\""))
            assertTrue(ticketId, submit.contains("\"answers\":{\"path\":\"/answers\"}"))
            assertTrue(ticketId, submit.contains("\"ticketId\":\"$ticketId\""))
        }
    }

    @Test
    fun `a v1_0 opening message becomes three v0_9_1 messages`() {
        val v1 =
            """{"version":"v1.0","createSurface":{"surfaceId":"csat","catalogId":"$SUPPORT_CATALOG_ID",""" +
                """"components":[{"id":"root","component":"Card","child":"form"}],"dataModel":{"answers":{"overall":0}}}}"""

        val translated = toAndroidxDialect(v1).map { Json.parseToJsonElement(it).jsonObject }

        assertEquals(3, translated.size)
        translated.forEach {
            assertEquals(ANDROIDX_PROTOCOL_VERSION, it["version"]?.jsonPrimitive?.content)
        }
        val create = translated[0]["createSurface"]?.jsonObject
        assertNotNull(create)
        assertEquals(CSAT_SURFACE_ID, create!!["surfaceId"]?.jsonPrimitive?.content)
        assertEquals(SUPPORT_CATALOG_ID, create["catalogId"]?.jsonPrimitive?.content)
        // v0.9.1 sends the data model back with an event only when it was asked for.
        assertEquals(true, create["sendDataModel"]?.jsonPrimitive?.content?.toBoolean())
        // Nothing v1.0-only survives: the components and the data model moved to their own
        // messages, which is the only thing the old parser knows how to read.
        assertNull(create["components"])
        assertNull(create["dataModel"])

        val data = translated[1]["updateDataModel"]?.jsonObject
        assertEquals("/", data?.get("path")?.jsonPrimitive?.content)
        assertEquals(
            0,
            data?.get("value")?.jsonObject?.get("answers")?.jsonObject?.get("overall")
                ?.jsonPrimitive?.content?.toInt(),
        )
        assertTrue(translated[2].containsKey("updateComponents"))
    }

    @Test
    fun `a v1_0 component message keeps its body and changes only its version`() {
        val body = """{"surfaceId":"csat","components":[{"id":"a1","component":"StarRating"}]}"""
        val translated = toAndroidxDialect("""{"version":"v1.0","updateComponents":$body}""")

        assertEquals(1, translated.size)
        val message = Json.parseToJsonElement(translated.single()).jsonObject
        assertEquals(ANDROIDX_PROTOCOL_VERSION, message["version"]?.jsonPrimitive?.content)
        assertEquals(
            Json.parseToJsonElement(body) as JsonObject,
            message["updateComponents"]?.jsonObject,
        )
    }

    @Test
    fun `something unrecognisable is passed through for the engine to refuse`() {
        // Better a complaint from the engine, in its own words, than a message invented here.
        assertEquals(listOf("not json at all"), toAndroidxDialect("not json at all"))
    }

    @Test
    fun `the prompt teaches the old dialect and quotes the real catalog`() {
        val prompt = csatSystemPrompt()

        assertTrue(prompt.contains(ANDROIDX_PROTOCOL_VERSION))
        assertTrue(prompt.contains(SUPPORT_CATALOG_ID))
        assertTrue(prompt.contains(CSAT_EVENT))
        // The three shapes a v1.0-trained model gets wrong on this wire.
        assertTrue(prompt.contains("\"child\":\"form\""))
        assertTrue(prompt.contains("\"action\":{\"event\""))
        assertTrue(prompt.contains("/answers/overall"))
        // The catalog half is generated, not written: every component has to appear in it.
        AndroidxSupportCatalog.components.forEach { component ->
            assertTrue(component.name, prompt.contains("\"${component.name}\""))
        }
    }
}
