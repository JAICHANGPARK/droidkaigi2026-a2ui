package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.androidxa2ui.AndroidxBookingCatalog
import com.example.a2uicomposelabs.androidxa2ui.AndroidxSurveyCatalog
import com.example.a2uicomposelabs.androidxa2ui.BOOKING_CATALOG_ID
import com.example.a2uicomposelabs.androidxa2ui.BookingAndroidxScript
import com.example.a2uicomposelabs.androidxa2ui.BookingV1Script
import com.example.a2uicomposelabs.androidxa2ui.SURVEY_CATALOG_ID
import com.example.a2uicomposelabs.androidxa2ui.SurveyAndroidxScript
import com.example.a2uicomposelabs.androidxa2ui.SurveyV1Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The androidx demo compiles a checked-in AOSP snapshot, and upstream moves daily. Re-syncs keep
 * changing what `material3-a2ui` exports — `MaterialTextComponent` went internal on 19 Aug 2026,
 * `MaterialCardComponent` followed on 21 Aug, and `MaterialRowComponent`, `MaterialColumnComponent`
 * and `MaterialButtonComponent` followed on 25 Aug, all swallowed by the A2uiBasicCatalogV1
 * contract — and nothing failed until someone opened the screen by hand.
 *
 * These are the cheap guard. Building a catalog resolves every component object the demo names,
 * and `A2uiCatalog(...)` itself throws on duplicate names. Then every component a script
 * mentions has to be in the catalog that script names, because the catalog is the allowlist: a
 * name that is not there draws nothing.
 */
class AndroidxCatalogTest {

    private val componentName = Regex("\"component\"\\s*:\\s*\"([^\"]+)\"")

    private fun componentsUsedBy(script: List<String>): Set<String> =
        script.flatMap { line -> componentName.findAll(line).map { it.groupValues[1] } }.toSet()

    @Test
    fun `each catalog builds and names every component exactly once`() {
        for (catalog in listOf(AndroidxBookingCatalog, AndroidxSurveyCatalog)) {
            val names = catalog.components.map { it.name }
            assertTrue("duplicate component names in ${catalog.id}: $names",
                names.size == names.toSet().size)
            assertTrue("expected Text to survive the basic-catalog move", "Text" in names)
            assertTrue("expected Card to survive the basic-catalog move", "Card" in names)
            assertTrue("expected Column to survive the basic-catalog move", "Column" in names)
            assertTrue("expected Button to survive the basic-catalog move", "Button" in names)
        }
    }

    @Test
    fun `every component the booking scripts use is in the booking catalog`() {
        val known = AndroidxBookingCatalog.components.map { it.name }.toSet()
        val used = componentsUsedBy(BookingAndroidxScript)
        assertTrue("no components found in the script", used.isNotEmpty())
        assertTrue("booking names components its catalog refuses: ${used - known}",
            (used - known).isEmpty())
        assertTrue("expected Slider to come from material3-a2ui now", "Slider" in known)
    }

    @Test
    fun `every component the survey scripts use is in the survey catalog`() {
        val known = AndroidxSurveyCatalog.components.map { it.name }.toSet()
        val used = componentsUsedBy(SurveyAndroidxScript)
        assertTrue("no components found in the script", used.isNotEmpty())
        assertTrue("survey names components its catalog refuses: ${used - known}",
            (used - known).isEmpty())
        assertTrue("expected CheckBox, the library's one input control", "CheckBox" in known)
    }

    /**
     * The point of splitting them. A catalog is an allowlist, so a screen must not be handed
     * components it has no use for — a survey agent that can reach DateTimeInput is a survey
     * agent that can draw one.
     */
    @Test
    fun `neither catalog carries the other screen's controls`() {
        val booking = AndroidxBookingCatalog.components.map { it.name }.toSet()
        val survey = AndroidxSurveyCatalog.components.map { it.name }.toSet()

        assertFalse("the booking form has no questions to ask", "Question" in booking)
        assertFalse("the booking form has nothing to rate", "StarRating" in booking)
        assertFalse("the survey books no tables", "DateTimeInput" in survey)
        assertFalse("the survey has no party size", "Slider" in survey)
    }

    /**
     * Both dialects of one screen must quote the same catalog, or the demo is comparing
     * catalogs rather than protocol versions and the whole side-by-side proves nothing.
     */
    @Test
    fun `both dialects of a screen name the same catalog`() {
        fun idsIn(script: List<String>) =
            script.flatMap { line ->
                Regex("\"catalogId\"\\s*:\\s*\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }
            }.toSet()

        assertEquals(setOf(BOOKING_CATALOG_ID), idsIn(BookingAndroidxScript))
        assertEquals(setOf(BOOKING_CATALOG_ID), idsIn(BookingV1Script))
        assertEquals(setOf(SURVEY_CATALOG_ID), idsIn(SurveyAndroidxScript))
        assertEquals(setOf(SURVEY_CATALOG_ID), idsIn(SurveyV1Script))
        assertEquals(BOOKING_CATALOG_ID, AndroidxBookingCatalog.id)
        assertEquals(SURVEY_CATALOG_ID, AndroidxSurveyCatalog.id)
    }
}
