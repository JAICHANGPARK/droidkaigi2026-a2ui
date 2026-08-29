package com.example.a2uicomposelabs

import com.example.a2uicomposelabs.a2ui.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.A2uiMessage
import com.example.a2uicomposelabs.a2ui.A2uiSchemaValidator
import com.example.a2uicomposelabs.a2ui.A2uiValidationException
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.ComponentNode
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.demos.AlbumCatalog
import com.example.a2uicomposelabs.demos.AnalyticsCatalog
import com.example.a2uicomposelabs.demos.AnalyticsCatalogSchema
import com.example.a2uicomposelabs.demos.AssistantCatalog
import com.example.a2uicomposelabs.demos.AssistantCatalogSchema
import com.example.a2uicomposelabs.demos.DiningCatalog
import com.example.a2uicomposelabs.demos.DiningCatalogSchema
import com.example.a2uicomposelabs.demos.AlbumCatalogSchema
import com.example.a2uicomposelabs.demos.MusicCatalogSchema
import com.example.a2uicomposelabs.demos.SurveyCatalog
import com.example.a2uicomposelabs.demos.SurveyCatalogSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every recorded demo stream must survive the same validation a live agent's output gets.
 * If a schema in [BasicCatalogSchema] and a demo asset ever disagree, this fails long before
 * the app is on stage.
 */
class DemoAssetsSchemaTest {

    private fun asset(name: String): List<String> {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        val file = candidates.firstOrNull(File::exists)
            ?: error("asset $name not found; looked in ${candidates.map(File::getAbsolutePath)}")
        return file.readLines().filter(String::isNotBlank)
    }

    /** Returns one message per rejected component, exactly as A2uiClient would record it. */
    private fun rejectionsIn(assetName: String, catalog: A2uiCatalog): List<String> {
        val validator = A2uiSchemaValidator(catalog)
        val rejections = mutableListOf<String>()
        for (line in asset(assetName)) {
            val components: List<ComponentNode> = when (val message = A2uiMessage.parse(line)) {
                is A2uiMessage.CreateSurface -> message.components
                is A2uiMessage.UpdateComponents -> message.components
                else -> emptyList()
            }
            for (node in components) {
                val definition = catalog.components[node.component]
                if (definition == null) {
                    rejections += "unknown component '${node.component}' (id=${node.id})"
                    continue
                }
                try {
                    validator.validate(node.props, definition.propertySchema, "/${node.id}")
                } catch (e: A2uiValidationException) {
                    rejections += "invalid ${node.component} (id=${node.id}): ${e.message}"
                }
            }
        }
        return rejections
    }

    @Test
    fun `chat demo validates cleanly`() {
        assertEquals(emptyList<String>(), rejectionsIn("chat_demo.jsonl", BasicCatalogSchema))
    }

    @Test
    fun `contact form validates cleanly`() {
        assertEquals(emptyList<String>(), rejectionsIn("contact_form.jsonl", BasicCatalogSchema))
    }

    @Test
    fun `playlist demo validates against the app's own catalog`() {
        assertEquals(emptyList<String>(), rejectionsIn("playlist_demo.jsonl", MusicCatalogSchema))
    }

    @Test
    fun `playlist demo would be rejected by the basic catalog alone`() {
        // The app's components only exist because the app declared them.
        val rejections = rejectionsIn("playlist_demo.jsonl", BasicCatalogSchema)
        assertTrue(rejections.any { it.contains("unknown component 'SongRow'") })
        assertTrue(rejections.any { it.contains("unknown component 'PlaylistCard'") })
    }

    @Test
    fun `live agent fallback carries exactly the two planted bad messages`() {
        val rejections = rejectionsIn("live_agent_fallback.jsonl", BasicCatalogSchema)
        assertEquals(rejections.toString(), 2, rejections.size)
        assertTrue(rejections.any { it.contains("unknown component 'WebView'") })
        assertTrue(rejections.any { it.contains("Additional property 'onClick' not allowed") })
    }

    @Test
    fun `every generated survey validates against the survey catalog`() {
        listOf("survey_cafe.jsonl", "survey_event.jsonl", "survey_onboarding.jsonl").forEach { name ->
            assertEquals(name, emptyList<String>(), rejectionsIn(name, SurveyCatalogSchema))
        }
    }

    @Test
    fun `surveys need the app's own StarRating`() {
        // The star rating exists only because this app declared and wrote it.
        val rejections = rejectionsIn("survey_cafe.jsonl", BasicCatalogSchema)
        assertTrue(rejections.any { it.contains("unknown component 'StarRating'") })
    }

    @Test
    fun `the survey catalog's two halves agree`() {
        assertEquals(
            SurveyCatalogSchema.components.keys.sorted(),
            (ComponentRegistry(BasicCatalog) + SurveyCatalog).names.sorted(),
        )
    }

    @Test
    fun `both album surfaces validate against the album catalog`() {
        listOf("album_search.jsonl", "album_detail.jsonl").forEach { name ->
            assertEquals(name, emptyList<String>(), rejectionsIn(name, AlbumCatalogSchema))
        }
    }

    @Test
    fun `the album catalog's two halves agree`() {
        assertEquals(
            AlbumCatalogSchema.components.keys.sorted(),
            (ComponentRegistry(BasicCatalog) + AlbumCatalog).names.sorted(),
        )
    }

    @Test
    fun `every recorded assistant answer validates against the merged catalog`() {
        listOf(
            "assistant_albums.jsonl", "assistant_playlist.jsonl", "assistant_survey.jsonl",
            "assistant_analytics.jsonl", "assistant_device.jsonl",
            "assistant_device_tiles.jsonl", "assistant_device_grid.jsonl",
            "assistant_device_list.jsonl", "assistant_device_bars.jsonl",
            "assistant_device_text.jsonl",
        )
            .forEach { name ->
                assertEquals(name, emptyList<String>(), rejectionsIn(name, AssistantCatalogSchema))
            }
    }

    @Test
    fun `the assistant can also play both dining flows`() {
        // The Assistant answers restaurant questions too, so its merged catalog has to accept
        // the same recordings the dedicated demo uses — components and functions alike.
        listOf(
            "dining_reservation.jsonl", "dining_reservation_done.jsonl",
            "dining_waitlist.jsonl", "dining_waitlist_done.jsonl",
            "dining_waitlist_closed.jsonl",
            "dining_delivery.jsonl", "dining_delivery_done.jsonl",
            "dining_payment.jsonl", "dining_tracking.jsonl",
            "dining_review.jsonl", "dining_review_done.jsonl",
        ).forEach { name ->
            assertEquals(name, emptyList<String>(), rejectionsIn(name, AssistantCatalogSchema))
        }
    }

    @Test
    fun `the assistant catalog can draw everything it describes`() {
        // The merged catalog is the one the agent is shown; a component described but not
        // registered would be a promise the renderer cannot keep.
        assertEquals(
            AssistantCatalogSchema.components.keys.sorted(),
            (ComponentRegistry(BasicCatalog) + AssistantCatalog).names.sorted(),
        )
    }

    @Test
    fun `every recorded analytics answer validates against the chart catalog`() {
        listOf("analytics_region.jsonl", "analytics_trend.jsonl", "analytics_breakdown.jsonl")
            .forEach { name ->
                assertEquals(name, emptyList<String>(), rejectionsIn(name, AnalyticsCatalogSchema))
            }
    }

    @Test
    fun `the analytics catalog can draw every chart it describes`() {
        assertEquals(
            AnalyticsCatalogSchema.components.keys.sorted(),
            (ComponentRegistry(BasicCatalog) + AnalyticsCatalog).names.sorted(),
        )
    }

    @Test
    fun `the device presentations differ only in how they draw the same data`() {
        // Same question, same request, five layouts. If a presentation ever started asking for
        // different data it would stop being a presentation and become a different answer.
        val requests = listOf(
            "assistant_device_tiles.jsonl", "assistant_device_grid.jsonl",
            "assistant_device_list.jsonl", "assistant_device_bars.jsonl",
            "assistant_device_text.jsonl",
        ).map { name ->
            asset(name).single { it.contains("updateDataModel") }.substringAfter("\"value\":")
        }
        assertEquals(1, requests.toSet().size)
        assertTrue(requests.first().startsWith("{\"kind\":\"device\"}"))
    }

    @Test
    fun `both dining flows validate against the dining catalog`() {
        listOf(
            "dining_reservation.jsonl", "dining_reservation_done.jsonl",
            "dining_waitlist.jsonl", "dining_waitlist_done.jsonl",
            "dining_waitlist_closed.jsonl",
            "dining_delivery.jsonl", "dining_delivery_done.jsonl",
            "dining_payment.jsonl", "dining_tracking.jsonl",
            "dining_review.jsonl", "dining_review_done.jsonl",
        ).forEach { name ->
            assertEquals(name, emptyList<String>(), rejectionsIn(name, DiningCatalogSchema))
        }
    }

    @Test
    fun `the delivery order needs the app's own MenuItemRow`() {
        // The stepper exists only because this app declared and wrote it.
        val rejections = rejectionsIn("dining_delivery.jsonl", BasicCatalogSchema)
        assertTrue(rejections.any { it.contains("unknown component 'MenuItemRow'") })
        assertTrue(rejections.any { it.contains("unknown component 'OrderTotalRow'") })
    }

    @Test
    fun `the dining catalog can draw everything it describes`() {
        assertEquals(
            DiningCatalogSchema.components.keys.sorted(),
            (ComponentRegistry(BasicCatalog) + DiningCatalog).names.sorted(),
        )
    }
}
