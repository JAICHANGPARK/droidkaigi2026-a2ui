package com.example.a2uicomposelabs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Schema validation proves a component *exists* and its properties are shaped right. It says
 * nothing about whether the paths those properties bind to are paths anyone ever writes — and a
 * typo there is invisible until the screen renders blank on stage.
 *
 * So this pins the other half of the contract: every `{"path": …}` in the album surfaces must be
 * a slot `AlbumDemo` actually fills. Rename a key on either side and this fails.
 */
class AlbumBindingsTest {

    private fun asset(name: String): List<String> {
        val file = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull(File::exists)
            ?: error("asset $name not found")
        return file.readLines().filter(String::isNotBlank)
    }

    /** Every distinct `{"path": "..."}` value anywhere in the asset. */
    private fun pathsIn(assetName: String): Set<String> {
        val found = sortedSetOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val path = (element["path"] as? JsonPrimitive)?.contentOrNull
                    if (path != null && element.size == 1) found += path
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        asset(assetName).forEach { walk(Json.parseToJsonElement(it)) }
        return found
    }

    @Test
    fun `album list binds only what the search writes`() {
        // AlbumDemo.searchAlbums writes /heading and /albums[{id,title,artist,artwork,meta}].
        assertEquals(
            setOf("/heading", "artwork", "title", "artist", "meta", "id"),
            pathsIn("album_search.jsonl"),
        )
    }

    @Test
    fun `album detail binds only what the lookup writes`() {
        // AlbumDemo.openAlbum writes /album{artwork,title,artist,meta,tracks[{number,title,duration,preview}]}.
        assertEquals(
            setOf(
                "/album/artwork", "/album/title", "/album/artist", "/album/meta",
                "number", "title", "duration", "preview",
            ),
            pathsIn("album_detail.jsonl"),
        )
    }

    @Test
    fun `the playlist rows bind the fields the track loader writes`() {
        // PlaylistDemo.loadRealTracks writes /songs[{title,artist,artwork,preview,selected}].
        val paths = pathsIn("playlist_demo.jsonl")
        listOf("title", "artist", "artwork", "preview", "selected").forEach { field ->
            assertEquals(
                "every SongRow must bind /songs/N/$field",
                (0..3).map { "/songs/$it/$field" }.toSet(),
                paths.filter { it.endsWith("/$field") }.toSet(),
            )
        }
    }
}
