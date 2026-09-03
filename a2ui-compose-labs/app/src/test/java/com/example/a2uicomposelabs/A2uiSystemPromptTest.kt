package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.a2ui.model.BasicCatalogSchema
import com.example.a2uicomposelabs.agent.A2uiToolCall
import com.example.a2uicomposelabs.agent.a2uiSystemPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt is generated from the catalog, so it is worth asserting that what the model is
 * told matches what the renderer will accept.
 */
class A2uiSystemPromptTest {

    private val prompt = a2uiSystemPrompt(BasicCatalogSchema)

    @Test
    fun `the prompt carries every component the renderer can draw`() {
        BasicCatalogSchema.components.keys.forEach { name ->
            assertTrue("$name missing from the system prompt", prompt.contains("\"$name\""))
        }
    }

    @Test
    fun `the prompt carries the descriptions the model reads`() {
        BasicCatalogSchema.components.values.forEach { definition ->
            assertTrue(
                "${definition.name} description missing from the system prompt",
                prompt.contains(definition.description.take(30)),
            )
        }
    }

    @Test
    fun `the prompt carries every function the renderer can run`() {
        BasicCatalogSchema.functions.forEach { function ->
            assertTrue(
                "${function.definition.name} missing from the system prompt",
                prompt.contains("\"${function.definition.name}\""),
            )
        }
    }

    @Test
    fun `the prompt stays within a sane budget`() {
        // The catalog schema is generated, so it grows every time a component or function is
        // added. This is the tripwire: past this the prompt starts to crowd out the answer.
        val approximateTokens = prompt.length / 4
        assertTrue("prompt is $approximateTokens tokens", approximateTokens < 12_000)
    }

    @Test
    fun `the prompt asks for a tool call, never for JSON in the reply`() {
        // The whole reason parse failures went away: UI leaves the model as a tool argument,
        // which the API delivers whole. A prompt that invites free-text JSON undoes that.
        assertTrue(prompt.contains(A2uiToolCall.NAME))
        assertTrue(prompt.contains(A2uiToolCall.COMPONENTS_ARG))
        assertTrue(prompt.contains("Do not write A2UI JSON in your reply"))
        assertFalse("the tag protocol is gone", prompt.contains("a2ui-json"))
    }

    @Test
    fun `without a pinned id the model just has to be self consistent`() {
        assertTrue(prompt.contains("Pick one `surfaceId`"))
        assertFalse(prompt.contains("Do not invent another one"))
    }

    @Test
    fun `a pinned id is stated verbatim so live output matches the saved presets`() {
        // Regression: the survey screen looks its surface up by ID, so the model must be told
        // which one to use, or a live generation renders nothing.
        val pinned = a2uiSystemPrompt(BasicCatalogSchema, surfaceId = "survey")
        assertTrue(pinned.contains("\"surfaceId\":\"survey\""))
        assertTrue(pinned.contains("Do not invent another one"))
    }
}
