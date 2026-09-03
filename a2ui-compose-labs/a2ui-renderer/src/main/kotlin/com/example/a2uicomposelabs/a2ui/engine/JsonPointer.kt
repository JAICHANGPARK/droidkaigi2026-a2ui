package com.example.a2uicomposelabs.a2ui.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Minimal RFC 6901 JSON Pointer over kotlinx [JsonElement] (immutable updates). */
object JsonPointer {

    private fun tokens(path: String): List<String> =
        path.trim().removePrefix("/").split("/")
            .filter { it.isNotEmpty() }
            .map { it.replace("~1", "/").replace("~0", "~") }

    fun get(root: JsonElement?, path: String): JsonElement? {
        var current: JsonElement = root ?: return null
        for (token in tokens(path)) {
            current = when (current) {
                is JsonObject -> (current as JsonObject)[token]
                is JsonArray -> token.toIntOrNull()?.let { (current as JsonArray).getOrNull(it) }
                else -> null
            } ?: return null
        }
        return current
    }

    /** Returns a new tree with [value] written at [path]; a null [value] deletes the key. */
    fun set(root: JsonElement, path: String, value: JsonElement?): JsonElement {
        val t = tokens(path)
        if (t.isEmpty()) return value ?: JsonObject(emptyMap())
        return setAt(root, t, value)
    }

    private fun setAt(node: JsonElement, tokens: List<String>, value: JsonElement?): JsonElement {
        val key = tokens.first()
        val rest = tokens.drop(1)
        return when (node) {
            is JsonObject -> {
                val map = node.toMutableMap()
                if (rest.isEmpty()) {
                    if (value == null) map.remove(key) else map[key] = value
                } else {
                    map[key] = setAt(map[key] ?: JsonObject(emptyMap()), rest, value)
                }
                JsonObject(map)
            }
            is JsonArray -> {
                val index = key.toIntOrNull() ?: return node
                val list = node.toMutableList()
                if (index !in list.indices) return node
                if (rest.isEmpty()) {
                    if (value == null) list.removeAt(index) else list[index] = value
                } else {
                    list[index] = setAt(list[index], rest, value)
                }
                JsonArray(list)
            }
            else -> if (rest.isEmpty() && value != null) JsonObject(mapOf(key to value)) else node
        }
    }
}
