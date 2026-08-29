package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.a2ui.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.demos.AssistantCatalogSchema
import com.example.a2uicomposelabs.demos.DiningCatalogSchema
import com.example.a2uicomposelabs.demos.MusicCatalogSchema
import com.example.a2uicomposelabs.demos.SurveyCatalogSchema
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A catalog derived from the basic one must keep its functions.
 *
 * Rebuilding a catalog from `BasicCatalogSchema.components` alone compiles, runs, and looks
 * right until a message calls `required` — at which point the renderer rejects it as an
 * unknown function and the form silently loses its inputs. Deriving with `withId` keeps both
 * halves; this makes sure nobody goes back to the other way.
 */
class DemoCatalogFunctionsTest {

    private val derived = mapOf(
        "survey" to SurveyCatalogSchema,
        "music" to MusicCatalogSchema,
        "assistant" to AssistantCatalogSchema,
        "dining" to DiningCatalogSchema,
    )

    @Test
    fun `every app catalog can still call the basic functions`() {
        val expected = BasicCatalogSchema.functions.map { it.definition.name }.toSet()
        derived.forEach { (name, catalog: A2uiCatalog) ->
            val available = catalog.functions.map { it.definition.name }.toSet()
            assertTrue(
                "$name catalog is missing ${expected - available}",
                available.containsAll(expected),
            )
        }
    }

    @Test
    fun `every app catalog keeps its own id`() {
        derived.forEach { (name, catalog) ->
            assertTrue("$name catalog kept the basic id", catalog.id != BasicCatalogSchema.id)
        }
    }

    @Test
    fun `the evaluator each catalog exposes knows those functions too`() {
        derived.forEach { (name, catalog) ->
            assertTrue("$name evaluator cannot run 'required'", "required" in catalog.evaluator.names)
        }
    }

    @Test
    fun `the dining catalog adds the arithmetic the protocol lacks`() {
        // A2UI has no + operator, so pricing a basket is only possible because the app
        // registered these two. Losing them turns every total into a silent zero.
        val available = DiningCatalogSchema.evaluator.names
        assertTrue("calcOrderTotal is missing", "calcOrderTotal" in available)
        assertTrue("countOrderItems is missing", "countOrderItems" in available)
    }
}
