package com.example.a2uicomposelabs.a2ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A live model, asked for a repeating menu, wrote `{"path":"/name"}` inside the template — the
 * root of the data model, where it meant this row's own field. Every row then rendered blank,
 * and every stepper wrote to the same `/quantity`, so four rows shared one number.
 *
 * A leading slash that finds nothing at the root but does find something on the row can only
 * have meant the row. That recovery is what these tests hold, along with its limit: a real root
 * value always wins, and a name that exists in neither place stays null.
 */
class RowScopeRecoveryTest {

    private fun surfaceWithMenu(): SurfaceState {
        val surface = SurfaceState("s1")
        surface.setDataModel(
            Json.parseToJsonElement(
                """{"menu":[{"name":"Margherita","price":14000,"quantity":0},
                            {"name":"Caesar salad","price":9000,"quantity":0}]}"""
            ) as JsonObject
        )
        return surface
    }

    private fun rowScope(surface: SurfaceState, index: Int) =
        BindingScope(surface, onAction = {}, evaluator = A2uiDynamicEvaluator())
            .forItem("/menu", index)

    private fun binding(path: String) = buildJsonObject { put("path", JsonPrimitive(path)) }

    @Test
    fun `a relative binding still resolves against its own row`() {
        val surface = surfaceWithMenu()
        assertEquals("Margherita", rowScope(surface, 0).readString(binding("name")))
        assertEquals("Caesar salad", rowScope(surface, 1).readString(binding("name")))
        assertEquals(0, surface.scopeRecoveries)
    }

    @Test
    fun `an absolute binding that means the row is recovered and counted`() {
        val surface = surfaceWithMenu()
        // Verbatim from a live generation: "/name" instead of "name".
        assertEquals("Margherita", rowScope(surface, 0).readString(binding("/name")))
        assertEquals("Caesar salad", rowScope(surface, 1).readString(binding("/name")))
        assertEquals("both reads were recovered", 2, surface.scopeRecoveries)
    }

    @Test
    fun `a real root value is never shadowed by a row field`() {
        val surface = surfaceWithMenu()
        surface.updateData("/name", JsonPrimitive("the restaurant"))
        assertEquals("the restaurant", rowScope(surface, 0).readString(binding("/name")))
        assertEquals("nothing was recovered", 0, surface.scopeRecoveries)
    }

    @Test
    fun `a name that exists nowhere stays null`() {
        val surface = surfaceWithMenu()
        assertNull(rowScope(surface, 0).read(binding("/nonsense")))
    }

    @Test
    fun `a stepper writing an absolute path lands on its own row`() {
        val surface = surfaceWithMenu()
        // Without the recovery both rows would write to a single "/quantity" at the root.
        rowScope(surface, 0).write(binding("/quantity"), JsonPrimitive(2))
        rowScope(surface, 1).write(binding("/quantity"), JsonPrimitive(3))

        assertEquals(2.0, (surface.read("/menu/0/quantity") as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals(3.0, (surface.read("/menu/1/quantity") as JsonPrimitive).content.toDouble(), 0.0)
        assertNull("nothing leaked to the root", surface.read("/quantity"))
    }

    @Test
    fun `a relative write still lands on its own row`() {
        val surface = surfaceWithMenu()
        rowScope(surface, 1).write(binding("quantity"), JsonPrimitive(5))
        assertEquals("5", (surface.read("/menu/1/quantity") as JsonPrimitive).contentOrNull)
        assertEquals(0, surface.scopeRecoveries)
    }
}
