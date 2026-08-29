package com.example.a2uicomposelabs.a2ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalog is only trustworthy if its two halves agree: what the schema promises the agent
 * it may emit, and what the registry can actually draw. Nothing enforces that at compile time,
 * so it is enforced here — a component added to one half and forgotten in the other fails the
 * build, not the demo.
 */
class CatalogDriftTest {

    @Test
    fun `every described component can be rendered, and every renderable one is described`() {
        assertEquals(
            BasicCatalogSchema.components.keys.sorted(),
            ComponentRegistry(BasicCatalog).names.sorted(),
        )
    }

    @Test
    fun `every component description is written for the agent to read`() {
        BasicCatalogSchema.components.values.forEach { definition ->
            assertEquals(
                "${definition.name} description must end in a full stop",
                true,
                definition.description.endsWith("."),
            )
            assertEquals(
                "${definition.name} description is too short to be useful to a model",
                true,
                definition.description.length > 20,
            )
        }
    }
}
