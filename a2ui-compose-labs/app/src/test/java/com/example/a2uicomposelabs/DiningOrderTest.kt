package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.BindingScope
import com.example.a2uicomposelabs.a2ui.SurfaceState
import com.example.a2uicomposelabs.demos.DiningCatalogSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Schema validation proves the delivery surface is *well formed*. This proves it actually
 * prices a basket.
 *
 * The claim the demo makes on stage is that tapping a quantity stepper re-prices the order with
 * nothing leaving the device. That only holds if the total is bound to a live `calcOrderTotal`
 * call over `/menu` — not to a number some earlier message baked in. So this drives the real
 * recorded stream, writes quantities the way the stepper does, and re-reads the same bindings
 * the screen reads.
 */
class DiningOrderTest {

    private val surfaceId = "t1"

    private fun deliverySurface(): Pair<SurfaceState, BindingScope> {
        val file = listOf(
            File("src/main/assets/dining_delivery.jsonl"),
            File("app/src/main/assets/dining_delivery.jsonl"),
        ).firstOrNull(File::exists) ?: error("dining_delivery.jsonl not found")

        val client = A2uiClient(DiningCatalogSchema)
        file.readLines().filter(String::isNotBlank).forEach { line ->
            client.apply(line.replace("__turn__", surfaceId))
        }
        assertEquals("the recorded stream was rejected: ${client.errors}", 0, client.errors.size)

        val surface = client.surfaces.getValue(surfaceId)
        val scope = BindingScope(
            surface = surface,
            onAction = {},
            itemBase = null,
            evaluator = DiningCatalogSchema.evaluator,
        )
        return surface to scope
    }

    /** The `calcOrderTotal` call the total row wraps in formatCurrency, on its own. */
    private fun totalCall(surface: SurfaceState): JsonElement {
        val value = surface.components.getValue("total").props["value"] as JsonObject
        return (value["args"] as JsonObject).getValue("value")
    }

    /** The condition guarding the submit button. */
    private fun orderCheck(surface: SurfaceState): JsonElement {
        val checks = surface.components.getValue("order").props["checks"]
        return ((checks as kotlinx.serialization.json.JsonArray)[0] as JsonObject).getValue("condition")
    }

    private fun BindingScope.number(prop: JsonElement): Double =
        (read(prop) as JsonPrimitive).doubleOrNull ?: error("not a number")

    @Test
    fun `an untouched basket costs nothing and cannot be ordered`() {
        val (surface, scope) = deliverySurface()
        assertEquals(0.0, scope.number(totalCall(surface)), 0.0)
        assertFalse(scope.read(orderCheck(surface)) == JsonPrimitive(true))
    }

    @Test
    fun `raising a quantity re-prices the basket through the same binding`() {
        val (surface, scope) = deliverySurface()

        // Exactly what MenuItemRow's + button does: write the item's own relative path.
        surface.updateData("/menu/0/quantity", JsonPrimitive(2))
        assertEquals("2 x 14,000", 28000.0, scope.number(totalCall(surface)), 0.0)

        surface.updateData("/menu/2/quantity", JsonPrimitive(1))
        assertEquals("plus a 9,000 salad", 37000.0, scope.number(totalCall(surface)), 0.0)

        // And down again — the stepper's − has to walk it back, not just forward.
        surface.updateData("/menu/0/quantity", JsonPrimitive(1))
        assertEquals("back to one pizza", 23000.0, scope.number(totalCall(surface)), 0.0)
    }

    @Test
    fun `the submit button unlocks as soon as something is in the basket`() {
        val (surface, scope) = deliverySurface()

        surface.updateData("/menu/1/quantity", JsonPrimitive(1))
        assertEquals(JsonPrimitive(true), scope.read(orderCheck(surface)))

        // Emptying it locks the button again.
        surface.updateData("/menu/1/quantity", JsonPrimitive(0))
        assertEquals(JsonPrimitive(false), scope.read(orderCheck(surface)))
    }

    @Test
    fun `the whole form still refuses to submit until the address is long enough`() {
        val (surface, scope) = deliverySurface()
        surface.updateData("/menu/0/quantity", JsonPrimitive(1))

        // The basket is fine, but the address check on another component still fails, and the
        // Button reads the whole surface — that is why the check lives on the input.
        assertFalse(scope.surfaceIsSubmittable())

        surface.updateData("/delivery/address", JsonPrimitive("서울 강남구 테헤란로 152"))
        assertTrue(scope.surfaceIsSubmittable())
    }
}
