package com.example.a2uicomposelabs.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A real, keyless music source for the playlist demo.
 *
 * The point it exists to make: **the agent decides *which* songs, the app resolves *what they
 * actually are*.** A model can name a plausible track and get the artist, the album, or the
 * whole song wrong — so nothing it says about a song reaches the screen. Every row shown comes
 * back from Apple's catalog, or it is dropped.
 *
 * That split also keeps the user's library out of the prompt entirely (see the data-model
 * discussion in `../../../../../../../04-aosp-source-reference.md`): the model works with a few
 * words, the app works with the data.
 *
 * The iTunes Search API needs no key, no OAuth and no signup, which is what makes it usable on
 * a conference stage. It is rate limited to roughly 20 calls a minute — fine for a demo, not a
 * production music backend.
 *
 * Deliberately `HttpURLConnection` + kotlinx-serialization, like `GeminiAgent`: this repo adds
 * no HTTP client so the whole thing stays readable in one sitting.
 */
object ITunesSearch {

    private const val ENDPOINT = "https://itunes.apple.com/search"
    private const val LOOKUP = "https://itunes.apple.com/lookup"

    private val json = Json { ignoreUnknownKeys = true }

    /** One album, as the store knows it. */
    data class Album(
        val id: Long,
        val title: String,
        val artist: String,
        /** https URL, 300x300 — the size a card wants. */
        val artworkUrl: String,
        /** https URL, 600x600 — the size a detail header wants. */
        val artworkLargeUrl: String,
        val year: String,
        val trackCount: Int,
    )

    /** One track inside an album: position, name, how long it runs, and a preview. */
    data class AlbumTrack(
        val number: Int,
        val title: String,
        /** Pre-formatted "m:ss" — the renderer displays strings, it does not do arithmetic. */
        val duration: String,
        val previewUrl: String,
    )

    data class AlbumDetail(val album: Album, val tracks: List<AlbumTrack>)

    /** One resolved track. Every field here came from the catalog, not from a model. */
    data class Track(
        val title: String,
        val artist: String,
        val album: String,
        /** https URL, 300x300 — upgraded from the 100x100 the API returns. */
        val artworkUrl: String,
        /** https URL of a ~30s preview. Empty when the store has none. */
        val previewUrl: String,
    )

    /**
     * Searches for one track and returns the best match, or null when nothing was found.
     *
     * A null result is the useful case: it means the model asked for a song that does not
     * exist, and the caller can drop it instead of rendering a lie.
     */
    suspend fun findTrack(query: String): Track? = withContext(Dispatchers.IO) {
        runCatching { request(query, limit = 1).firstOrNull() }.getOrNull()
    }

    /** Searches once and returns up to [limit] tracks — used when the query is a vibe, not a title. */
    suspend fun findTracks(query: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        runCatching { request(query, limit) }.getOrDefault(emptyList())
    }

    /**
     * Resolves a list of "Title — Artist" candidates, dropping the ones the catalog does not
     * have. Order is preserved, duplicates are removed by track id.
     */
    suspend fun resolveAll(candidates: List<String>, limit: Int): List<Track> {
        val resolved = LinkedHashMap<String, Track>()
        for (candidate in candidates) {
            if (resolved.size >= limit) break
            val track = findTrack(candidate) ?: continue
            resolved.putIfAbsent("${track.title}|${track.artist}", track)
        }
        return resolved.values.toList()
    }

    /**
     * Album search: "newjeans" gives back the EPs and albums, newest first as the store orders
     * them. This is the query behind "show me NewJeans albums".
     */
    suspend fun findAlbums(query: String, limit: Int): List<Album> = withContext(Dispatchers.IO) {
        runCatching {
            get("$ENDPOINT?term=${enc(query)}&entity=album&limit=${limit.coerceIn(1, 25)}")
                .results
                .mapNotNull(Result::toAlbum)
        }.getOrDefault(emptyList())
    }

    /**
     * Everything on one album. The lookup endpoint returns the collection first and then its
     * tracks in the same array, distinguished by `wrapperType`.
     */
    suspend fun lookupAlbum(collectionId: Long): AlbumDetail? = withContext(Dispatchers.IO) {
        runCatching {
            val results = get("$LOOKUP?id=$collectionId&entity=song&limit=200").results
            val album = results.firstOrNull { it.wrapperType == "collection" }?.toAlbum()
                ?: return@runCatching null
            val tracks = results
                .filter { it.wrapperType == "track" }
                .mapNotNull(Result::toAlbumTrack)
                .sortedBy(AlbumTrack::number)
            AlbumDetail(album, tracks)
        }.getOrNull()
    }

    private fun request(term: String, limit: Int): List<Track> =
        get("$ENDPOINT?term=${enc(term)}&entity=song&limit=${limit.coerceIn(1, 25)}")
            .results
            .mapNotNull(Result::toTrack)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun get(url: String): SearchResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("iTunes request failed: HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString<SearchResponse>(body)
        } finally {
            connection.disconnect()
        }
    }

    @Serializable private class SearchResponse(val results: List<Result> = emptyList())

    @Serializable
    private class Result(
        val wrapperType: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val collectionId: Long? = null,
        val trackNumber: Int? = null,
        val trackCount: Int? = null,
        val trackTimeMillis: Long? = null,
        val releaseDate: String? = null,
        @SerialName("artworkUrl100") val artwork: String? = null,
        val previewUrl: String? = null,
    ) {

        private fun artworkAt(size: String) = artwork?.replace("100x100bb", size).orEmpty()

        fun toAlbum(): Album? {
            val id = collectionId ?: return null
            val title = collectionName ?: return null
            return Album(
                id = id,
                title = title,
                artist = artistName.orEmpty(),
                artworkUrl = artworkAt("300x300bb"),
                artworkLargeUrl = artworkAt("600x600bb"),
                year = releaseDate?.take(4).orEmpty(),
                trackCount = trackCount ?: 0,
            )
        }

        fun toAlbumTrack(): AlbumTrack? {
            val title = trackName ?: return null
            val seconds = ((trackTimeMillis ?: 0L) / 1000L).toInt()
            return AlbumTrack(
                number = trackNumber ?: 0,
                title = title,
                duration = "%d:%02d".format(seconds / 60, seconds % 60),
                previewUrl = previewUrl.orEmpty(),
            )
        }

        fun toTrack(): Track? {
            val title = trackName ?: return null
            val artist = artistName ?: return null
            return Track(
                title = title,
                artist = artist,
                album = collectionName.orEmpty(),
                // The API hands back a 100px thumbnail; the same path serves larger sizes.
                artworkUrl = artworkAt("300x300bb"),
                previewUrl = previewUrl.orEmpty(),
            )
        }
    }
}
