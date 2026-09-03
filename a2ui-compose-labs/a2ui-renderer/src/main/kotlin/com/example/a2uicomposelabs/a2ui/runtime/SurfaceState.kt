package com.example.a2uicomposelabs.a2ui.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.a2uicomposelabs.a2ui.engine.JsonPointer
import com.example.a2uicomposelabs.a2ui.model.ComponentNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Compose state for one surface.
 * The whole renderer is this loop: message in → state change → recomposition.
 */
class SurfaceState(val surfaceId: String) {

    val components = mutableStateMapOf<String, ComponentNode>()

    var dataModel: JsonElement by mutableStateOf<JsonElement>(JsonObject(emptyMap()))
        private set

    /**
     * How many bindings inside a repeated row had to be re-read against the row itself.
     *
     * Models write `{"path":"/name"}` inside a template surprisingly often, meaning the root of
     * the data model when they meant this row's own field. Counted rather than hidden — the
     * screen is right, but the agent was not.
     */
    var scopeRecoveries by mutableStateOf(0)
        internal set

    /** Returns false when the batch would exceed the component cap (bounded state). */
    fun putComponents(nodes: List<ComponentNode>): Boolean {
        // Count only genuinely new, distinct ids: replacements and in-batch duplicates
        // must not count against the cap.
        val added = nodes.map { it.id }.distinct().count { it !in components }
        if (components.size + added > MAX_COMPONENTS) return false
        nodes.forEach { components[it.id] = it }
        return true
    }

    fun setDataModel(model: JsonObject) {
        dataModel = model
    }

    fun updateData(path: String, value: JsonElement?) {
        dataModel = JsonPointer.set(dataModel, path, value)
    }

    fun read(path: String): JsonElement? = JsonPointer.get(dataModel, path)

    companion object {
        const val MAX_COMPONENTS = 200
    }
}
